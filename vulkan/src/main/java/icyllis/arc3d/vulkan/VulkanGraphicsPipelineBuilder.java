/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.vulkan;

import icyllis.arc3d.compiler.CompileOptions;
import icyllis.arc3d.compiler.ModuleLoader;
import icyllis.arc3d.compiler.ShaderCompiler;
import icyllis.arc3d.compiler.ShaderKind;
import icyllis.arc3d.compiler.TranslationUnit;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.DepthStencilSettings;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.PipelineDesc;
import icyllis.arc3d.engine.RenderPassDesc;
import icyllis.arc3d.engine.ShaderCaps;
import icyllis.arc3d.engine.VertexInputLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK11.*;

public class VulkanGraphicsPipelineBuilder {

    private final VulkanDevice mDevice;
    private final PipelineDesc mPipelineDesc;
    private final RenderPassDesc mRenderPassDesc;

    private PipelineDesc.GraphicsPipelineInfo mInfo;

    // these two buffers subject to Java Cleaner GC
    private ByteBuffer mFinalizedVertSPIRV;
    private ByteBuffer mFinalizedFragSPIRV;

    private int mNumShaders = 0;

    //////////
    // the following objects need to be explicitly deleted
    private long mVertShaderModule = VK_NULL_HANDLE;
    private long mFragShaderModule = VK_NULL_HANDLE;

    @SharedPtr
    private VulkanDescriptorSetLayout[] mSetLayouts;
    @SharedPtr
    private VulkanRenderPass mRenderPass;

    private long mPipelineLayout = VK_NULL_HANDLE;
    private long mPipeline = VK_NULL_HANDLE;
    //////////

    private VulkanGraphicsPipelineBuilder(VulkanDevice device,
                                          PipelineDesc pipelineDesc,
                                          RenderPassDesc renderPassDesc) {
        mDevice = device;
        mPipelineDesc = pipelineDesc;
        mRenderPassDesc = renderPassDesc;
    }

    @NonNull
    @SharedPtr
    public static VulkanGraphicsPipeline createGraphicsPipeline(
            final VulkanDevice device,
            final PipelineDesc pipelineDesc,
            final RenderPassDesc renderPassDesc) {
        return new VulkanGraphicsPipeline(device,
                CompletableFuture.supplyAsync(() -> {
                    VulkanGraphicsPipelineBuilder builder = new VulkanGraphicsPipelineBuilder(device, pipelineDesc, renderPassDesc);
                    builder.build();
                    return builder;
                }));
    }

    // 3.3.1 Object Lifetime
    //
    // The following object types are consumed when they are passed into a Vulkan command and not
    // further accessed by the objects they are used to create. They must not be destroyed in the duration
    // of any API command they are passed into:
    //
    // • VkShaderModule
    // • VkPipelineCache
    //
    // A VkRenderPass or VkPipelineLayout object passed as a parameter to create another object is not
    // further accessed by that object after the duration of the command it is passed into.
    //
    // VkDescriptorSetLayout objects may be accessed by commands that operate on descriptor sets
    // allocated using that layout, and those descriptor sets must not be updated with
    // vkUpdateDescriptorSets after the descriptor set layout has been destroyed. Otherwise, a
    // VkDescriptorSetLayout object passed as a parameter to create another object is not further accessed
    // by that object after the duration of the command it is passed into.
    void destroy() {
        if (mVertShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(mDevice.vkDevice(), mVertShaderModule, null);
            mVertShaderModule = VK_NULL_HANDLE;
        }
        if (mFragShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(mDevice.vkDevice(), mFragShaderModule, null);
            mFragShaderModule = VK_NULL_HANDLE;
        }
        if (mSetLayouts != null) {
            for (VulkanDescriptorSetLayout setLayout : mSetLayouts) {
                if (setLayout != null) {
                    setLayout.unref();
                }
            }
            mSetLayouts = null;
        }
        mRenderPass = RefCnt.move(mRenderPass);
        if (mPipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(mDevice.vkDevice(), mPipelineLayout, null);
            mPipelineLayout = VK_NULL_HANDLE;
        }
        if (mPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(mDevice.vkDevice(), mPipeline, null);
            mPipeline = VK_NULL_HANDLE;
        }
    }

    // compile Arc3D Shading Language into SPIR-V vulkan target
    private boolean compileShaders() {
        ShaderCompiler compiler = new ShaderCompiler();
        CompileOptions options = new CompileOptions();
        ShaderCaps shaderCaps = mDevice.getCaps().shaderCaps();

        if (!mInfo.mFragSource.isEmpty()) {
            TranslationUnit fragmentProgram = compiler.parse(mInfo.mFragSource, ShaderKind.FRAGMENT,
                    options, ModuleLoader.getInstance().loadCommonModule(compiler));
            if (fragmentProgram == null) {
                VKUtil.handleCompileError(mDevice.getLogger(),
                        mInfo.mFragSource, compiler.getErrorMessage());
                return false;
            } else {
                mFinalizedFragSPIRV = compiler.generateSPIRV(fragmentProgram, shaderCaps);
                if (mFinalizedFragSPIRV == null) {
                    VKUtil.handleCompileError(mDevice.getLogger(),
                            mInfo.mFragSource, compiler.getErrorMessage());
                    return false;
                } else {
                    mNumShaders++;
                }
            }
        }
        {
            TranslationUnit vertexProgram = compiler.parse(mInfo.mVertSource, ShaderKind.VERTEX,
                    options, ModuleLoader.getInstance().loadCommonModule(compiler));
            if (vertexProgram == null) {
                VKUtil.handleCompileError(mDevice.getLogger(),
                        mInfo.mVertSource, compiler.getErrorMessage());
                return false;
            } else {
                mFinalizedVertSPIRV = compiler.generateSPIRV(vertexProgram, shaderCaps);
                if (mFinalizedVertSPIRV == null) {
                    VKUtil.handleCompileError(mDevice.getLogger(),
                            mInfo.mVertSource, compiler.getErrorMessage());
                    return false;
                } else {
                    mNumShaders++;
                }
            }
        }

        /*CompletableFuture.runAsync(() -> {
            String filename = mInfo.mPipelineLabel.replaceAll("/", ".");
            try {
                Files.writeString(Path.of(filename + ".vert"), mInfo.mVertSource,
                        StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (var channel = FileChannel.open(Path.of(filename + ".vert.spv"),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                var src = mFinalizedVertSPIRV.slice();
                while (src.hasRemaining()) {
                    channel.write(src);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!mInfo.mFragSource.isEmpty()) {
                try {
                    Files.writeString(Path.of(filename + ".frag"), mInfo.mFragSource,
                            StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try (var channel = FileChannel.open(Path.of(filename + ".frag.spv"),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    var src = mFinalizedFragSPIRV.slice();
                    while (src.hasRemaining()) {
                        channel.write(src);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });*/

        return true;
    }

    private boolean createPipelineLayout() {
        mSetLayouts = new VulkanDescriptorSetLayout[mInfo.mDescriptorSetLayouts.length];
        if (mSetLayouts.length > VulkanCaps.MAX_BOUND_SETS) {
            return false;
        }
        for (int i = 0; i < mSetLayouts.length; i++) {
            mSetLayouts[i] = VulkanDescriptorSetLayout.make(mDevice, mInfo.mDescriptorSetLayouts[i]);
            if (mSetLayouts[i] == null) {
                return false;
            }
        }

        try (var stack = MemoryStack.stackPush()) {

            var setLayouts = stack.mallocLong(mSetLayouts.length);
            for (int i = 0; i < mSetLayouts.length; i++) {
                setLayouts.put(i, mSetLayouts[i].vkSetLayout());
            }

            //noinspection DataFlowIssue
            var pCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .pSetLayouts(setLayouts)
                    .pPushConstantRanges(null);
            var pPipelineLayout = stack.mallocLong(1);
            var result = vkCreatePipelineLayout(mDevice.vkDevice(),
                    pCreateInfo, null, pPipelineLayout);
            mDevice.checkResult(result);
            if (result != VK_SUCCESS) {
                return false;
            }

            mPipelineLayout = pPipelineLayout.get(0);
            return true;
        }
    }

    private VkPipelineShaderStageCreateInfo.@NonNull Buffer setupShaderStages(@NonNull MemoryStack stack) {
        stack.nASCII("main", true);
        long entryPointNameEncoded = stack.getPointerAddress();

        VkPipelineShaderStageCreateInfo.Buffer pStages = VkPipelineShaderStageCreateInfo
                .calloc(mNumShaders, stack);
        if (mFinalizedVertSPIRV != null) {
            var stage = pStages.get();
            stage
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(mVertShaderModule);
            memPutAddress(stage.address() + VkPipelineShaderStageCreateInfo.PNAME, entryPointNameEncoded);
            // no specialization info
        }
        if (mFinalizedFragSPIRV != null) {
            var stage = pStages.get();
            stage
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(mFragShaderModule);
            memPutAddress(stage.address() + VkPipelineShaderStageCreateInfo.PNAME, entryPointNameEncoded);
            // no specialization info
        }
        assert !pStages.hasRemaining();

        return pStages.flip();
    }

    @Nullable
    private VkPipelineVertexInputStateCreateInfo setupVertexInputState(
            @NonNull MemoryStack stack
    ) {
        VertexInputLayout inputLayout = mInfo.mInputLayout;
        int bindings = inputLayout.getBindingCount();

        if (bindings > mDevice.getCaps().maxVertexBindings()) {
            return null;
        }

        int totalLocations = 0;
        for (int i = 0; i < bindings; i++) {
            totalLocations += inputLayout.getLocationCount(i);
        }

        if (totalLocations > mDevice.getCaps().maxVertexAttributes()) {
            return null;
        }

        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription
                .malloc(totalLocations, stack);
        VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription
                .malloc(bindings, stack);

        // index is location, they are the same
        int index = 0;

        for (int binding = 0; binding < bindings; binding++) {

            int inputRate = switch (inputLayout.getInputRate(binding)) {
                case VertexInputLayout.INPUT_RATE_VERTEX -> VK_VERTEX_INPUT_RATE_VERTEX;
                case VertexInputLayout.INPUT_RATE_INSTANCE -> VK_VERTEX_INPUT_RATE_INSTANCE;
                default -> throw new AssertionError();
            };
            int stride = inputLayout.getStride(binding);

            index = set_vertex_format_binding_group(inputLayout.getAttributes(binding),
                    attributeDescriptions, index, binding);

            var description = bindingDescriptions.get();
            description
                    .binding(binding)
                    .stride(stride)
                    .inputRate(inputRate);
        }

        assert index == totalLocations;
        assert !attributeDescriptions.hasRemaining();
        assert !bindingDescriptions.hasRemaining();

        attributeDescriptions.flip();
        bindingDescriptions.flip();

        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .pVertexBindingDescriptions(bindingDescriptions)
                .pVertexAttributeDescriptions(attributeDescriptions);
    }

    private static int set_vertex_format_binding_group(
            @NonNull Iterator<VertexInputLayout.Attribute> attribs,
            VkVertexInputAttributeDescription.Buffer attributeDescriptions,
            int index,
            int binding
    ) {
        while (attribs.hasNext()) {
            var attrib = attribs.next();
            // a matrix can take up multiple locations
            int locations = attrib.locationCount();
            int offset = attrib.offset();
            for (int i = 0; i < locations; i++) {
                set_attrib_format_binding_group(attrib.srcType(), attributeDescriptions.get(), index, binding, offset);
                index++;
                offset += attrib.size();
            }
        }
        return index;
    }

    private static void set_attrib_format_binding_group(
            int type,
            VkVertexInputAttributeDescription description,
            int index,
            int binding,
            int offset
    ) {
        int format = switch (type) {
            case Engine.VertexAttribType.kFloat ->
                    VK_FORMAT_R32_SFLOAT;
            case Engine.VertexAttribType.kFloat2 ->
                    VK_FORMAT_R32G32_SFLOAT;
            case Engine.VertexAttribType.kFloat3 ->
                    VK_FORMAT_R32G32B32_SFLOAT;
            case Engine.VertexAttribType.kFloat4 ->
                    VK_FORMAT_R32G32B32A32_SFLOAT;
            case Engine.VertexAttribType.kHalf ->
                    VK_FORMAT_R16_SFLOAT;
            case Engine.VertexAttribType.kHalf2 ->
                    VK_FORMAT_R16G16_SFLOAT;
            case Engine.VertexAttribType.kHalf4 ->
                    VK_FORMAT_R16G16B16A16_SFLOAT;
            case Engine.VertexAttribType.kInt2 ->
                    VK_FORMAT_R32G32_SINT;
            case Engine.VertexAttribType.kInt3 ->
                    VK_FORMAT_R32G32B32_SINT;
            case Engine.VertexAttribType.kInt4 ->
                    VK_FORMAT_R32G32B32A32_SINT;
            case Engine.VertexAttribType.kByte ->
                    VK_FORMAT_R8_SINT;
            case Engine.VertexAttribType.kByte2 ->
                    VK_FORMAT_R8G8_SINT;
            case Engine.VertexAttribType.kByte4 ->
                    VK_FORMAT_R8G8B8A8_SINT;
            case Engine.VertexAttribType.kUByte ->
                    VK_FORMAT_R8_UINT;
            case Engine.VertexAttribType.kUByte2 ->
                    VK_FORMAT_R8G8_UINT;
            case Engine.VertexAttribType.kUByte4 ->
                    VK_FORMAT_R8G8B8A8_UINT;
            case Engine.VertexAttribType.kUByte_norm ->
                    VK_FORMAT_R8_UNORM;
            case Engine.VertexAttribType.kUByte4_norm ->
                    VK_FORMAT_R8G8B8A8_UNORM;
            case Engine.VertexAttribType.kShort2 ->
                    VK_FORMAT_R16G16_SINT;
            case Engine.VertexAttribType.kShort4 ->
                    VK_FORMAT_R16G16B16A16_SINT;
            case Engine.VertexAttribType.kUShort2 ->
                    VK_FORMAT_R16G16_UINT;
            case Engine.VertexAttribType.kUShort2_norm ->
                    VK_FORMAT_R16G16_UNORM;
            case Engine.VertexAttribType.kInt ->
                    VK_FORMAT_R32_SINT;
            case Engine.VertexAttribType.kUInt ->
                    VK_FORMAT_R32_UINT;
            case Engine.VertexAttribType.kUShort_norm ->
                    VK_FORMAT_R16_UNORM;
            case Engine.VertexAttribType.kUShort4_norm ->
                    VK_FORMAT_R16G16B16A16_UNORM;
            default -> throw new AssertionError(type);
        };

        description
                .location(index)
                .binding(binding)
                .format(format)
                .offset(offset);
    }

    private @NonNull VkPipelineInputAssemblyStateCreateInfo setupInputAssemblyState(
            @NonNull MemoryStack stack
    ) {
        return VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .topology(VKUtil.toVkPrimitiveTopology(mInfo.mPrimitiveType))
                .primitiveRestartEnable(false);
    }

    private @NonNull VkPipelineViewportStateCreateInfo setupViewportScissorState(
            @NonNull MemoryStack stack
    ) {
        // don't use multi-viewport;
        // use dynamic viewport and scissor
        return VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .viewportCount(1)
                .scissorCount(1);
    }

    private @NonNull VkPipelineRasterizationStateCreateInfo setupRasterizationState(
            @NonNull MemoryStack stack
    ) {
        //TODO add a wireframe mode and cull mode
        return VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
                .depthBiasConstantFactor(0.0f)
                .depthBiasClamp(0.0f)
                .depthBiasSlopeFactor(0.0f)
                .lineWidth(1.0f);
    }

    private @NonNull VkPipelineMultisampleStateCreateInfo setupMultisampleState(
            @NonNull MemoryStack stack
    ) {
        return VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .rasterizationSamples(VKUtil.toVkSampleCount(mRenderPassDesc.mSampleCount))
                .sampleShadingEnable(false)
                .minSampleShading(0.0f)
                .pSampleMask(null)
                .alphaToCoverageEnable(false)
                .alphaToOneEnable(false);
    }

    private @NonNull VkPipelineDepthStencilStateCreateInfo setupDepthStencilState(
            @NonNull MemoryStack stack
    ) {
        var settings = mInfo.mDepthStencilSettings;
        var pCreateInfo = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .depthTestEnable(settings.mDepthTest)
                .depthWriteEnable(settings.mDepthWrite)
                .depthCompareOp(VKUtil.toVkCompareOp(settings.mDepthCompareOp))
                .depthBoundsTestEnable(false)
                .stencilTestEnable(settings.mStencilTest);
        if (settings.mStencilTest) {
            setup_stencil_op_state(settings.mFrontFace, pCreateInfo.front());
            setup_stencil_op_state(settings.mBackFace, pCreateInfo.back());
        }
        pCreateInfo
                .minDepthBounds(0.0f)
                .maxDepthBounds(1.0f);
        return pCreateInfo;
    }

    private static void setup_stencil_op_state(DepthStencilSettings.@NonNull Face face,
                                               @NonNull VkStencilOpState opState) {
        opState
                .failOp(VKUtil.toVkStencilOp(face.mFailOp))
                .passOp(VKUtil.toVkStencilOp(face.mPassOp))
                .depthFailOp(VKUtil.toVkStencilOp(face.mDepthFailOp))
                .compareOp(VKUtil.toVkStencilOp(face.mCompareOp))
                .compareMask(face.mCompareMask)
                .writeMask(face.mWriteMask)
                .reference(face.mReference);
    }

    private @NonNull VkPipelineColorBlendStateCreateInfo setupColorBlendState(
            @NonNull MemoryStack stack
    ) {
        var blendInfo = mInfo.mBlendInfo;

        int attachmentCount = mRenderPassDesc.mColorAttachments.length;
        VkPipelineColorBlendAttachmentState.Buffer attachmentStates = null;

        if (attachmentCount > 0) {
            boolean enable = !blendInfo.blendShouldDisable();
            attachmentStates = VkPipelineColorBlendAttachmentState
                    .malloc(attachmentCount, stack);
            VkPipelineColorBlendAttachmentState attachmentState = attachmentStates.get(0);
            attachmentState.blendEnable(enable);
            if (enable) {
                int srcFactor = VKUtil.toVkBlendFactor(blendInfo.mSrcFactor);
                int dstFactor = VKUtil.toVkBlendFactor(blendInfo.mDstFactor);
                int blendOp = VKUtil.toVkBlendOp(blendInfo.mEquation);
                attachmentState
                        .srcColorBlendFactor(srcFactor)
                        .dstColorBlendFactor(dstFactor)
                        .colorBlendOp(blendOp)
                        .srcAlphaBlendFactor(srcFactor)
                        .dstAlphaBlendFactor(dstFactor)
                        .alphaBlendOp(blendOp);
            }
            if (blendInfo.mColorWrite) {
                attachmentState.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                        VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            } else {
                attachmentState.colorWriteMask(0);
            }
            for (int i = 1; i < attachmentCount; i++) {
                // duplicate for all attachments
                attachmentStates.put(i, attachmentState);
            }
        }

        //noinspection DataFlowIssue
        return VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .logicOpEnable(false)
                .pAttachments(attachmentStates);
    }

    private @NonNull VkPipelineDynamicStateCreateInfo setupDynamicState(
            @NonNull MemoryStack stack
    ) {
        var dynamicStates = stack.ints(VK_DYNAMIC_STATE_VIEWPORT,
                VK_DYNAMIC_STATE_SCISSOR,
                VK_DYNAMIC_STATE_BLEND_CONSTANTS);

        return VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType$Default()
                .flags(0)
                .pDynamicStates(dynamicStates);
    }

    private void build() {
        mInfo = mPipelineDesc.createGraphicsPipelineInfo(mDevice, mRenderPassDesc);

        if (!compileShaders()) {
            return;
        }

        try {
            mVertShaderModule = VKUtil.createShaderModule(mDevice, mFinalizedVertSPIRV);
            if (mVertShaderModule == VK_NULL_HANDLE) {
                return;
            }
            if (mFinalizedFragSPIRV != null) {
                mFragShaderModule = VKUtil.createShaderModule(mDevice, mFinalizedFragSPIRV);
                if (mFragShaderModule == VK_NULL_HANDLE) {
                    return;
                }
            }
        } finally {
            Reference.reachabilityFence(mFinalizedVertSPIRV);
            Reference.reachabilityFence(mFinalizedFragSPIRV);
        }

        if (!createPipelineLayout()) {
            return;
        }

        mRenderPass = VulkanRenderPass.make(mDevice,
                VulkanRenderPassSet.makeCompatibleDesc(mRenderPassDesc));
        if (mRenderPass == null) {
            return;
        }

        try (var stack = MemoryStack.stackPush()) {



            VkPipelineShaderStageCreateInfo.Buffer pStages = setupShaderStages(stack);

            VkPipelineVertexInputStateCreateInfo pVertexInputState = setupVertexInputState(stack);
            if (pVertexInputState == null) {
                return;
            }

            VkPipelineInputAssemblyStateCreateInfo pInputAssemblyState = setupInputAssemblyState(stack);

            VkPipelineViewportStateCreateInfo pViewportState = setupViewportScissorState(stack);

            VkPipelineRasterizationStateCreateInfo pRasterizationState = setupRasterizationState(stack);

            VkPipelineMultisampleStateCreateInfo pMultisampleState = setupMultisampleState(stack);

            VkPipelineDepthStencilStateCreateInfo pDepthStencilState = setupDepthStencilState(stack);

            VkPipelineColorBlendStateCreateInfo pColorBlendState = setupColorBlendState(stack);

            VkPipelineDynamicStateCreateInfo pDynamicState = setupDynamicState(stack);

            VkGraphicsPipelineCreateInfo.Buffer pCreateInfo = VkGraphicsPipelineCreateInfo
                    .calloc(1, stack);

            boolean loadFromResolve = (mRenderPassDesc.mRenderPassFlags & RenderPassDesc.kLoadFromResolve_Flag) != 0;

            pCreateInfo
                    .sType$Default()
                    .flags(0)
                    .pStages(pStages)
                    .pVertexInputState(pVertexInputState)
                    .pInputAssemblyState(pInputAssemblyState)
                    .pViewportState(pViewportState)
                    .pRasterizationState(pRasterizationState)
                    .pMultisampleState(pMultisampleState)
                    .pDepthStencilState(pDepthStencilState)
                    .pColorBlendState(pColorBlendState)
                    .pDynamicState(pDynamicState)
                    .layout(mPipelineLayout)
                    .renderPass(mRenderPass.vkRenderPass())
                    .subpass(loadFromResolve ? 1 : 0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            var pPipeline = stack.mallocLong(1);
            var result = vkCreateGraphicsPipelines(mDevice.vkDevice(),
                    VK_NULL_HANDLE, pCreateInfo, null, pPipeline);
            mDevice.checkResult(result);
            if (result != VK_SUCCESS) {
                mDevice.getLogger().error("Failed to create VulkanGraphicsPipeline: {}",
                        VKUtil.getResultMessage(result));
                return;
            }

            mPipeline = pPipeline.get(0);
        }
    }

    boolean finish(VulkanGraphicsPipeline dest, VulkanResourceProvider resourceProvider) {
        @SharedPtr
        VulkanRenderPassSet renderPassSet = null;
        @SharedPtr
        VulkanDescriptorSetManager[] descriptorSetManagers = null;
        try {
            if (mPipeline == VK_NULL_HANDLE) {
                return false;
            }

            renderPassSet = mDevice.findOrCreateCompatibleRenderPassSet(mRenderPassDesc);
            if (renderPassSet == null) {
                // this shouldn't happen, since beginRenderPass will first be called
                return false;
            }

            descriptorSetManagers = new VulkanDescriptorSetManager[mSetLayouts.length];
            for (int i = 0; i < descriptorSetManagers.length; i++) {
                var layoutInfo = mInfo.mDescriptorSetLayouts[i];
                if (layoutInfo.getBindingCount() > 0) {
                    descriptorSetManagers[i] = mDevice.findOrCreateDescriptorSetManager(
                            resourceProvider, layoutInfo);
                    if (descriptorSetManagers[i] == null) {
                        return false;
                    }
                }
                // else a placeholder
            }

            // move pipeline and pipeline layout to dest
            dest.init(mPipeline, mPipelineLayout, // move
                    descriptorSetManagers, renderPassSet, // move
                    mInfo.mInputLayout.getBindingCount());
            mPipeline = VK_NULL_HANDLE;
            mPipelineLayout = VK_NULL_HANDLE;
            descriptorSetManagers = null;
            renderPassSet = null;

            return true;
        } finally {
            if (renderPassSet != null) {
                renderPassSet.unref();
            }
            if (descriptorSetManagers != null) {
                for (VulkanDescriptorSetManager manager : descriptorSetManagers) {
                    if (manager != null) {
                        manager.unref();
                    }
                }
            }
            // shader modules, descriptor set layouts, render pass can be deleted
            destroy();
        }
    }
}

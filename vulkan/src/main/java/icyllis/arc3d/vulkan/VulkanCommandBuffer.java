/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Buffer;
import icyllis.arc3d.engine.BufferImageCopyData;
import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.CommandBuffer;
import icyllis.arc3d.engine.Engine.BufferUsageFlags;
import icyllis.arc3d.engine.Engine.DescriptorType;
import icyllis.arc3d.engine.Engine.IndexType;
import icyllis.arc3d.engine.GraphicsPipeline;
import icyllis.arc3d.engine.Image;
import icyllis.arc3d.engine.ImageMutableState;
import icyllis.arc3d.engine.Sampler;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK11.*;

public abstract class VulkanCommandBuffer extends CommandBuffer {

    public static final class SetBindingState {

        //TODO vulkan have per-stage limit on bindings
        public static final int MAX_BINDINGS = 16;

        // these resources are tracked by command buffer, so raw ptr
        // can contain VulkanImageView, VulkanBuffer, VulkanBufferView
        @RawPtr
        private final Object[] mResources = new Object[MAX_BINDINGS];
        @RawPtr
        private final VulkanSampler[] mSamplers = new VulkanSampler[MAX_BINDINGS];

        // Typical implementations only allow you to bind sizes up to UINT_MAX,
        // and dynamic offsets are also limited to uint32_t, so both are int.
        public final int[] mBindingOffsets = new int[MAX_BINDINGS];
        public final int[] mBindingSizes = new int[MAX_BINDINGS];

        // This is raw ptr because it's managed by the current pipeline,
        // if pipeline is being deleted then the manager will not be reused.
        // The managed itself is managed by VulkanDevice and deduplicated, so
        // it can also be used to check against pipeline layout compatibility.
        @RawPtr
        private VulkanDescriptorSetManager mCurrentManager;

        // from VkSpec:
        //
        // Pipeline Layout Compatibility
        //
        // Two pipeline layouts are defined to be “compatible for push constants” if they were created with
        // identical push constant ranges. Two pipeline layouts are defined to be “compatible for set N” if they
        // were created with identically defined descriptor set layouts for sets zero through N, and if they were
        // created with identical push constant ranges.
        //
        // When binding a descriptor set (see Descriptor Set Binding) to set number N, if the previously bound
        // descriptor sets for sets zero through N-1 were all bound using compatible pipeline layouts, then
        // performing this binding does not disturb any of the lower numbered sets. If, additionally, the
        // previously bound descriptor set for set N was bound using a pipeline layout compatible for set N,
        // then the bindings in sets numbered greater than N are also not disturbed.
        //
        // Similarly, when binding a pipeline, the pipeline can correctly access any previously bound
        // descriptor sets which were bound with compatible pipeline layouts, as long as all lower numbered
        // sets were also bound with compatible layouts.
        //
        // Layout compatibility means that descriptor sets can be bound to a command buffer for use by any
        // pipeline created with a compatible pipeline layout, and without having bound a particular pipeline
        // first. It also means that descriptor sets can remain valid across a pipeline change, and the same
        // resources will be accessible to the newly bound pipeline.
        //
        private boolean mNeedsRebind;

        private final IntBuffer mDynamicOffsets = MemoryUtil.memAllocInt(MAX_BINDINGS);

        @RawPtr
        public VulkanImageView getImageView(int binding) {
            return ((VulkanImageView) mResources[binding]);
        }

        @RawPtr
        public VulkanBuffer getBuffer(int binding) {
            return ((VulkanBuffer) mResources[binding]);
        }

        @RawPtr
        public VulkanSampler getSampler(int binding) {
            return mSamplers[binding];
        }

        void reset() {
            Arrays.fill(mResources, null);
            Arrays.fill(mSamplers, null);
            Arrays.fill(mBindingOffsets, 0);
            Arrays.fill(mBindingSizes, 0);
            mCurrentManager = null;
        }

        void destroy() {
            MemoryUtil.memFree(mDynamicOffsets);
        }
    }

    protected final SetBindingState[] mSetsBindingState = new SetBindingState[VulkanCaps.MAX_BOUND_SETS];

    protected final VkBufferMemoryBarrier.Buffer mBufferBarriers = VkBufferMemoryBarrier.malloc(4);
    protected final VkImageMemoryBarrier.Buffer mImageBarriers = VkImageMemoryBarrier.malloc(16);
    protected int mSrcStageMask, mDstStageMask;

    protected final VulkanDevice mDevice;
    protected final VulkanResourceProvider mResourceProvider;
    protected final VkCommandBuffer mCommandBuffer;
    protected boolean mIsRecording = false;
    protected boolean mInRenderPass = false;

    // current pipeline state
    @RawPtr
    protected VulkanGraphicsPipeline mGraphicsPipeline;

    @RawPtr
    protected final LongBuffer mActiveVertexBuffers = MemoryUtil.memAllocLong(Caps.MAX_VERTEX_BINDINGS);
    protected final LongBuffer mActiveVertexOffsets = MemoryUtil.memAllocLong(Caps.MAX_VERTEX_BINDINGS);
    protected boolean mNeedsBindVertexBuffers;

    public VulkanCommandBuffer(@NonNull VulkanDevice device,
                               VulkanResourceProvider resourceProvider,
                               long commandBuffer) {
        mDevice = device;
        mResourceProvider = resourceProvider;
        mCommandBuffer = new VkCommandBuffer(commandBuffer, device.vkDevice());
        assert commandBuffer != VK_NULL_HANDLE;

        for (int i = 0; i < mSetsBindingState.length; i++) {
            mSetsBindingState[i] = new SetBindingState();
        }

        resetStates();
    }

    protected static long allocate(@NonNull VulkanDevice device,
                                   long commandPool,
                                   boolean forSecondary) {
        assert commandPool != VK_NULL_HANDLE;
        try (var stack = stackPush()) {
            var pCreateInfo = VkCommandBufferAllocateInfo
                    .calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(forSecondary
                            ? VK_COMMAND_BUFFER_LEVEL_SECONDARY
                            : VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            var pCommandBuffer = stack.mallocPointer(1);
            var result = vkAllocateCommandBuffers(
                    device.vkDevice(),
                    pCreateInfo,
                    pCommandBuffer
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to allocate {} VulkanCommandBuffer: {}",
                        forSecondary ? "Secondary" : "Primary", VKUtil.getResultMessage(result));
                return VK_NULL_HANDLE;
            }
            return pCommandBuffer.get(0);
        }
    }

    public void resetStates() {
        mGraphicsPipeline = null;
        for (SetBindingState setBindingState : mSetsBindingState) {
            setBindingState.reset();
        }
        for (int i = 0; i < Caps.MAX_VERTEX_BINDINGS; i++) {
            mActiveVertexBuffers.put(i, VK_NULL_HANDLE);
        }
        for (int i = 0; i < Caps.MAX_VERTEX_BINDINGS; i++) {
            mActiveVertexOffsets.put(i, 0);
        }
    }

    @Override
    public <T> void setupForShaderRead(@NonNull List<@RawPtr T> textures, @NonNull Function<? super T, @RawPtr Image> toTexture) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean bindGraphicsPipeline(@RawPtr GraphicsPipeline graphicsPipeline) {
        assert mInRenderPass;

        mGraphicsPipeline = (VulkanGraphicsPipeline) graphicsPipeline;

        if (!mGraphicsPipeline.bindPipeline(this)) {
            return false;
        }

        int firstInvalidateSet = mSetsBindingState.length;
        for (int set = mGraphicsPipeline.getSetCount() - 1; set >= 0; set--) {
            VulkanDescriptorSetManager setManager = mGraphicsPipeline.getDescriptorSetManager(set);
            if (mSetsBindingState[set].mCurrentManager != setManager) {
                mSetsBindingState[set].mCurrentManager = setManager;
                firstInvalidateSet = set;
            }
        }
        // NB: if first 'getSetCount()' set layouts used by the current pipeline match, nothing is invalidated;
        // otherwise, invalidate up to MAX_BOUND_SETS (not getSetCount)
        for (int set = firstInvalidateSet; set < mSetsBindingState.length; set++) {
            mSetsBindingState[set].mNeedsRebind = true;
        }

        return true;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        try (var stack = stackPush()) {
            // no multi-viewport
            VkViewport.Buffer viewports = VkViewport.malloc(1, stack);

            viewports
                    .x(x)
                    .y(y)
                    .width(width)
                    .height(height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            vkCmdSetViewport(mCommandBuffer, 0, viewports);
        }
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        try (var stack = stackPush()) {
            // no multi-viewport
            VkRect2D.Buffer scissors = VkRect2D.malloc(1, stack);

            scissors.offset().set(x, y);
            scissors.extent().set(width, height);

            vkCmdSetScissor(mCommandBuffer, 0, scissors);
        }
    }

    @Override
    public void bindIndexBuffer(int indexType, @RawPtr Buffer buffer, long offset) {
        int vkIndexType = switch (indexType) {
            case IndexType.kUShort -> VK_INDEX_TYPE_UINT16;
            case IndexType.kUInt -> VK_INDEX_TYPE_UINT32;
            default -> throw new AssertionError();
        };
        vkCmdBindIndexBuffer(
                mCommandBuffer,
                ((VulkanBuffer) buffer).vkBuffer(),
                offset,
                vkIndexType
        );
    }

    @Override
    public void bindVertexBuffer(int binding, @RawPtr Buffer buffer, long offset) {
        // record each binding here, and bindVertexBuffers() together
        long vkBuffer = ((VulkanBuffer) buffer).vkBuffer();
        if (mActiveVertexBuffers.get(binding) != vkBuffer ||
                mActiveVertexOffsets.get(binding) != offset) {
            mActiveVertexBuffers.put(binding, vkBuffer);
            mActiveVertexOffsets.put(binding, offset);
            mNeedsBindVertexBuffers = true;
        }
    }

    @Override
    public void bindUniformBuffer(int set, int binding, @RawPtr Buffer buffer, int offset, int size) {
        var bindingState = mSetsBindingState[set];
        if (bindingState.mResources[binding] != buffer ||
                bindingState.mBindingOffsets[binding] != offset ||
                bindingState.mBindingSizes[binding] != size) {
            bindingState.mResources[binding] = buffer;
            bindingState.mBindingOffsets[binding] = offset;
            bindingState.mBindingSizes[binding] = size;
            bindingState.mNeedsRebind = true;
        }
    }

    @Override
    public void bindTextureSampler(int set, int binding, @RawPtr Image texture,
                                   short swizzle, @RawPtr Sampler sampler) {
        var bindingState = mSetsBindingState[set];
        @RawPtr
        VulkanImageView vulkanImageView = ((VulkanImage) texture).findOrCreateTextureView(swizzle);
        VulkanSampler vulkanSampler = (VulkanSampler) sampler;

        if (vulkanImageView == null) {
            //TODO in this case, submit should fail, verify that
            return;
        }

        if (bindingState.mResources[binding] != vulkanImageView ||
                bindingState.mSamplers[binding] != vulkanSampler) {
            bindingState.mResources[binding] = vulkanImageView;
            bindingState.mSamplers[binding] = vulkanSampler;
            bindingState.mNeedsRebind = true;
        }
    }

    protected void bindResourcesIfNeeded(int bindPoint) {
        if (bindPoint == VK_PIPELINE_BIND_POINT_GRAPHICS &&
                mNeedsBindVertexBuffers) {
            int bindingCount = mGraphicsPipeline.getVertexBindingCount();
            mActiveVertexBuffers.limit(bindingCount);
            mActiveVertexOffsets.limit(bindingCount);
            vkCmdBindVertexBuffers(
                    mCommandBuffer,
                    0,
                    mActiveVertexBuffers,
                    mActiveVertexOffsets
            );
            mActiveVertexBuffers.clear();
            mActiveVertexOffsets.clear();
            mNeedsBindVertexBuffers = false;
        }

        int setCount = mGraphicsPipeline.getSetCount();
        long pipelineLayout = mGraphicsPipeline.vkPipelineLayout();
        for (int set = 0; set < setCount; set++) {
            var bindingState = mSetsBindingState[set];
            if (!bindingState.mNeedsRebind || bindingState.mCurrentManager == null) {
                continue;
            }
            bindingState.mNeedsRebind = false;
            @SharedPtr
            VulkanDescriptorSet descriptorSet = bindingState.mCurrentManager
                    .findOrCreateDescriptorSet(mDevice, bindingState);
            if (descriptorSet == null) {
                //TODO OOM case, submit should fail, verify that
                continue;
            }
            @RawPtr
            var descriptorSetLayout = bindingState.mCurrentManager.getLayout();

            // performing this binding does not disturb any of the lower numbered sets.
            // the bindings in sets numbered greater than N are also not disturbed.
            try (var stack = stackPush()) {

                int numDynamicOffsets = 0;
                for (int binding = 0; binding < descriptorSetLayout.getBindingCount(); binding++) {
                    if (!descriptorSetLayout.isUsed(binding)) {
                        continue;
                    }
                    switch (descriptorSetLayout.getType(binding)) {
                        case DescriptorType.kUniformBuffer:
                        case DescriptorType.kStorageBuffer:
                            bindingState.mDynamicOffsets.put(numDynamicOffsets,
                                    bindingState.mBindingOffsets[binding]);
                            numDynamicOffsets++;
                            break;
                        default:
                            break;
                    }
                }
                IntBuffer dynamicOffsets = null;
                if (numDynamicOffsets > 0) {
                    dynamicOffsets = bindingState.mDynamicOffsets
                            .limit(numDynamicOffsets);
                }

                LongBuffer pDescriptorSet = stack.longs(descriptorSet.vkDescriptorSet());

                vkCmdBindDescriptorSets(
                        mCommandBuffer,
                        bindPoint,
                        pipelineLayout,
                        set,
                        pDescriptorSet,
                        dynamicOffsets
                );

                if (dynamicOffsets != null) {
                    dynamicOffsets.clear();
                }
            }

            trackResource(descriptorSet); // move
        }
    }

    @Override
    public void draw(int vertexCount, int baseVertex) {
        bindResourcesIfNeeded(VK_PIPELINE_BIND_POINT_GRAPHICS);
        drawInstanced(1, 0, vertexCount, baseVertex);
    }

    @Override
    public void drawIndexed(int indexCount, int baseIndex, int baseVertex) {
        bindResourcesIfNeeded(VK_PIPELINE_BIND_POINT_GRAPHICS);
        drawIndexedInstanced(indexCount, baseIndex, 1, 0, baseVertex);
    }

    @Override
    public void drawInstanced(int instanceCount, int baseInstance,
                              int vertexCount, int baseVertex) {
        bindResourcesIfNeeded(VK_PIPELINE_BIND_POINT_GRAPHICS);
        vkCmdDraw(mCommandBuffer,
                vertexCount,
                instanceCount,
                baseVertex,
                baseInstance);
    }

    @Override
    public void drawIndexedInstanced(int indexCount, int baseIndex,
                                     int instanceCount, int baseInstance,
                                     int baseVertex) {
        bindResourcesIfNeeded(VK_PIPELINE_BIND_POINT_GRAPHICS);
        vkCmdDrawIndexed(mCommandBuffer,
                indexCount,
                instanceCount,
                baseIndex,
                baseVertex,
                baseInstance);
    }

    @Override
    protected boolean onCopyBuffer(Buffer srcBuffer, Buffer dstBuffer, long srcOffset, long dstOffset, long size) {
        assert (srcBuffer.getUsage() & (BufferUsageFlags.kUpload | BufferUsageFlags.kHostVisible))
                == (BufferUsageFlags.kUpload | BufferUsageFlags.kHostVisible);

        VulkanBuffer vulkanSrcBuffer = (VulkanBuffer) srcBuffer;
        VulkanBuffer vulkanDstBuffer = (VulkanBuffer) dstBuffer;
        // host buffer does not need barrier, see vkQueueSubmit
        transitionBufferAccess(vulkanDstBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT);
        commitPipelineBarriers(false, false);

        try (var stack = stackPush()) {
            VkBufferCopy.Buffer copies = VkBufferCopy.malloc(1, stack);

            copies
                    .srcOffset(srcOffset)
                    .dstOffset(dstOffset)
                    .size(size);

            vkCmdCopyBuffer(
                    mCommandBuffer,
                    vulkanSrcBuffer.vkBuffer(),
                    vulkanDstBuffer.vkBuffer(),
                    copies
            );
        }

        //TODO we do not yet support combinations, like for both Vertex/Uniform read.
        // do we need to track buffers and insert barriers in other cases?
        int dstStage;
        int dstAccess;
        int usageFlags = dstBuffer.getUsage();
        if ((usageFlags & BufferUsageFlags.kVertex) != 0) {
            dstStage = VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
            dstAccess = VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
        } else if ((usageFlags & BufferUsageFlags.kIndex) != 0) {
            //TODO in Vulkan 1.3, use synchronization2
            dstStage = VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
            dstAccess = VK_ACCESS_INDEX_READ_BIT;
        } else if ((usageFlags & BufferUsageFlags.kUniform) != 0) {
            dstStage = VK_PIPELINE_STAGE_VERTEX_SHADER_BIT |
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            dstAccess = VK_ACCESS_UNIFORM_READ_BIT;
        } else if ((usageFlags & BufferUsageFlags.kStorage) != 0) {
            // multiple purposes are not implemented yet
            throw new UnsupportedOperationException();
        } else {
            throw new UnsupportedOperationException();
        }

        transitionBufferAccess(vulkanDstBuffer, dstStage, dstAccess);

        return true;
    }

    @Override
    protected boolean onCopyBufferToImage(@RawPtr Buffer srcBuffer, @RawPtr Image dstImage,
                                          @NonNull List<BufferImageCopyData> copyData) {
        assert (srcBuffer.getUsage() & (BufferUsageFlags.kUpload | BufferUsageFlags.kHostVisible))
                == (BufferUsageFlags.kUpload | BufferUsageFlags.kHostVisible);

        VulkanBuffer vulkanSrcBuffer = (VulkanBuffer) srcBuffer;
        VulkanImage vulkanDstImage = (VulkanImage) dstImage;
        // host buffer does not need barrier, see vkQueueSubmit
        transitionImageLayout(vulkanDstImage,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT,
                0,
                vulkanDstImage.getDesc().getMipLevelCount());
        commitPipelineBarriers(false, false);

        int bytesPerBlock = vulkanDstImage.getDesc().getBytesPerBlock();
        int oneBlockTexels = vulkanDstImage.getDesc().isCompressed()
                ? 4
                : 1;
        int aspectMask = VKUtil.getFullAspectMask(vulkanDstImage.getVulkanDesc().mVkFormat);
        try (var stack = stackPush()) {
            VkBufferImageCopy.Buffer copies = VkBufferImageCopy.malloc(copyData.size(), stack);

            for (int i = 0; i < copyData.size(); i++) {
                var data = copyData.get(i);
                var copy = copies.get();
                assert data.mBufferRowBytes % bytesPerBlock == 0;
                int rowLength = (int) (data.mBufferRowBytes / bytesPerBlock * oneBlockTexels);
                assert rowLength >= data.mWidth;
                copy
                        .bufferOffset(data.mBufferOffset)
                        .bufferRowLength(rowLength)
                        .bufferImageHeight(0); // height is tightly packed
                var subresource = copy.imageSubresource();
                subresource
                        .aspectMask(aspectMask)
                        .mipLevel(data.mMipLevel)
                        .baseArrayLayer(data.mArraySlice)
                        .layerCount(data.mNumSlices);
                copy.imageOffset().set(data.mX, data.mY, data.mZ);
                copy.imageExtent().set(data.mWidth, data.mHeight, data.mDepth);
            }

            copies.flip();

            vkCmdCopyBufferToImage(
                    mCommandBuffer,
                    vulkanSrcBuffer.vkBuffer(),
                    vulkanDstImage.vkImage(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copies
            );
        }

        return true;
    }

    @Override
    protected boolean onCopyImage(Image srcImage, int srcL, int srcT, int srcR, int srcB, Image dstImage, int dstX,
                                  int dstY, int mipLevel) {

        VulkanImage vulkanSrcImage = (VulkanImage) srcImage;
        VulkanImage vulkanDstImage = (VulkanImage) dstImage;

        try (var stack = stackPush()) {
            VkImageCopy.Buffer copy = VkImageCopy.malloc(1, stack);

            copy.srcSubresource().set(
                    VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1
            );
            copy.srcOffset().set(srcL, srcT, 0);
            copy.dstSubresource().set(
                    VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1
            );
            copy.dstOffset().set(dstX, dstY, 0);
            copy.extent().set(srcR - srcL, srcB - srcT, 1);

            transitionImageLayout(vulkanSrcImage,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_ACCESS_TRANSFER_READ_BIT,
                    0,
                    vulkanSrcImage.getMipLevelCount());
            transitionImageLayout(vulkanDstImage,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_ACCESS_TRANSFER_WRITE_BIT,
                    0,
                    vulkanDstImage.getMipLevelCount());
            commitPipelineBarriers(false, false);

            vkCmdCopyImage(
                    mCommandBuffer,
                    vulkanSrcImage.vkImage(),
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    vulkanDstImage.vkImage(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copy
            );
        }

        return true;
    }

    @Override
    public void prepareSurfaceForStateUpdate(@NonNull Image surfaceImage,
                                             @Nullable ImageMutableState newState,
                                             boolean present) {
        VulkanImage vulkanImage = (VulkanImage) surfaceImage;

        if (present) {
            transitionImageLayout(vulkanImage,
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                    0,
                    0,
                    vulkanImage.getMipLevelCount());
        } else {
            assert newState != null;
            VulkanImageMutableState vulkanState = (VulkanImageMutableState) newState;
            int newLayout = vulkanState.getImageLayout();
            if (newLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
                newLayout = vulkanImage.getVulkanMutableState().getImageLayout();
            }

            int dstStage;
            //TODO we will do this when there's demand
            // we may introduce Engine.ResourceAccessFlags to fine-control?
        }
    }

    // reference
    @VisibleForTesting
    public void setupImageForPresent(VulkanImage srcImage, int width, int height, long dstImage, boolean first) {
        try (var stack = stackPush()) {
            VkImageCopy.Buffer copy = VkImageCopy.malloc(1, stack);

            copy.srcSubresource().set(
                    VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1
            );
            copy.srcOffset().set(0, 0, 0);
            copy.dstSubresource().set(
                    VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1
            );
            copy.dstOffset().set(0, 0, 0);
            copy.extent().set(width, height, 1);

            transitionImageLayout(srcImage,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_ACCESS_TRANSFER_READ_BIT,
                    0,
                    srcImage.getMipLevelCount());

            {
                var barrier = mImageBarriers.get();
                barrier
                        .sType$Default()
                        .pNext(NULL)
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .oldLayout(first ? VK_IMAGE_LAYOUT_UNDEFINED : KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(srcImage.getVulkanMutableState().getQueueFamilyIndex())
                        .dstQueueFamilyIndex(srcImage.getVulkanMutableState().getQueueFamilyIndex())
                        .image(dstImage);
                var subresource = barrier.subresourceRange();
                subresource
                        .aspectMask(VKUtil.getFullAspectMask(VK_FORMAT_R8G8B8A8_UNORM))
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                mSrcStageMask |= VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                mDstStageMask |= VK_PIPELINE_STAGE_TRANSFER_BIT;
            }

            commitPipelineBarriers(false, false);

            vkCmdCopyImage(
                    mCommandBuffer,
                    srcImage.vkImage(),
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    dstImage,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copy
            );

            {
                var barrier = mImageBarriers.get();
                barrier
                        .sType$Default()
                        .pNext(NULL)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(0)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                        .srcQueueFamilyIndex(srcImage.getVulkanMutableState().getQueueFamilyIndex())
                        .dstQueueFamilyIndex(srcImage.getVulkanMutableState().getQueueFamilyIndex())
                        .image(dstImage);
                var subresource = barrier.subresourceRange();
                subresource
                        .aspectMask(VKUtil.getFullAspectMask(VK_FORMAT_R8G8B8A8_UNORM))
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                mSrcStageMask |= VK_PIPELINE_STAGE_TRANSFER_BIT;
                mDstStageMask |= VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

                commitPipelineBarriers(false, false);
            }
        }
    }

    @Override
    protected void begin() {
        mDevice.flushRenderCalls();
        mDevice.purgeStaleResourcesIfNeeded();
        try (var stack = stackPush()) {
            var beginInfo = VkCommandBufferBeginInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
                    .pInheritanceInfo(null);
            VKUtil._CHECK_ERROR_(vkBeginCommandBuffer(mCommandBuffer, beginInfo));
        }
        mIsRecording = true;
    }

    protected void end() {
        commitPipelineBarriers(false, false);

        assert mIsRecording;
        vkEndCommandBuffer(mCommandBuffer);

        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    //// synchronization used outside render pass
    //TODO better way to handle more cases, such as adding engine-level ResourceAccess bit flags?

    @SuppressWarnings("DuplicateBranchesInSwitch")
    protected static int imageLayoutToSrcStageMask(int layout, boolean wrapped) {
        // this can be inferred from old layout, for common usage
        int srcStageMask;
        switch (layout) {
            case VK_IMAGE_LAYOUT_GENERAL:
                // this is really used for storage image only
                // there's special path for input attachments
                srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                break;

            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
                srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                break;

            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL:
                srcStageMask = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT |
                        VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                break;

            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                break;

            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                srcStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
                break;

            case VK_IMAGE_LAYOUT_PREINITIALIZED:
                //TODO verify the usage of linear tiling?
                srcStageMask = VK_PIPELINE_STAGE_HOST_BIT;
                break;

            case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR:
                srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                break;

            case VK_IMAGE_LAYOUT_UNDEFINED:
                // if wrapped, the image is typically a swapchain image,
                // otherwise wait nothing
                srcStageMask = wrapped
                        ? VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                break;

            default:
                throw new IllegalStateException();
        }
        return srcStageMask;
    }

    protected static int imageLayoutToSrcAccessMask(int layout) {
        // this can be inferred from old layout, other writes don't need barriers in practical use,
        // like VK_ACCESS_HOST_WRITE_BIT is guaranteed by queue submission
        int srcAccessMask;
        switch (layout) {
            case VK_IMAGE_LAYOUT_GENERAL:
                // no fine control
                srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
                break;

            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
                srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                break;

            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
                srcAccessMask = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                break;

            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
                break;

            case VK_IMAGE_LAYOUT_UNDEFINED:
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL:
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
            case VK_IMAGE_LAYOUT_PREINITIALIZED:
            case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR:
                // read only = no writes to sync
                srcAccessMask = 0;
                break;

            default:
                throw new IllegalStateException();
        }
        return srcAccessMask;
    }

    protected void transitionImageLayout(VulkanImage image,
                                         int newLayout,
                                         int dstStageMask,
                                         int dstAccessMask,
                                         int baseMipLevel,
                                         int mipLevelCount) {
        int currentLayout = image.getVulkanMutableState().getImageLayout();
        int currentQueueIndex = image.getVulkanMutableState().getQueueFamilyIndex();
        //TODO transition queue family if needed, mainly for future Vulkan Video encode/decode queue
        // and dedicated present queue

        // layout same and read only, based on our actual usage, no barrier is needed
        if (currentLayout == newLayout &&
                (currentLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ||
                        currentLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ||
                        currentLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)) {
            return;
        }

        int srcStageMask = imageLayoutToSrcStageMask(currentLayout, image.isWrapped());
        int srcAccessMask = imageLayoutToSrcAccessMask(currentLayout);

        assert mImageBarriers.hasRemaining();
        var barrier = mImageBarriers.get();
        barrier
                .sType$Default()
                .pNext(NULL)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask)
                .oldLayout(currentLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(currentQueueIndex)
                .dstQueueFamilyIndex(currentQueueIndex)
                .image(image.vkImage());
        var subresource = barrier.subresourceRange();
        subresource
                .aspectMask(VKUtil.getFullAspectMask(image.getVulkanDesc().getVkFormat()))
                .baseMipLevel(baseMipLevel)
                .levelCount(mipLevelCount)
                .baseArrayLayer(0)
                .layerCount(image.getDesc().getLayerCount());

        image.getVulkanMutableState().setImageLayout(newLayout);

        mSrcStageMask |= srcStageMask;
        mDstStageMask |= dstStageMask;
    }

    //TODO really need to handle more cases, currently only supports Granite renderer
    protected static int bufferAccessToSrcStageMask(int access) {
        int srcStageMask;
        switch (access) {
            case 0:
                srcStageMask = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                break;

            case VK_ACCESS_TRANSFER_WRITE_BIT:
            case VK_ACCESS_TRANSFER_READ_BIT:
                srcStageMask = VK_PIPELINE_STAGE_TRANSFER_BIT;
                break;

            case VK_ACCESS_SHADER_WRITE_BIT:
                srcStageMask = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                break;

            case VK_ACCESS_INDEX_READ_BIT:
            case VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT:
                srcStageMask = VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
                break;

            case VK_ACCESS_UNIFORM_READ_BIT:
                srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                break;

            case (VK_ACCESS_INDIRECT_COMMAND_READ_BIT):
                srcStageMask = VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
                break;

            case VK_ACCESS_HOST_READ_BIT:
            case VK_ACCESS_HOST_WRITE_BIT:
                srcStageMask = VK_PIPELINE_STAGE_HOST_BIT;
                break;

            default:
                throw new IllegalStateException();
        }

        return srcStageMask;
    }

    protected boolean accessIsReadOnly(int access) {

        int readBits = VK_ACCESS_INDIRECT_COMMAND_READ_BIT |
                VK_ACCESS_INDEX_READ_BIT |
                VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT |
                VK_ACCESS_UNIFORM_READ_BIT |
                VK_ACCESS_TRANSFER_READ_BIT |
                VK_ACCESS_HOST_READ_BIT |
                VK_ACCESS_INPUT_ATTACHMENT_READ_BIT |
                VK_ACCESS_SHADER_READ_BIT |
                VK_ACCESS_COLOR_ATTACHMENT_READ_BIT |
                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT |
                VK_ACCESS_MEMORY_READ_BIT;

        if (access == (access & readBits)) {
            return true;
        }

        int writeBits = VK_ACCESS_TRANSFER_WRITE_BIT |
                VK_ACCESS_SHADER_WRITE_BIT |
                VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT |
                VK_ACCESS_MEMORY_WRITE_BIT |
                VK_ACCESS_HOST_WRITE_BIT;

        assert (access & ~(readBits | writeBits)) == 0;

        return false;
    }

    protected void transitionBufferAccess(VulkanBuffer buffer,
                                          int dstStageMask,
                                          int dstAccessMask) {
        int currentAccess = buffer.getCurrentAccess();

        //TODO the following code only handles if accesses contain only one-bit
        int srcStageMask = bufferAccessToSrcStageMask(currentAccess);
        buffer.setCurrentAccess(dstAccessMask);

        if (srcStageMask == VK_PIPELINE_STAGE_HOST_BIT) {
            // rely on queue submit, no barrier is needed
            return;
        }

        boolean currentReadOnly = accessIsReadOnly(currentAccess);
        if (currentReadOnly && accessIsReadOnly(dstAccessMask) &&
                (currentAccess & dstAccessMask) == dstAccessMask) {
            // already visible
            return;
        }

        // a read -> X barrier used to make available memory visible
        // so srcAccessMask is 0, since it's already flushed to L2 in the last barrier

        assert mBufferBarriers.hasRemaining();
        var barrier = mBufferBarriers.get();
        barrier
                .sType$Default()
                .pNext(NULL)
                .srcAccessMask(currentReadOnly ? 0 : currentAccess)
                .dstAccessMask(dstAccessMask)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(buffer.vkBuffer())
                .offset(0)
                .size(buffer.getSize());

        mSrcStageMask |= srcStageMask;
        mDstStageMask |= dstStageMask;
    }

    protected void commitPipelineBarriers(boolean forSubpassSelfDependency,
                                          boolean byRegionDependency) {
        // barriers cannot be inserted within render pass, except for self-dependency barriers
        assert !mInRenderPass || forSubpassSelfDependency;
        // only self-dependency barrier can combine by-region bit
        assert forSubpassSelfDependency || !byRegionDependency;
        mBufferBarriers.flip();
        mImageBarriers.flip();
        // currently we never submit a pipeline barrier without at least one buffer or image barrier
        if (mBufferBarriers.hasRemaining() || mImageBarriers.hasRemaining()) {
            vkCmdPipelineBarrier(mCommandBuffer,
                    mSrcStageMask, mDstStageMask,
                    byRegionDependency ? VK_DEPENDENCY_BY_REGION_BIT : 0,
                    null, mBufferBarriers, mImageBarriers);
            mSrcStageMask = 0;
            mDstStageMask = 0;
        }
        mBufferBarriers.clear();
        mImageBarriers.clear();
    }

    @Override
    protected void destroy() {
        mBufferBarriers.free();
        mImageBarriers.free();
        MemoryUtil.memFree(mActiveVertexBuffers);
        MemoryUtil.memFree(mActiveVertexOffsets);
        for (SetBindingState setBindingState : mSetsBindingState) {
            setBindingState.destroy();
        }
    }
}

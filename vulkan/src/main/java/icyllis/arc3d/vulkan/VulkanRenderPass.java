/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ManagedResource;
import icyllis.arc3d.engine.RenderPassDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTBlendOperationAdvanced;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Managed by {@link VulkanRenderPassSet} or tracked by in-flight command buffer.
 */
public final class VulkanRenderPass extends ManagedResource {

    public static final int kLoadOpBits = 2;
    public static final int kStoreOpBits = 1;
    public static final int kLoadStoreOpsBits = kLoadOpBits + kStoreOpBits;

    static {
        //noinspection ConstantValue
        assert Engine.LoadOp.kCount <= (1 << kLoadOpBits);
        //noinspection ConstantValue
        assert Engine.StoreOp.kCount <= (1 << kStoreOpBits);
        //noinspection ConstantValue
        assert (Caps.MAX_COLOR_TARGETS + 1 + 1) * kLoadStoreOpsBits <= Integer.SIZE;
        // MRT + one resolve + one depth/stencil, 30 bits used
    }

    /**
     * Extracts all the load/store ops in the desc into int32 key.
     */
    public static int extractLoadStoreOps(@NonNull RenderPassDesc desc) {
        assert desc.mColorAttachments.length <= Caps.MAX_COLOR_TARGETS;
        int bits = 0;
        for (int i = 0; i < desc.mColorAttachments.length; i++) {
            var attachment = desc.mColorAttachments[i];
            bits |= ((attachment.mLoadOp) | (attachment.mStoreOp << kLoadOpBits))
                    << (kLoadStoreOpsBits * i);
        }
        if (desc.mColorResolveAttachment.isUsed()) {
            bits |= ((desc.mColorResolveAttachment.mLoadOp) | (desc.mColorResolveAttachment.mStoreOp << kLoadOpBits))
                    << (kLoadStoreOpsBits * (Caps.MAX_COLOR_TARGETS));
        }
        if (desc.mDepthStencilAttachment.isUsed()) {
            bits |= ((desc.mDepthStencilAttachment.mLoadOp) | (desc.mDepthStencilAttachment.mStoreOp << kLoadOpBits))
                    << (kLoadStoreOpsBits * (Caps.MAX_COLOR_TARGETS + 1));
        }
        return bits;
    }

    private final long mRenderPass;

    private final int mDenseLoadStoreOps;

    private final int mGranularityX;
    private final int mGranularityY;

    private VulkanRenderPass(VulkanDevice device, long renderPass,
                             int denseLoadStoreOps,
                             int granularityX, int granularityY) {
        super(device);
        mRenderPass = renderPass;
        mDenseLoadStoreOps = denseLoadStoreOps;
        mGranularityX = granularityX;
        mGranularityY = granularityY;
        assert renderPass != VK_NULL_HANDLE;
    }

    @SuppressWarnings("resource")
    @Nullable
    @SharedPtr
    public static VulkanRenderPass make(@NonNull VulkanDevice device,
                                        @NonNull RenderPassDesc desc) {

        // Order:
        // color attachments;
        // resolve attachment;
        // depth/stencil attachment

        try (var stack = MemoryStack.stackPush()) {

            int numAttachments = desc.mColorAttachments.length +
                    (desc.mColorResolveAttachment.isUsed() ? 1 : 0) +
                    (desc.mDepthStencilAttachment.isUsed() ? 1 : 0);
            int vkSamples = VKUtil.toVkSampleCount(desc.mSampleCount);
            boolean loadFromResolve = (desc.mRenderPassFlags & RenderPassDesc.kLoadFromResolve_Flag) != 0;

            // All attachment descriptions to be used
            VkAttachmentDescription.Buffer pAttachments = VkAttachmentDescription
                    .calloc(numAttachments, stack);

            // Refs to attachments that are used by subpasses
            VkAttachmentReference.Buffer pColorRefs = VkAttachmentReference
                    .malloc(desc.mColorAttachments.length, stack);
            // MRT and MSAA will not be used together
            VkAttachmentReference.Buffer pColorResolveRefs = VkAttachmentReference
                    .malloc(1, stack);
            // Use dst as input, at most one
            VkAttachmentReference.Buffer pInputRefs = VkAttachmentReference
                    .malloc(1, stack);
            VkAttachmentReference pDepthStencilRef = VkAttachmentReference
                    .malloc(stack);

            VkSubpassDescription.Buffer pSubpasses = VkSubpassDescription
                    .calloc(loadFromResolve ? 2 : 1, stack);
            VkSubpassDependency.Buffer pDependencies = VkSubpassDependency
                    .malloc(2, stack);

            int mainSubpass = loadFromResolve ? 1 : 0;
            VkSubpassDescription mainSubpassDesc = pSubpasses.get(mainSubpass);

            mainSubpassDesc
                    .flags(0)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .pPreserveAttachments(null);
                    // other fields are zeros

            // set up color attachments
            if (desc.mColorAttachments.length > 0) {

                // Keep initial/final layouts to VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                // if reading from the dst as an input attachment, use VK_IMAGE_LAYOUT_GENERAL for the subpass,
                // this is more efficient on certain GPUs.

                int imageLayoutDuringSubpass = (desc.mRenderPassFlags & RenderPassDesc.kUseDstAsInput_Flag) != 0
                        ? VK_IMAGE_LAYOUT_GENERAL
                        : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

                for (int i = 0; i < desc.mColorAttachments.length; i++) {
                    var colorDesc = desc.mColorAttachments[i];
                    var colorRef = pColorRefs.get();

                    if (!colorDesc.isUsed()) {
                        colorRef
                                .attachment(VK_ATTACHMENT_UNUSED)
                                .layout(VK_IMAGE_LAYOUT_UNDEFINED);
                    } else {
                        colorRef
                                .attachment(pAttachments.position())
                                .layout(imageLayoutDuringSubpass);

                        pAttachments.get()
                                .flags(0)
                                .format(VKUtil.toVkFormat(colorDesc.mFormat))
                                .samples(vkSamples)
                                .loadOp(VKUtil.toVkLoadOp(colorDesc.mLoadOp))
                                .storeOp(VKUtil.toVkStoreOp(colorDesc.mStoreOp))
                                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                                .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                                .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                    }
                }

                pColorRefs.flip();
                assert pColorRefs.remaining() == desc.mColorAttachments.length;

                mainSubpassDesc.pColorAttachments(pColorRefs);

                if ((desc.mRenderPassFlags & (RenderPassDesc.kUseDstAsInput_Flag |
                        RenderPassDesc.kUseNonCoherentAdvBlend_Flag)) != 0) {
                    // set up a self dependency
                    var selfDependency = pDependencies.get();

                    int dstStageMask = 0;
                    int dstAccessMask = 0;

                    if ((desc.mRenderPassFlags & RenderPassDesc.kUseDstAsInput_Flag) != 0) {
                        dstStageMask |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                        dstAccessMask |= VK_ACCESS_INPUT_ATTACHMENT_READ_BIT;

                        assert desc.mColorAttachments.length == 1;
                        mainSubpassDesc.pInputAttachments(pColorRefs);
                    }

                    if ((desc.mRenderPassFlags & RenderPassDesc.kUseNonCoherentAdvBlend_Flag) != 0) {
                        dstStageMask |= VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                        dstAccessMask |= EXTBlendOperationAdvanced.VK_ACCESS_COLOR_ATTACHMENT_READ_NONCOHERENT_BIT_EXT;
                    }

                    assert dstStageMask != 0;

                    selfDependency
                            .srcSubpass(mainSubpass)
                            .dstSubpass(mainSubpass)
                            .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                            .dstStageMask(dstStageMask)
                            .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                            .dstAccessMask(dstAccessMask)
                            .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
                }
            } else {
                assert desc.mRenderPassFlags == 0;
            }

            if (desc.mColorResolveAttachment.isUsed()) {
                assert desc.mColorAttachments.length == 1;

                // if first subpass is LoadFromResolve
                int startImageLayout = loadFromResolve
                        ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                        : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

                var colorResolveDesc = desc.mColorResolveAttachment;

                var colorResolveRef = pColorResolveRefs.get();
                colorResolveRef
                        .attachment(pAttachments.position())
                        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                pAttachments.get()
                        .flags(0)
                        .format(VKUtil.toVkFormat(colorResolveDesc.mFormat))
                        .samples(vkSamples)
                        .loadOp(VKUtil.toVkLoadOp(colorResolveDesc.mLoadOp))
                        .storeOp(VKUtil.toVkStoreOp(colorResolveDesc.mStoreOp))
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(startImageLayout)
                        .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                pColorResolveRefs.flip();
                assert pColorResolveRefs.remaining() == 1;

                mainSubpassDesc.pResolveAttachments(pColorResolveRefs);

                if (loadFromResolve) {
                    var inputRef = pInputRefs.get();
                    inputRef
                            .attachment(colorResolveRef.attachment())
                            .layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

                    pInputRefs.flip();
                    assert pInputRefs.remaining() == 1;
                    assert pColorRefs.remaining() == 1;

                    // The load subpass will always be the first
                    var loadSubpassDesc = pSubpasses.get(0);
                    loadSubpassDesc
                            .flags(0)
                            .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                            .pInputAttachments(pInputRefs)
                            .pColorAttachments(pColorRefs)
                            .pPreserveAttachments(null);

                    var loadDependency = pDependencies.get();
                    loadDependency
                            .srcSubpass(0)
                            .dstSubpass(mainSubpass)
                            .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                            .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                            .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
                }
            } else {
                assert !loadFromResolve;
            }

            if (desc.mDepthStencilAttachment.isUsed()) {
                var depthStencilDesc = desc.mColorResolveAttachment;

                int vkLoadOp = VKUtil.toVkLoadOp(depthStencilDesc.mLoadOp);
                int vkStoreOp = VKUtil.toVkStoreOp(depthStencilDesc.mStoreOp);

                pDepthStencilRef
                        .attachment(pAttachments.position())
                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                pAttachments.get()
                        .flags(0)
                        .format(VKUtil.toVkFormat(depthStencilDesc.mFormat))
                        .samples(vkSamples)
                        .loadOp(vkLoadOp)
                        .storeOp(vkStoreOp)
                        .stencilLoadOp(vkLoadOp)
                        .stencilStoreOp(vkStoreOp)
                        .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                        .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            } else {
                pDepthStencilRef
                        .attachment(VK_ATTACHMENT_UNUSED)
                        .layout(VK_IMAGE_LAYOUT_UNDEFINED);
            }

            mainSubpassDesc.pDepthStencilAttachment(pDepthStencilRef);

            assert !pSubpasses.hasRemaining();
            pAttachments.flip();
            pSubpasses.flip();
            pDependencies.flip();

            VkRenderPassCreateInfo pCreateInfo = VkRenderPassCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .pAttachments(pAttachments)
                    .pSubpasses(pSubpasses)
                    .pDependencies(pDependencies);

            var pRenderPass = stack.mallocLong(1);

            var result = vkCreateRenderPass(device.vkDevice(),
                    pCreateInfo, null, pRenderPass);
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanRenderPass: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            VkExtent2D pGranularity = VkExtent2D.malloc(stack);

            vkGetRenderAreaGranularity(device.vkDevice(),
                    pRenderPass.get(0), pGranularity);

            return new VulkanRenderPass(device, pRenderPass.get(0),
                    extractLoadStoreOps(desc),
                    pGranularity.width(), pGranularity.height());
        }
    }

    public long vkRenderPass() {
        return mRenderPass;
    }

    public int getDenseLoadStoreOps() {
        return mDenseLoadStoreOps;
    }

    public int getGranularityX() {
        return mGranularityX;
    }

    public int getGranularityY() {
        return mGranularityY;
    }

    @Override
    protected void deallocate() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyRenderPass(device.vkDevice(),
                mRenderPass, null);
    }
}

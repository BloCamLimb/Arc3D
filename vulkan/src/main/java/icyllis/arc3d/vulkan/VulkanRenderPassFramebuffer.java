/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2024 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.engine.Framebuffer;
import icyllis.arc3d.engine.FramebufferDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Managed by {@link VulkanRenderPassSet} and tracked by command buffers and
 * graphics pipelines.
 */
public final class VulkanRenderPassFramebuffer extends Framebuffer {

    private final long mFramebuffer;

    private VulkanRenderPassFramebuffer(VulkanDevice device, long framebuffer) {
        super(device);
        mFramebuffer = framebuffer;
        assert framebuffer != VK_NULL_HANDLE;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "ManualMinMaxCalculation"})
    private static boolean addAttachmentView(@NonNull LongBuffer pAttachments,
                                             FramebufferDesc.@NonNull AttachmentDesc desc) {
        if (desc.mAttachment == null) {
            return true;
        }
        @RawPtr
        VulkanImage attachmentImage = (VulkanImage) desc.mAttachment.get();
        assert attachmentImage != null;
        @RawPtr
        VulkanImageView attachmentImageView = attachmentImage.findOrCreateRenderTargetView(
                desc.mArraySlice >= 0 ? desc.mArraySlice : 0,
                desc.mArraySlice >= 0 ? 1 : attachmentImage.getDesc().getLayerCount()
        );
        if (attachmentImageView == null) {
            return false;
        }
        pAttachments.put(attachmentImageView.vkImageView());
        return true;
    }

    // CompatibleRenderPass is raw pointer because it's managed by GraphicsPipeline,
    // if the pipeline is deleted, then the framebuffer must also not be in-flight.
    // Framebuffer is either deleted when any VulkanImage is deleted, or
    // VulkanRenderPassSet is deleted. ImageViews are managed by VulkanImage.
    @Nullable
    @SharedPtr
    public static VulkanRenderPassFramebuffer make(@NonNull VulkanDevice device,
                                                   @NonNull @RawPtr VulkanRenderPass compatibleRenderPass,
                                                   @NonNull FramebufferDesc desc) {

        // Order:
        // color attachments;
        // resolve attachment;
        // depth/stencil attachment

        try (var stack = MemoryStack.stackPush()) {

            int numAttachments = desc.mColorAttachments.length +
                    (desc.mColorResolveAttachment.mAttachment != null ? 1 : 0) +
                    (desc.mDepthStencilAttachment.mAttachment != null ? 1 : 0);

            var pAttachments = stack.mallocLong(numAttachments);
            for (int i = 0; i < desc.mColorAttachments.length; i++) {
                var colorDesc = desc.mColorAttachments[i];
                if (!addAttachmentView(pAttachments, colorDesc)) {
                    return null;
                }
            }
            if (!addAttachmentView(pAttachments, desc.mColorResolveAttachment)) {
                return null;
            }
            if (!addAttachmentView(pAttachments, desc.mDepthStencilAttachment)) {
                return null;
            }
            pAttachments.flip();

            var pCreateInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(compatibleRenderPass.vkRenderPass())
                    .pAttachments(pAttachments)
                    .width(desc.mWidth)
                    .height(desc.mHeight)
                    .layers(desc.mLayers);
            var pFramebuffer = stack.mallocLong(1);
            var result = vkCreateFramebuffer(device.vkDevice(),
                    pCreateInfo, null, pFramebuffer);
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanFramebuffer: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanRenderPassFramebuffer(device, pFramebuffer.get(0));
        }
    }

    public long vkFramebuffer() {
        return mFramebuffer;
    }

    @Override
    protected void deallocate() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyFramebuffer(device.vkDevice(),
                mFramebuffer, null);
    }
}

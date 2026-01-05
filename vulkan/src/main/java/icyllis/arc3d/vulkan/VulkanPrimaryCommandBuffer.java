/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Engine.LoadOp;
import icyllis.arc3d.engine.FramebufferDesc;
import icyllis.arc3d.engine.Image;
import icyllis.arc3d.engine.QueueManager;
import icyllis.arc3d.engine.RenderPassDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import java.util.List;
import java.util.function.Function;

import static org.lwjgl.vulkan.VK11.*;

/**
 * This class is created with a command pool, a single primary command buffer, and
 * (optional) a set of secondary command buffers. After submission, this enters
 * the in flight state, and we will continuously check whether the GPU has finished
 * the work to reset/recycle the command pool (and its command buffers).
 */
public final class VulkanPrimaryCommandBuffer extends VulkanCommandBuffer {

    // command buffers submitted to the same queue will finish in submission order

    private final long mCommandPool;

    private long mSubmitFence = VK_NULL_HANDLE;

    private VulkanPrimaryCommandBuffer(VulkanDevice device,
                                       VulkanResourceProvider resourceProvider,
                                       long commandBuffer,
                                       long commandPool) {
        super(device, resourceProvider, commandBuffer);
        mCommandPool = commandPool;
        assert commandPool != VK_NULL_HANDLE;
    }

    @Nullable
    public static VulkanPrimaryCommandBuffer create(@NonNull VulkanDevice device,
                                                    VulkanResourceProvider resourceProvider) {
        int cmdPoolCreateFlags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
        if (device.isProtectedContext()) {
            cmdPoolCreateFlags |= VK_COMMAND_POOL_CREATE_PROTECTED_BIT;
        }
        long commandPool;
        try (var stack = MemoryStack.stackPush()) {
            var pCreateInfo = VkCommandPoolCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .flags(cmdPoolCreateFlags)
                    .queueFamilyIndex(device.getQueueIndex());
            var pCommandPool = stack.mallocLong(1);
            var result = vkCreateCommandPool(
                    device.vkDevice(),
                    pCreateInfo,
                    null,
                    pCommandPool
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to VulkanCommandPool: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }
            commandPool = pCommandPool.get(0);
        }

        long primaryCommandBuffer =
                VulkanCommandBuffer.allocate(device, commandPool, /*forSecondary*/ false);
        if (primaryCommandBuffer == VK_NULL_HANDLE) {
            vkDestroyCommandPool(
                    device.vkDevice(),
                    commandPool,
                    null
            );
            return null;
        }
        return new VulkanPrimaryCommandBuffer(device,
                resourceProvider,
                primaryCommandBuffer,
                commandPool);
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Override
    public <T> void setupForShaderRead(@NonNull List<@RawPtr T> textures,
                                       @NonNull Function<? super T, @RawPtr Image> toTexture) {
        for (int i = 0; i < textures.size(); i++) {
            @RawPtr
            VulkanImage image = (VulkanImage) toTexture.apply(textures.get(i));
            transitionImageLayout(image,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT |
                            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_ACCESS_SHADER_READ_BIT,
                    0,
                    image.getDesc().getMipLevelCount());
            if (!mImageBarriers.hasRemaining()) {
                commitPipelineBarriers(false, false);
            }
        }
        commitPipelineBarriers(false, false);
    }

    /**
     * Matches {@link VulkanRenderPass}.
     */
    private int setupRenderTargetLayouts(@NonNull RenderPassDesc renderPassDesc,
                                         @NonNull FramebufferDesc framebufferDesc) {
        // setup execution dependency, availability, visibility,
        // do layout transition to match VulkanRenderPass if needed
        int attachmentCount = 0;
        for (var colorAttachment : framebufferDesc.mColorAttachments) {
            if (colorAttachment.mAttachment == null) {
                // skip placeholder color buffer
                continue;
            }
            @RawPtr
            VulkanImage attachment = (VulkanImage) colorAttachment.mAttachment.get();
            assert attachment != null;

            // blending requires READ bit
            int dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            int dstAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

            // requires self-dependency barriers
            if ((renderPassDesc.mRenderPassFlags & RenderPassDesc.kUseDstAsInput_Flag) != 0) {
                dstStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                dstAccess |= VK_ACCESS_INPUT_ATTACHMENT_READ_BIT;
            }

            transitionImageLayout(attachment,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    dstStage,
                    dstAccess,
                    0,
                    attachment.getDesc().getMipLevelCount());
            attachmentCount++;
        }

        if (framebufferDesc.mColorResolveAttachment.mAttachment != null) {
            @RawPtr
            VulkanImage attachment = (VulkanImage) framebufferDesc.mColorResolveAttachment.mAttachment.get();
            assert attachment != null;

            int layout;
            int dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            int dstAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
            if ((renderPassDesc.mRenderPassFlags & RenderPassDesc.kLoadFromResolve_Flag) != 0) {
                layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                dstStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                dstAccess |= VK_ACCESS_INPUT_ATTACHMENT_READ_BIT;
            } else {
                layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                dstAccess |= VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            }

            transitionImageLayout(attachment,
                    layout,
                    dstStage,
                    dstAccess,
                    0,
                    attachment.getDesc().getMipLevelCount());
            attachmentCount++;
        }

        if (framebufferDesc.mDepthStencilAttachment.mAttachment != null) {
            @RawPtr
            VulkanImage attachment = (VulkanImage) framebufferDesc.mDepthStencilAttachment.mAttachment.get();
            assert attachment != null;

            transitionImageLayout(attachment,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT |
                            VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                    0,
                    attachment.getDesc().getMipLevelCount());
            attachmentCount++;
        }

        commitPipelineBarriers(false, false);

        return attachmentCount;
    }

    private static void adjust_render_area(@Nullable Rect2ic renderPassBounds,
                                           int granularityHoriz,
                                           int granularityVert,
                                           int framebufferWidth,
                                           int framebufferHeight,
                                           VkRect2D outRenderArea) {
        if (renderPassBounds == null) {
            outRenderArea.offset().set(0, 0);
            outRenderArea.extent().set(framebufferWidth, framebufferHeight);
            return;
        }

        // Either multiple of the granularity or the entire framebuffer

        int left, top, right, bottom;
        if (granularityHoriz == 0 || granularityHoriz == 1) {
            left = renderPassBounds.left();
            right = renderPassBounds.right();
        } else {
            // Start with the right side of rect so we know if we end up going past the framebufferWidth.
            int rightAdj = renderPassBounds.right() % granularityHoriz;
            if (rightAdj != 0) {
                rightAdj = granularityHoriz - rightAdj;
            }
            right = renderPassBounds.right() + rightAdj;
            if (right > framebufferWidth) {
                right = framebufferWidth;
                left = 0;
            } else {
                left = renderPassBounds.left() - renderPassBounds.left() % granularityHoriz;
            }
        }

        if (granularityVert == 0 || granularityVert == 1) {
            top = renderPassBounds.top();
            bottom = renderPassBounds.bottom();
        } else {
            // Start with the bottom side of rect so we know if we end up going past the framebufferHeight.
            int bottomAdj = renderPassBounds.bottom() % granularityVert;
            if (bottomAdj != 0) {
                bottomAdj = granularityVert - bottomAdj;
            }
            bottom = renderPassBounds.bottom() + bottomAdj;
            if (bottom > framebufferHeight) {
                bottom = framebufferHeight;
                top = 0;
            } else {
                top = renderPassBounds.top() - renderPassBounds.top() % granularityVert;
            }
        }

        outRenderArea.offset().set(left, top);
        outRenderArea.extent().set(right - left, bottom - top);
    }

    private static void gather_clear_values(@NonNull RenderPassDesc renderPassDesc,
                                            float[] clearColors,
                                            float clearDepth,
                                            int clearStencil,
                                            VkClearValue.Buffer outClearValues) {
        int clearColorCount = 0;
        for (var colorAttachment : renderPassDesc.mColorAttachments) {
            if (colorAttachment.isUsed()) {
                if (colorAttachment.mLoadOp == LoadOp.kClear) {
                    VkClearColorValue clearColor = outClearValues
                            .position(clearColorCount)
                            .color();
                    for (int i = 0; i < 4; i++) {
                        clearColor.float32(i, clearColors[clearColorCount * 4 + i]);
                    }
                }
                clearColorCount++;
            }
            // else placeholder attachment is not indexing
        }
        if (renderPassDesc.mColorResolveAttachment.isUsed()) {
            if (renderPassDesc.mColorResolveAttachment.mLoadOp == LoadOp.kClear) {
                VkClearColorValue clearColor = outClearValues
                        .position(clearColorCount)
                        .color();
                for (int i = 0; i < 4; i++) {
                    clearColor.float32(i, clearColors[clearColorCount * 4 + i]);
                }
            }
            clearColorCount++;
        }
        if (renderPassDesc.mDepthStencilAttachment.isUsed()) {
            if (renderPassDesc.mDepthStencilAttachment.mLoadOp == LoadOp.kClear) {
                VkClearDepthStencilValue clearDepthStencil = outClearValues
                        .position(clearColorCount)
                        .depthStencil();
                clearDepthStencil
                        .depth(clearDepth)
                        .stencil(clearStencil);
            }
            clearColorCount++;
        }
        outClearValues.clear();
        // render pass desc and framebuffer desc must match
        assert clearColorCount == outClearValues.remaining();
    }

    @Override
    public boolean beginRenderPass(@NonNull RenderPassDesc renderPassDesc,
                                   @NonNull FramebufferDesc framebufferDesc,
                                   @Nullable Rect2ic renderPassBounds,
                                   float[] clearColors,
                                   float clearDepth,
                                   int clearStencil) {
        @SharedPtr
        VulkanRenderPassSet renderPassSet = mDevice.findOrCreateCompatibleRenderPassSet(renderPassDesc);
        if (renderPassSet == null) {
            return false;
        }

        @SharedPtr
        VulkanRenderPass renderPass = renderPassSet.findOrCreateRenderPass(
                mDevice, renderPassDesc);
        if (renderPass == null) {
            renderPassSet.unref();
            return false;
        }

        @SharedPtr
        VulkanRenderPassFramebuffer framebuffer = renderPassSet.findOrCreateFramebuffer(
                mDevice, framebufferDesc);
        if (framebuffer == null) {
            renderPass.unref();
            renderPassSet.unref();
            return false;
        }

        int attachmentCount = setupRenderTargetLayouts(renderPassDesc, framebufferDesc);

        try (var stack = MemoryStack.stackPush()) {

            VkClearValue.Buffer pClearValues = VkClearValue.malloc(attachmentCount, stack);
            gather_clear_values(renderPassDesc, clearColors, clearDepth, clearStencil, pClearValues);

            VkRenderPassBeginInfo pBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass.vkRenderPass())
                    .framebuffer(framebuffer.vkFramebuffer());

            int granularityH = renderPass.getGranularityX();
            int granularityV = renderPass.getGranularityY();
            adjust_render_area(renderPassBounds,
                    granularityH, granularityV,
                    framebufferDesc.mWidth, framebufferDesc.mHeight,
                    pBeginInfo.renderArea());

            pBeginInfo.pClearValues(pClearValues);

            vkCmdBeginRenderPass(mCommandBuffer,
                    pBeginInfo,
                    VK_SUBPASS_CONTENTS_INLINE);
        }

        mInRenderPass = true;

        trackResource(renderPass);
        trackResource(framebuffer);

        return true;
    }

    @Override
    public void endRenderPass() {
        vkCmdEndRenderPass(mCommandBuffer);
        mInRenderPass = false;
    }

    @Override
    protected boolean submit(QueueManager queueManager) {
        if (mIsRecording) {
            end();
        }

        mDevice.purgeStaleResourcesIfNeeded();

        if (mSubmitFence == VK_NULL_HANDLE) {
            try (var stack = MemoryStack.stackPush()) {
                var pCreateInfo = VkFenceCreateInfo
                        .calloc(stack)
                        .sType$Default()
                        .flags(0);
                var pFence = stack.mallocLong(1);
                var result = vkCreateFence(
                        mDevice.vkDevice(),
                        pCreateInfo,
                        null,
                        pFence
                );
                mDevice.checkResult(result);
                if (result != VK_SUCCESS) {
                    mSubmitFence = VK_NULL_HANDLE;
                    return false;
                }
                mSubmitFence = pFence.get(0);
            }
            assert mSubmitFence != VK_NULL_HANDLE;
        } else {
            var result = vkResetFences(
                    mDevice.vkDevice(),
                    mSubmitFence
            );
            mDevice.checkResult(result);
        }

        //TODO submit here

        return true;
    }

    @Override
    protected boolean checkFinishedAndReset() {
        if (mSubmitFence == VK_NULL_HANDLE) {
            return true;
        }

        var result = vkGetFenceStatus(mDevice.vkDevice(), mSubmitFence);

        //TODO OOM should be handled carefully

        switch (result) {
            case VK_SUCCESS:
            case VK_ERROR_DEVICE_LOST:

                vkResetCommandPool(
                        mDevice.vkDevice(),
                        mCommandPool,
                        0
                );

                callFinishedCallbacks(result == VK_SUCCESS);
                releaseResources();

                return true;

            case VK_NOT_READY:
            case VK_ERROR_OUT_OF_DEVICE_MEMORY:
            case VK_ERROR_OUT_OF_HOST_MEMORY:
            default:
                return false;
        }
    }

    @Override
    protected void waitUntilFinished() {
        if (mSubmitFence == VK_NULL_HANDLE) {
            return;
        }

        var result = vkWaitForFences(
                mDevice.vkDevice(),
                mSubmitFence,
                true,
                0xffffffffffffffffL
        );
        mDevice.checkResult(result);
    }

    private void destroy() {
        //TODO call this in some way
        vkDestroyCommandPool(
                mDevice.vkDevice(),
                mCommandPool,
                null
        );
    }
}

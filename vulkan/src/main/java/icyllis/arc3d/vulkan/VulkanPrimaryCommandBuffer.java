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

import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.engine.FramebufferDesc;
import icyllis.arc3d.engine.QueueManager;
import icyllis.arc3d.engine.RenderPassDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;

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

    @Override
    public boolean beginRenderPass(RenderPassDesc renderPassDesc,
                                   FramebufferDesc framebufferDesc,
                                   Rect2ic renderPassBounds,
                                   float[] clearColors,
                                   float clearDepth,
                                   int clearStencil) {
        mInRenderPass = true;
        return false;
    }

    @Override
    public void endRenderPass() {
        mInRenderPass = false;
    }

    @Override
    protected boolean submit(QueueManager queueManager) {
        if (mIsRecording) {
            end();
        }

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

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
import icyllis.arc3d.engine.Buffer;
import icyllis.arc3d.engine.BufferImageCopyData;
import icyllis.arc3d.engine.CommandBuffer;
import icyllis.arc3d.engine.GraphicsPipeline;
import icyllis.arc3d.engine.Image;
import icyllis.arc3d.engine.Sampler;
import org.jspecify.annotations.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK11.*;

public abstract class VulkanCommandBuffer extends CommandBuffer {

    public static class SetBindingState {

        @RawPtr
        public Object[] mResources = new Object[16];
        @RawPtr
        public VulkanSampler[] mSamplers = new VulkanSampler[16];

        public int[] mBindingOffsets = new int[16];
        public int[] mBindingSizes = new int[16];

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
    }

    protected final VulkanDevice mDevice;
    protected final VkCommandBuffer mCommandBuffer;
    protected boolean mIsRecording = false;

    public VulkanCommandBuffer(@NonNull VulkanDevice device, long commandBuffer) {
        mDevice = device;
        mCommandBuffer = new VkCommandBuffer(commandBuffer, device.vkDevice());
        assert commandBuffer != VK_NULL_HANDLE;
    }

    protected static long allocate(@NonNull VulkanDevice device,
                                   long commandPool,
                                   boolean forSecondary) {
        assert commandPool != VK_NULL_HANDLE;
        try (var stack = MemoryStack.stackPush()) {
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

    public boolean bindGraphicsPipeline(GraphicsPipeline graphicsPipeline) {
        //TODO
        return false;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {

    }

    @Override
    public void setScissor(int x, int y, int width, int height) {

    }

    @Override
    public void bindIndexBuffer(int indexType, Buffer buffer, long offset) {
        //vkCmdBindIndexBuffer();
    }

    @Override
    public void bindVertexBuffer(int binding, Buffer buffer, long offset) {
        // record each binding here, and bindVertexBuffers() together
    }

    @Override
    public void bindUniformBuffer(int binding, Buffer buffer, int offset, int size) {

    }

    @Override
    public void bindTextureSampler(int binding, Image texture, Sampler sampler, short swizzle) {

    }

    @Override
    public void draw(int vertexCount, int baseVertex) {
        drawInstanced(1, 0, vertexCount, baseVertex);
    }

    @Override
    public void drawIndexed(int indexCount, int baseIndex, int baseVertex) {
        drawIndexedInstanced(indexCount, baseIndex, 1, 0, baseVertex);
    }

    @Override
    public void drawInstanced(int instanceCount, int baseInstance,
                              int vertexCount, int baseVertex) {
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
        vkCmdDrawIndexed(mCommandBuffer,
                indexCount,
                instanceCount,
                baseIndex,
                baseVertex,
                baseInstance);
    }

    @Override
    protected boolean onCopyBuffer(Buffer srcBuffer, Buffer dstBuffer, long srcOffset, long dstOffset, long size) {
        return false;
    }

    @Override
    protected boolean onCopyBufferToImage(Buffer srcBuffer, Image dstImage, int srcColorType, int dstColorType,
                                          BufferImageCopyData[] copyData) {
        return false;
    }

    @Override
    protected boolean onCopyImage(Image srcImage, int srcL, int srcT, int srcR, int srcB, Image dstImage, int dstX,
                                  int dstY, int mipLevel) {
        return false;
    }

    @Override
    protected void begin() {
        try (var stack = MemoryStack.stackPush()) {
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
        assert mIsRecording;
        vkEndCommandBuffer(mCommandBuffer);

        mIsRecording = false;
    }

    @Override
    protected void waitUntilFinished() {
    }

    public void bindVertexBuffers() {
        //vkCmdBindVertexBuffers();
    }

    public boolean isRecording() {
        return mIsRecording;
    }
}

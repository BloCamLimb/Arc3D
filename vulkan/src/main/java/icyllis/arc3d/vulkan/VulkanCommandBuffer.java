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
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;

import java.util.List;
import java.util.function.Function;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK11.*;

public abstract class VulkanCommandBuffer extends CommandBuffer {

    public static class SetBindingState {

        @RawPtr
        protected Object[] mResources = new Object[16];
        @RawPtr
        protected VulkanSampler[] mSamplers = new VulkanSampler[16];

        public int[] mBindingOffsets = new int[16];
        public int[] mBindingSizes = new int[16];

        protected int mHighWaterCount;

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

    protected final SetBindingState[] mSetsBindingState = new SetBindingState[VulkanCaps.MAX_BOUND_SETS];

    //TODO release these when this is deleted
    protected VkBufferMemoryBarrier.Buffer mBufferBarriers = VkBufferMemoryBarrier.malloc(4);
    protected VkImageMemoryBarrier.Buffer mImageBarriers = VkImageMemoryBarrier.malloc(16);
    protected int mSrcStageMask, mDstStageMask;

    protected final VulkanDevice mDevice;
    protected final VulkanResourceProvider mResourceProvider;
    protected final VkCommandBuffer mCommandBuffer;
    protected boolean mIsRecording = false;
    protected boolean mInRenderPass = false;

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

    @Override
    public <T> void setupForShaderRead(@NonNull List<@RawPtr T> textures, @NonNull Function<? super T, @RawPtr Image> toTexture) {
        throw new UnsupportedOperationException();
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
        mDevice.flushRenderCalls();
        mDevice.purgeStaleResourcesIfNeeded();
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

    public void bindVertexBuffers() {
        //vkCmdBindVertexBuffers();
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    //// synchronization used outside render pass
    //TODO better way to handle more cases, such as adding engine-level ResourceAccess bit flags?

    @SuppressWarnings("DuplicateBranchesInSwitch")
    protected static int imageLayoutToSrcStageMask(int layout) {
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
                srcStageMask = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
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
                srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT |
                        VK_ACCESS_SHADER_WRITE_BIT;
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

        // layout same and read only, based on our actual usage, no barrier is needed
        if (currentLayout == newLayout &&
                (currentLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ||
                        currentLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ||
                        currentLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)) {
            return;
        }

        int srcStageMask = imageLayoutToSrcStageMask(currentLayout);
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
        // there must be something to insert
        assert mBufferBarriers.hasRemaining() || mImageBarriers.hasRemaining();
        vkCmdPipelineBarrier(mCommandBuffer,
                mSrcStageMask, mDstStageMask,
                byRegionDependency ? VK_DEPENDENCY_BY_REGION_BIT : 0,
                null, mBufferBarriers, mImageBarriers);
        mBufferBarriers.clear();
        mImageBarriers.clear();
        mSrcStageMask = 0;
        mDstStageMask = 0;
    }
}

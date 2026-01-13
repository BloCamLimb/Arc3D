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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.RefCounted;
import icyllis.arc3d.core.SharedPtr;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;

/**
 * Backend-specific command buffer, executing thread only.
 */
public abstract class CommandBuffer {

    private final ObjectArrayList<@SharedPtr RefCounted> mTrackingUsageResources = new ObjectArrayList<>();
    private final ObjectArrayList<@SharedPtr Resource> mTrackingCommandBufferResources = new ObjectArrayList<>();

    private final ArrayDeque<FlushInfo.FinishedCallback> mFinishedCallbacks = new ArrayDeque<>();

    public abstract <T> void setupForShaderRead(@NonNull List<@RawPtr T> textures,
                                                @NonNull Function<? super T, @RawPtr Image> toTexture);

    /**
     * Begin render pass. If successful, {@link #endRenderPass()} must be called.
     * <p>
     * Placeholder attachment is not indexing in <var>clearColors</var>. For example, if you have
     * 6 color attachments and 1 resolve attachment, but 3 of the color attachments are placeholders
     * (i.e., {@link RenderPassDesc.AttachmentDesc#isUsed} returns false), then you only need to
     * provide <code>(6-3+1)*4</code> continuous floats. If loadOp is not clear, then ignore the slot.
     *
     * @param renderPassDesc   descriptor to create a render pass
     * @param framebufferDesc  descriptor to create a framebuffer
     * @param renderPassBounds content bounds of this render pass, null means entire framebuffer
     * @param clearColors      clear color for each color attachment, and then resolve attachment
     * @param clearDepth       clear depth
     * @param clearStencil     clear stencil (unsigned)
     * @return success or not
     */
    public abstract boolean beginRenderPass(@NonNull RenderPassDesc renderPassDesc,
                                            @NonNull FramebufferDesc framebufferDesc,
                                            @Nullable Rect2ic renderPassBounds,
                                            float[] clearColors,
                                            float clearDepth,
                                            int clearStencil);

    /**
     * Set viewport, must be called after {@link #beginRenderPass}.
     */
    public abstract void setViewport(int x, int y, int width, int height);

    /**
     * Set scissor, must be called after {@link #beginRenderPass}.
     */
    public abstract void setScissor(int x, int y, int width, int height);

    /**
     * Bind graphics pipeline. Due to async compiling, it may fail.
     * Render pass scope, caller must track the pipeline.
     *
     * @param graphicsPipeline the pipeline object
     * @return success or not
     */
    public abstract boolean bindGraphicsPipeline(@RawPtr GraphicsPipeline graphicsPipeline);

    /**
     * Render pass scope, caller must track the buffer.
     *
     * @param indexType see {@link Engine.IndexType}
     */
    public abstract void bindIndexBuffer(int indexType,
                                         @RawPtr Buffer buffer,
                                         long offset);

    /**
     * Render pass scope, caller must track the buffer.
     */
    public abstract void bindVertexBuffer(int binding,
                                          @RawPtr Buffer buffer,
                                          long offset);

    /**
     * Render pass scope, caller must track the buffer.
     */
    public abstract void bindUniformBuffer(int set,
                                           int binding,
                                           @RawPtr Buffer buffer,
                                           int offset,
                                           int size);

    /**
     * Bind texture view and sampler to the same binding point (combined image sampler).
     * Render pass scope, caller must track the image and sampler.
     *
     * @param set     the descriptor set index
     * @param binding the binding index
     * @param texture the texture image
     * @param swizzle the swizzle of the texture view for shader read, see {@link Swizzle}
     * @param sampler the sampler state
     */
    public abstract void bindTextureSampler(int set,
                                            int binding,
                                            @RawPtr Image texture,
                                            short swizzle,
                                            @RawPtr Sampler sampler);

    /**
     * Records a non-indexed draw to current command buffer.
     * Render pass scope.
     *
     * @param vertexCount the number of vertices to draw
     * @param baseVertex  the index of the first vertex to draw
     */
    public abstract void draw(int vertexCount, int baseVertex);

    /**
     * Records an indexed draw to current command buffer.
     * For OpenGL ES, if base vertex is unavailable, gl_VertexID always begins at 0.
     * Render pass scope.
     *
     * @param indexCount the number of vertices to draw
     * @param baseIndex  the base index within the index buffer
     * @param baseVertex the value added to the vertex index before indexing into the vertex buffer
     */
    public abstract void drawIndexed(int indexCount, int baseIndex,
                                     int baseVertex);

    /**
     * Records a non-indexed draw to current command buffer.
     * For OpenGL, regardless of the baseInstance value, gl_InstanceID always begins at 0.
     * Render pass scope.
     *
     * @param instanceCount the number of instances to draw
     * @param baseInstance  the instance ID of the first instance to draw
     * @param vertexCount   the number of vertices to draw
     * @param baseVertex    the index of the first vertex to draw
     */
    public abstract void drawInstanced(int instanceCount, int baseInstance,
                                       int vertexCount, int baseVertex);

    /**
     * Records an indexed draw to current command buffer.
     * For OpenGL ES, if base vertex is unavailable, gl_VertexID always begins at 0.
     * For OpenGL, regardless of the baseInstance value, gl_InstanceID always begins at 0.
     * Render pass scope.
     *
     * @param indexCount    the number of vertices to draw
     * @param baseIndex     the base index within the index buffer
     * @param instanceCount the number of instances to draw
     * @param baseInstance  the instance ID of the first instance to draw
     * @param baseVertex    the value added to the vertex index before indexing into the vertex buffer
     */
    public abstract void drawIndexedInstanced(int indexCount, int baseIndex,
                                              int instanceCount, int baseInstance,
                                              int baseVertex);

    /**
     * End the current render pass.
     */
    public abstract void endRenderPass();

    /**
     * Performs a buffer-to-buffer copy.
     * <p>
     * Can only be used outside render passes.
     * <p>
     * The caller must track resources if success.
     */
    public final boolean copyBuffer(@RawPtr Buffer srcBuffer,
                                    @RawPtr Buffer dstBuffer,
                                    long srcOffset,
                                    long dstOffset,
                                    long size) {
        assert srcBuffer != null && dstBuffer != null;
        return onCopyBuffer(srcBuffer, dstBuffer, srcOffset, dstOffset, size);
    }

    protected abstract boolean onCopyBuffer(@RawPtr Buffer srcBuffer,
                                            @RawPtr Buffer dstBuffer,
                                            long srcOffset,
                                            long dstOffset,
                                            long size);

    /**
     * Performs a buffer-to-image copy.
     * <p>
     * Can only be used outside render passes.
     * <p>
     * The caller must track resources if success.
     */
    public final boolean copyBufferToImage(@RawPtr Buffer srcBuffer,
                                           @RawPtr Image dstImage,
                                           @NonNull List<@NonNull BufferImageCopyData> copyData) {
        assert srcBuffer != null && dstImage != null && !copyData.isEmpty();
        if (!dstImage.isSampledImage() && !dstImage.isStorageImage()) {
            //TODO support copy to render buffer?
            return false;
        }
        return onCopyBufferToImage(srcBuffer, dstImage, copyData);
    }

    protected abstract boolean onCopyBufferToImage(@RawPtr Buffer srcBuffer,
                                                   @RawPtr Image dstImage,
                                                   @NonNull List<@NonNull BufferImageCopyData> copyData);

    /**
     * Perform an image-to-image copy, with the specified regions. Scaling is
     * not allowed.
     * <p>
     * If their dimensions are same and formats are compatible, then this method will
     * attempt to perform copy. Otherwise, this method will attempt to perform blit,
     * which may include format conversion.
     * <p>
     * Only mipmap level <var>level</var> of 2D images will be copied, without any
     * multisampled buffer and depth/stencil buffer.
     * <p>
     * Can only be used outside render passes.
     * <p>
     * The caller must track resources if success.
     *
     * @return success or not
     */
    public final boolean copyImage(@RawPtr Image srcImage,
                                   int srcL, int srcT, int srcR, int srcB,
                                   @RawPtr Image dstImage,
                                   int dstX, int dstY,
                                   int mipLevel) {
        assert srcImage != null && dstImage != null;
        return onCopyImage(srcImage, srcL, srcT, srcR, srcB, dstImage, dstX, dstY, mipLevel);
    }

    protected abstract boolean onCopyImage(@RawPtr Image srcImage,
                                           int srcL, int srcT, int srcR, int srcB,
                                           @RawPtr Image dstImage,
                                           int dstX, int dstY,
                                           int mipLevel);

    /**
     * Make device writes to buffer <em>available</em> on host domain.
     */
    public void setupBufferForHostRead(@RawPtr Buffer buffer) {

    }

    public void waitSemaphore(@Nullable BackendSemaphore waitSemaphore) {
    }

    public void signalSemaphore(@Nullable BackendSemaphore signalSemaphore) {
    }

    /**
     * Takes a Usage ref on the Resource that will be released when the command buffer
     * has finished execution.
     * <p>
     * This is used for resources that can be updated by host after submission,
     * and resources that can be shared between recording contexts and submissions.
     *
     * @param resource the resource to move
     */
    public final void trackResource(@SharedPtr Resource resource) {
        if (resource == null) {
            return;
        }
        mTrackingUsageResources.add(resource);
    }

    /**
     * Takes a ref on the ManagedResource that will be released when the command buffer
     * has finished execution.
     *
     * @param resource the resource to move
     */
    public final void trackResource(@SharedPtr ManagedResource resource) {
        if (resource == null) {
            return;
        }
        mTrackingUsageResources.add(resource);
    }

    /**
     * Takes a ref on the RecycledResource that will be recycled when the command buffer
     * has finished execution.
     *
     * @param resource the resource to move
     */
    public final void trackResource(@SharedPtr RecycledResource resource) {
        if (resource == null) {
            return;
        }
        mTrackingUsageResources.add(resource);
    }

    /**
     * Takes a CommandBuffer ref on the Resource that will be released when the command buffer
     * has finished execution.
     * <p>
     * CommandBuffer ref allows a Resource to be returned to ResourceCache for reuse while
     * the CommandBuffer is still executing on the GPU. This is most commonly used for
     * GPU-only Resources.
     *
     * @param resource the resource to move
     */
    public final void trackCommandBufferResource(@SharedPtr Resource resource) {
        if (resource == null) {
            return;
        }
        resource.refCommandBuffer();
        mTrackingCommandBufferResources.add(resource);
        resource.unref();
    }

    public void addFinishedCallback(FlushInfo.FinishedCallback callback) {
        mFinishedCallbacks.add(callback);
    }

    // called by Queue, begin command buffer
    protected abstract void begin();

    // called by Queue, end command buffer and submit to queue
    protected abstract boolean submit(QueueManager queueManager);

    // called by Queue
    protected abstract boolean checkFinishedAndReset();

    /**
     * Blocks the current thread and waits for GPU to finish outstanding works.
     */
    // called by Queue, waitForQueue()
    protected abstract void waitUntilFinished();

    // called by subclass
    protected final void callFinishedCallbacks(boolean success) {
        for (var callback : mFinishedCallbacks) {
            callback.onFinished(success);
        }
        mFinishedCallbacks.clear();
    }

    // called by subclass
    protected final void releaseResources() {
        mTrackingUsageResources.forEach(RefCounted::unref);
        mTrackingUsageResources.clear();
        mTrackingCommandBufferResources.forEach(Resource::unrefCommandBuffer);
        mTrackingCommandBufferResources.clear();
    }
}

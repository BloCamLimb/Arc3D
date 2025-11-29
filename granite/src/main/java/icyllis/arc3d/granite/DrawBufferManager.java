/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Buffer;
import icyllis.arc3d.engine.BufferViewInfo;
import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.Resource;
import icyllis.arc3d.engine.ResourceProvider;
import icyllis.arc3d.engine.VertexInputLayout;
import icyllis.arc3d.granite.task.Task;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Manages dynamic and streaming GPU buffers.
 * <p>
 * For streaming buffers: use persistent mapped host-visible buffers for Vulkan and
 * OpenGL 4.4; use mutable buffer and CPU staging buffer for OpenGL 4.3 and below.
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class DrawBufferManager implements AutoCloseable {

    /**
     * We expect buffers for meshes to be at least 128KB.
     */
    public static final int kVertexBufferMinSize = 1 << 17;
    public static final int kVertexBufferMaxSize = 1 << 20;  //  1MB
    public static final int kIndexBufferSize = 1 << 13;
    public static final int kUniformBufferMinSize = 1 << 14; // 16KB
    public static final int kUniformBufferMaxSize = 1 << 20; //  1MB

    /**
     * This instance can have only one owner at the same time.
     * When one owner finished using it, call reset() then clear the reference
     * to this object, and the ownership will be transferred to the manager.
     */
    public final class BufferSubAllocator {

        final int mStateIndex;

        // this allocator is the only owner of the buffer
        @SharedPtr
        Buffer mBuffer;

        long mMappedPtr;
        int mOffset;
        int mStride;
        int mRemaining;

        BufferSubAllocator(int stateIndex,
                           @SharedPtr Buffer buffer,
                           long mappedPtr,
                           int stride) {
            assert stride > 0;
            mStateIndex = stateIndex;
            mBuffer = buffer;
            mMappedPtr = mappedPtr;
            mStride = stride;
            mRemaining = (int) mBuffer.getSize() / mStride;
        }

        /**
         * Returns the underlying buffer object back to the pool and recycles this allocator.
         */
        public void reset() {
            if (mBuffer == null) {
                // should not happen
                assert false;
                return;
            }

            BufferState state = mCurrentBuffers[mStateIndex];
            BufferSubAllocator buffer = state.mAvailableBuffer;
            if (buffer == this ||                                       // can't stash itself, and buffer is unique owner
                    remainingBytes() < state.mOffsetAlignment ||                // too small
                    remainingBytes() < (buffer != null ? buffer.remainingBytes() : 0))  // not larger than existing
            {
                mUsedBuffers.add(mBuffer); // move
                mBuffer = null;
                mMappedPtr = NULL;
            } else {
                if (buffer != null) {
                    buffer.reset();
                }
                state.mAvailableBuffer = this;
            }
        }

        /**
         * Reset for a new block or new vertex format.
         */
        public void resetForNewBinding() {
            mRemaining = 0;
            mStride = 0;
        }

        /**
         * Returns the number of remaining bytes in the GPU buffer, assuming an alignment of 1.
         */
        public int remainingBytes() {
            return mBuffer != null ? (int) mBuffer.getSize() - mOffset : 0;
        }

        /**
         * Return a host coherent memory address, if NULL then allocation is failed
         * <p>
         * The stride is the size of vertex, instance, uniform block, or shader storage block.
         * If either is an array of structs, it can also be the size of that struct. In both
         * cases, the stride should already be aligned base on block/struct alignment rules.
         *
         * @param count   the minimum number of blocks/structs to reserve
         * @param stride  the size of block/struct
         * @param align   the alignment requirement
         * @param outInfo the out buffer bind info
         * @return a mapped, write-only address, or NULL if failed
         */
        public long getMappedSubrange(int count,
                                      int stride,
                                      int align,
                                      @NonNull BufferViewInfo outInfo) {
            // write-combining buffer is automatically mapped
            assert mMappedPtr != NULL || mBuffer == null;
            prepareForStride(stride, align, count);

            if (mBuffer == null || count > mRemaining) {
                outInfo.set(null);
                return NULL;
            }

            assert mStride > 0;
            int size = count * mStride;

            int offset = mOffset;
            outInfo.mBuffer = mBuffer;
            outInfo.mOffset = offset;
            outInfo.mSize = size;
            mOffset += size;
            mRemaining -= count;
            return mMappedPtr + offset;
        }

        /**
         * Returns the number of {@code stride} blocks left in the buffer, where {@code stride} was the last
         * value passed to getMappedSubrange.
         */
        public int availableWithStride() {
            return mRemaining;
        }

        /**
         * Returns a buffer writer for {@code count*stride} bytes, where {@code stride} was the last
         * value passed to getMappedSubrange.
         * <p>
         * If {@code count <= availableWithStride()}, then the appended range will be contiguous with
         * the BufferViewInfo returned from the last call to getMappedSubrange (and/or other calls to
         * appendMappedStride). The binding's size should be increased by count*stride, which is the
         * caller's responsibility.
         * <p>
         * Otherwise, NULL will be returned and no change is made to the allocator.
         */
        public long appendMappedWithStride(int count) {
            assert (count > 0 && (mMappedPtr != NULL || mRemaining == 0));

            if (mBuffer == null || count > mRemaining) {
                return NULL;
            }

            assert mStride > 0;
            int size = count * mStride;

            int offset = mOffset;
            mOffset += size;
            mRemaining -= count;
            return mMappedPtr + offset;
        }

        void prepareForStride(int stride, int align, int minCount) {
            assert stride > 0 && align > 0 && minCount >= 0;
            if (mBuffer != null) {
                if (mStride == stride && align == stride) {
                    assert mOffset % align == 0;
                    return;
                }

                // For vertex buffers, this should be LCM, but `align` is already
                // a multiple of stride, and a multiple of OffsetAlignment.
                // For UBO and SSBO, `align` is 1, and we just need to align the
                // offset to the alignment of the block.
                int alignment = align;
                if (mStride == 0) {
                    alignment = MathUtil.alignTo(alignment,
                            mCurrentBuffers[mStateIndex].mOffsetAlignment);
                }

                if (remainingBytes() >= alignment - 1) {
                    // alignment can be NPoT
                    int offset = MathUtil.alignUp(mOffset, alignment);
                    assert offset <= mBuffer.getSize();
                    mStride = stride;
                    int remaining = ((int) mBuffer.getSize() - offset) / stride;
                    if (remaining >= minCount) {
                        mOffset = offset;
                        mRemaining = remaining;
                        return;
                    }
                }
            }
            mRemaining = 0;
        }
    }

    private static final class BufferState {

        final int mUsage;
        final String mLabel;

        final int mOffsetAlignment; // must be power of two
        final int mMinBlockSize; // must be power of two
        final int mMaxBlockSize; // must be power of two

        // used for block growth
        int mLastBufferSize;

        @Nullable
        BufferSubAllocator mAvailableBuffer;

        BufferState(Caps caps, int usage, int minBlockSize, int maxBlockSize, String label) {
            assert minBlockSize <= maxBlockSize;
            mUsage = usage;
            // UBO and SSBO offset alignment is 256 at most
            if ((usage & Engine.BufferUsageFlags.kUniform) != 0) {
                mOffsetAlignment = caps.minUniformBufferOffsetAlignment();
            } else if ((usage & Engine.BufferUsageFlags.kStorage) != 0) {
                mOffsetAlignment = caps.minStorageBufferOffsetAlignment();
            } else {
                mOffsetAlignment = VertexInputLayout.Attribute.OFFSET_ALIGNMENT;
            }
            mMinBlockSize = MathUtil.alignTo(minBlockSize, mOffsetAlignment);
            mMaxBlockSize = MathUtil.alignTo(maxBlockSize, mOffsetAlignment);
            mLabel = label;
        }
    }

    // @formatter:off
    static final int kVertexBufferIndex     = 0;
    static final int kIndexBufferIndex      = 1;
    static final int kUniformBufferIndex    = 2;
    static final int kStorageBufferIndex    = 3;
    final BufferState[] mCurrentBuffers = new BufferState[4];
    // @formatter:on

    final ObjectArrayList<@SharedPtr Buffer> mUsedBuffers = new ObjectArrayList<>();

    private final ResourceProvider mResourceProvider;

    // If mapping failed on Buffers created/managed by this DrawBufferManager,
    // remember so that the next Recording will fail.
    private boolean mMappingFailed = false;

    public DrawBufferManager(@NonNull Caps caps, @NonNull ResourceProvider resourceProvider) {
        mResourceProvider = resourceProvider;
        mCurrentBuffers[kVertexBufferIndex] = new BufferState(
                caps,
                Engine.BufferUsageFlags.kVertex | Engine.BufferUsageFlags.kHostVisible,
                kVertexBufferMinSize, kVertexBufferMaxSize,
                "DirectVertexBuffer"
        );
        mCurrentBuffers[kIndexBufferIndex] = new BufferState(
                caps,
                Engine.BufferUsageFlags.kIndex | Engine.BufferUsageFlags.kHostVisible,
                kIndexBufferSize, kIndexBufferSize,
                "DirectIndexBuffer"
        );
        mCurrentBuffers[kUniformBufferIndex] = new BufferState(
                caps,
                Engine.BufferUsageFlags.kUniform | Engine.BufferUsageFlags.kHostVisible,
                kUniformBufferMinSize, kUniformBufferMaxSize,
                "DirectUniformBuffer"
        );
        mCurrentBuffers[kStorageBufferIndex] = new BufferState(
                caps,
                Engine.BufferUsageFlags.kStorage | Engine.BufferUsageFlags.kHostVisible,
                kUniformBufferMinSize, kUniformBufferMaxSize,
                "DirectStorageBuffer"
        );
    }




    /**
     * Allocate write-combining buffer.
     *
     * @return buffer, or null
     */
    @Nullable
    public BufferSubAllocator getMappableVertexBuffer(int reservedCount, int stride,
                                                      int alignment) {
        return prepareBuffer(kVertexBufferIndex, reservedCount, stride, alignment);
    }

    /**
     * Allocate write-combining buffer.
     * The buffer can be used only for uniform buffer.
     *
     * @return buffer, or null
     */
    @Nullable
    public BufferSubAllocator getMappableUniformBuffer(int count, int stride) {
        return prepareBuffer(kUniformBufferIndex, count, stride, 1);
    }

    /**
     * Allocate write-combining buffer.
     *
     * @return buffer, or null
     */
    @Nullable
    public BufferSubAllocator getMappableStorageBuffer(int count, int stride) {
        return prepareBuffer(kStorageBufferIndex, count, stride, 1);
    }

    /*
     * Allocate write-combining buffer and return a host coherent memory address.
     * The buffer can be used only for uniform buffer. Return pointer is 4-byte aligned
     * according to GPU requirements, if NULL then allocation is failed.
     *
     * @param requiredBytes uniform buffer size (aligned) * count
     * @param outInfo       buffer bind info
     * @return write-only address, or NULL
     * @see #alignUniformBlockSize(int)
     */
    /*public long getUniformPointer(int requiredBytes, BufferViewInfo outInfo) {
        return prepareMappedPointer(
                mCurrentBuffers[kUniformBufferIndex],
                requiredBytes,
                outInfo,
                "DirectUniformBuffer"
        );
    }*/

    public void putBackVertexBytes(int unusedBytes) {

    }

    public int alignUniformBlockSize(int dataSize) {
        return MathUtil.alignTo(dataSize, mCurrentBuffers[kUniformBufferIndex].mOffsetAlignment);
    }

    @Nullable
    private BufferSubAllocator prepareBuffer(
            int stateIndex,
            int count,
            int stride,
            int align) {
        BufferState state = mCurrentBuffers[stateIndex];
        // The size for a buffer is aligned to the minimum block size for better resource reuse.
        long bytes = (long) count * stride;
        if (bytes > Integer.MAX_VALUE - (state.mMinBlockSize + 1)) {
            bytes = 0;
        }
        if (mMappingFailed || bytes <= 0) {
            return null;
        }

        boolean mappable = (state.mUsage & Engine.BufferUsageFlags.kHostVisible) != 0;

        if (state.mAvailableBuffer != null) {
            var buffer = state.mAvailableBuffer;
            buffer.resetForNewBinding();
            buffer.prepareForStride(stride, align, count);
            if (buffer.availableWithStride() >= count) {
                assert buffer.mBuffer != null;
                assert (buffer.mMappedPtr != NULL) == mappable;
                state.mAvailableBuffer = null;
                return buffer; // transfer ownership to caller
            }

            // Not enough room in the available buffer so release it and create a new buffer.
            buffer.reset();
            state.mAvailableBuffer = null;
        }

        int bufferSize = MathUtil.alignTo((int) bytes, state.mMinBlockSize);
        if (bufferSize < state.mMaxBlockSize) {
            bufferSize = MathUtil.clamp(state.mLastBufferSize * 2, bufferSize, state.mMaxBlockSize);
            state.mLastBufferSize = bufferSize;
        } else {
            state.mLastBufferSize = state.mMaxBlockSize;
        }
        assert bufferSize >= bytes && bufferSize >= state.mMinBlockSize;

        @SharedPtr Buffer buffer = mResourceProvider.findOrCreateBuffer(
                bufferSize, state.mUsage, state.mLabel
        );
        if (buffer == null) {
            setMappingFailed();
            return null;
        }

        long mappedPtr = NULL;
        if (mappable) {
            mappedPtr = buffer.map();
            if (mappedPtr == NULL) {
                // Mapping a direct draw buffer failed
                setMappingFailed();
                return null;
            }
        }

        // The returned buffer is not set to mAvailableBuffer because it is going to be passed up to
        // the caller for their use first.
        return this.new BufferSubAllocator(stateIndex, buffer, // move
                mappedPtr, stride);
    }

    private void setMappingFailed() {
        mMappingFailed = true;

        for (var state : mCurrentBuffers) {
            if (state.mAvailableBuffer != null) {
                state.mAvailableBuffer.reset();
                state.mAvailableBuffer = null;
            }
            state.mLastBufferSize = 0;
        }

        for (int i = 0; i < mUsedBuffers.size(); i++) {
            var buffer = mUsedBuffers.get(i);
            if (buffer.isMapped()) {
                // no need to flush any data
                buffer.unmap(0);
            }
            buffer.unref();
        }
        mUsedBuffers.clear();
    }

    /**
     * Let possible users check if the manager is already in a bad mapping state and skip any extra
     * work that will be wasted because the next Recording snap will fail.
     */
    public boolean hasMappingFailed() {
        return mMappingFailed;
    }

    /**
     * Finalizes all buffers and transfers ownership of them to given list.
     * Returns true on success and false if hasMappingFailed() returns true.
     * <p>
     * Regardless of success or failure, the DrawBufferManager is reset to a valid initial state
     * for recording buffer data for the next Recording.
     *
     * @param outTasks        receive tasks
     * @param outResourceRefs receive ownership of resources
     */
    public boolean flush(Consumer<@SharedPtr Task> outTasks,
                         ObjectArrayList<@SharedPtr Resource> outResourceRefs) {
        if (mMappingFailed) {
            assert mUsedBuffers.isEmpty();
            // check and reset
            mMappingFailed = false;
            return false;
        }

        for (var state : mCurrentBuffers) {
            // Reset all available buffer sub allocators since they won't be allocatable anymore.
            // This pushes the underlying resource and transfer range to mUsedBuffers
            if (state.mAvailableBuffer != null) {
                state.mAvailableBuffer.reset();
                state.mAvailableBuffer = null;
            }
            state.mLastBufferSize = 0;
        }

        for (int i = 0; i < mUsedBuffers.size(); i++) {
            var buffer = mUsedBuffers.get(i);
            if (buffer.isMapped()) {
                // flush changes
                buffer.unmap();
            }
        }
        // move all
        outResourceRefs.addAll(mUsedBuffers);
        mUsedBuffers.clear();

        return true;
    }

    @Override
    public void close() {
        // same process
        setMappingFailed();
    }
}

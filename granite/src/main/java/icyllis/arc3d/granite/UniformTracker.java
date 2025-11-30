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

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.BufferViewInfo;
import icyllis.arc3d.granite.DrawBufferManager.BufferSubAllocator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * @see UniformDataCache
 */
public final class UniformTracker implements AutoCloseable {

    @RawPtr
    private final DrawBufferManager mBufferManager;
    @RawPtr
    private final UniformDataCache mUniformDataCache;

    // The GPU buffer we are sub-allocating. UBOs will always have to issue binding commands
    // when a draw needs to use a different set of uniform values.
    @Nullable
    private BufferSubAllocator mCurrentBuffer;

    // Internally track the last binding returned, so that we know whether new uploads or
    // rebinds are necessary.
    private final BufferViewInfo mLastBinding = new BufferViewInfo();

    // This keeps track of the last index used for writing uniforms from a provided uniform cache.
    // If a provided index matches the last index, the uniforms are assumed to already be written
    // and no additional uploading is performed. This assumes a UniformTracker will always be
    // provided with the same uniform cache.
    private int mLastUniformIndex = DrawPass.INVALID_INDEX;

    public UniformTracker(@RawPtr DrawBufferManager bufferManager,
                          @RawPtr UniformDataCache uniformDataCache) {
        mBufferManager = bufferManager;
        mUniformDataCache = uniformDataCache;
    }

    @Override
    public void close() {
        if (mCurrentBuffer != null) {
            mCurrentBuffer.reset();
        }
        mCurrentBuffer = null;
    }

    /**
     * Updates the current tracked uniform index and returns whether bindUniforms()
     * needs to be called. Also writes the uniform data to mapped GPU buffer if not.
     */
    public boolean writeUniforms(int uniformIndex) {
        if (uniformIndex >= DrawPass.INVALID_INDEX) {
            // don't care, we can bind anything
            return false;
        }

        if (uniformIndex == mLastUniformIndex) {
            // no change
            return false;
        }

        mLastUniformIndex = uniformIndex;

        final var slot = mUniformDataCache.lookup(uniformIndex);
        // Upload the uniform data if we haven't already.
        if (!slot.mBufferInfo.isValid()) {
            int uniformDataSize = slot.mPointer.remaining() << 2;

            long writer = MemoryUtil.NULL;
            if (mCurrentBuffer != null) {
                writer = mCurrentBuffer.getMappedSubrange(
                        1, uniformDataSize, 1, slot.mBufferInfo);
            }
            if (writer == MemoryUtil.NULL) {
                mCurrentBuffer = mBufferManager.getMappableUniformBuffer(1, uniformDataSize);
                if (mCurrentBuffer == null || (writer = mCurrentBuffer.getMappedSubrange(
                        1, uniformDataSize, 1, slot.mBufferInfo)) == MemoryUtil.NULL) {
                    // Allocation failed, recording will fail, just not to bind
                    return false;
                }
            }

            MemoryUtil.memCopy(
                    /*src*/ MemoryUtil.memAddress(slot.mPointer),
                    /*dst*/ writer,
                    uniformDataSize
            );

            // each block's offset is aligned to uniform block offset alignment
            mCurrentBuffer.resetForNewBinding();
        }

        boolean rebind = !mLastBinding.equals(slot.mBufferInfo);
        mLastBinding.set(slot.mBufferInfo);

        return rebind;
    }

    public void bindUniforms(int binding, @NonNull DrawCommandList commandList) {
        commandList.bindUniformBuffer(
                binding,
                mLastBinding
        );
    }
}

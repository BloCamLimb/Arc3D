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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.*;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import icyllis.arc3d.granite.DrawBufferManager.BufferSubAllocator;

import static org.lwjgl.system.MemoryUtil.NULL;

public final class MeshDrawWriter implements AutoCloseable {

    @RawPtr
    private final DrawBufferManager mBufferManager;
    private final DrawCommandList mCommandList;

    private BufferSubAllocator mCurrentBuffer;

    // Pipeline state matching currently bound pipeline
    private int mStaticStride;
    private int mAppendStride;

    private boolean mAppendVertices;

    private final BufferViewInfo mAppend = new BufferViewInfo();
    private final BufferViewInfo mStatic = new BufferViewInfo();
    private final BufferViewInfo mIndices = new BufferViewInfo();

    @RawPtr
    private Buffer mBoundAppendBuffer = null;
    private final BufferViewInfo mBoundStatic = new BufferViewInfo();
    private final BufferViewInfo mBoundIndices = new BufferViewInfo();

    private int mTemplateCount;

    private int mPendingCount; // # of vertices or instances (depending on mode) to be drawn

    // storage address for VertexWriter when GPU buffer mapping fails
    private long mFailureStorage = NULL;
    private int mFailureCapacity = 0;

    public MeshDrawWriter(@RawPtr DrawBufferManager bufferManager, DrawCommandList commandList) {
        mBufferManager = bufferManager;
        mCommandList = commandList;
    }

    @Override
    public void close() {
        if (mCurrentBuffer != null) {
            mCurrentBuffer.reset();
        }
        mCurrentBuffer = null;
        if (mFailureStorage != NULL) {
            MemoryUtil.nmemFree(mFailureStorage);
        }
        mFailureStorage = NULL;
    }

    /**
     * Notify that a new pipeline state will be bound, providing vertex binding points and strides.
     * This issues draw calls for pending data that relied on the old pipeline, so this must be
     * called <em>before</em> binding new pipeline.
     */
    public void newPipelineState(int vertexBinding,
                                 int instanceBinding,
                                 int vertexStride,
                                 int instanceStride) {
        flush();

        // Once flushed, any pending data must have been drawn.
        assert (mPendingCount == 0);

        assert vertexStride > 0 || instanceStride > 0;

        mStaticStride = instanceStride > 0 ? vertexStride : 0;
        mAppendStride = instanceStride > 0 ? instanceStride : vertexStride;
        mAppendVertices = instanceStride == 0;
        //TODO we should make backend not assume input binding is associated with pipeline
        // currently we force a rebind at higher-level, let backend handle actual state changes
        mBoundAppendBuffer = null;
        mBoundStatic.set(null);
        mBoundIndices.set(null);

        if (mCurrentBuffer != null) {
            // ARM hardware: On a new pipeline, the initial offset when appending
            // vertices must be 4-count aligned, otherwise align to the stride so that access can use
            // the baseInstance parameter of draw calls.
            int baseAlign = mAppendVertices ? 4 * vertexStride : instanceStride;

            mCurrentBuffer.getMappedSubrange(0,
                    mAppendStride, baseAlign, mAppend);
        }
    }

    /**
     * Notify that a new dynamic state, like scissor, descriptor set, blend constant, will be set.
     * This issues draw calls for any pending vertex and instance data collected by the writer.
     */
    public void newDynamicState() {
        flush();
    }

    // A == B && (A == null || C == D)
    // A != B || (A != null && C != D)

    // note if buffer is null, offset must be 0
    void setTemplate(@Nullable BufferViewInfo vertices,
                     @Nullable BufferViewInfo indices,
                     int templateCount) {
        if (mPendingCount == 0) {
            mStatic.set(vertices);
            mIndices.set(indices);
            mTemplateCount = templateCount;
        } else {
            assert mStatic.equals(vertices) && mIndices.equals(indices);
            assert mAppendStride == 0 || mAppend.mOffset % mAppendStride == 0;
            assert mTemplateCount == templateCount;
        }
    }

    public void flush() {
        int pendingCount = mPendingCount;
        // Skip flush if no items appended
        if (pendingCount == 0) {
            return;
        }

        // How much to advance mAppend.mOffset when the flush is completed
        int advanceCount = pendingCount;

        // ARM hardware: Unreferenced vertices in sequential indexes of 4 will be
        // speculatively executed. To work around this, we pad the buffer by requesting additional
        // space, and then ensure valid, minimally deleterious data by memsetting the padding to zero.
        if (mAppendVertices) {
            int countDiff = (-pendingCount) & 3;
            if (countDiff != 0) {
                long writer = mCurrentBuffer.appendMappedWithStride(countDiff);
                assert writer != NULL;
                // Given that sizeof(Vertex) is 4-aligned and generally does not exceed 24, a custom loop is faster than memset.
                int bytesToZero = countDiff * mAppendStride;
                assert MathUtil.isAlign4(bytesToZero) && bytesToZero <= 96;
                for (int i = 0; i < bytesToZero; i += 4) {
                    MemoryUtil.memPutInt(writer + i, 0);
                }
                advanceCount += countDiff;
            }
        }

        boolean indexed = false;
        if (mIndices.isValid()) {
            if (!mBoundIndices.equals(mIndices)) {
                mBoundIndices.set(mIndices);
                mCommandList.bindIndexBuffer(Engine.IndexType.kUShort, mIndices);
            }
            indexed = true;
        }

        // append buffer should always match the current stride, use pendingBase as a
        // pseudo-alias for offset and use offset 0 for bind() to reduce vertex binds
        assert mAppend.mOffset % mAppendStride == 0;
        int pendingBase = (int) mAppend.mOffset / mAppendStride;
        assert mAppend.mBuffer != null;
        if (mBoundAppendBuffer != mAppend.mBuffer) {
            mCommandList.bindVertexBuffer(0, mAppend.mBuffer, 0);
            mBoundAppendBuffer = mAppend.mBuffer;
        }

        if (mStatic.isValid()) {
            if (!mBoundStatic.equals(mStatic)) {
                mBoundStatic.set(mStatic);
                mCommandList.bindVertexBuffer(1, mStatic);
            }
        }

        if (mTemplateCount != 0) {
            // Instanced drawing
            assert !mAppendVertices;
            if (indexed) {
                mCommandList.drawIndexedInstanced(mTemplateCount, 0,
                        pendingCount, pendingBase, 0);
            } else {
                mCommandList.drawInstanced(pendingCount, pendingBase,
                        mTemplateCount, 0);
            }
        } else {
            assert mAppendVertices;
            if (indexed) {
                mCommandList.drawIndexed(pendingCount, 0, pendingBase);
            } else {
                mCommandList.draw(pendingCount, pendingBase);
            }
        }

        //noinspection IntegerMultiplicationImplicitCastToLong
        mAppend.mOffset += advanceCount * mAppendStride;
        mPendingCount = 0;
    }

    private long getFailureStorage(int size) {
        if (size > mFailureCapacity) {
            long newStorage = MemoryUtil.nmemRealloc(mFailureStorage, size);
            if (newStorage == NULL) {
                throw new OutOfMemoryError();
            }
            mFailureStorage = newStorage;
            mFailureCapacity = size;
            return newStorage;
        }
        return mFailureStorage;
    }

    //// Vertices

    public void beginVertices() {
        assert mAppendStride > 0;
        assert mAppendVertices;
        setTemplate(null, null, 0);
    }

    private void reallocForVertices(int count) {
        flush();

        if (mCurrentBuffer != null) {
            mCurrentBuffer.reset();
        }
        // alignment issue
        int align = 4 * mAppendStride;
        mCurrentBuffer = mBufferManager.getMappableVertexBuffer(
                /*reservedCount*/ MathUtil.align4(count),
                mAppendStride, align);
        if (mCurrentBuffer != null) {
            mCurrentBuffer.getMappedSubrange(0, mAppendStride, 1, mAppend);
        }
    }

    public void reserveVertices(int count) {
        // alignment issue
        count = Math.max(MathUtil.align4(mPendingCount + count) - mPendingCount, count);
        if (mCurrentBuffer == null || count > mCurrentBuffer.availableWithStride()) {
           reallocForVertices(count);
        }
    }

    public long appendVertices(int count) {
        reserveVertices(count);
        assert count > 0;
        assert mCurrentBuffer == null || mCurrentBuffer.availableWithStride() >= count;
        long writer;
        if (mCurrentBuffer == null || (writer = mCurrentBuffer.appendMappedWithStride(count)) == NULL) {
            int size = count * mAppendStride;
            return getFailureStorage(size);
        }
        mPendingCount += count;
        return writer;
    }

    //// Instances

    /**
     * Start writing instance data and bind static vertex buffer and index buffer.
     */
    public void beginInstances(@Nullable BufferViewInfo vertices,
                               @Nullable BufferViewInfo indices,
                               int vertexCount) {
        assert mAppendStride > 0;
        assert !mAppendVertices;
        assert vertexCount > 0;
        setTemplate(vertices, indices, vertexCount);
    }

    private void reallocForInstances(int count) {
        flush();

        if (mCurrentBuffer != null) {
            mCurrentBuffer.reset();
        }
        int align = mAppendStride;
        mCurrentBuffer = mBufferManager.getMappableVertexBuffer(
                /*reservedCount*/ count,
                mAppendStride, align);
        if (mCurrentBuffer != null) {
            mCurrentBuffer.getMappedSubrange(0, mAppendStride, 1, mAppend);
        }
    }

    public void reserveInstances(int count) {
        if (mCurrentBuffer == null || count > mCurrentBuffer.availableWithStride()) {
            reallocForInstances(count);
        }
    }

    /**
     * The caller must write <code>count * stride</code> bytes to the pointer.
     *
     * @param count instance count
     */
    public long appendInstances(int count) {
        reserveInstances(count);
        assert count > 0;
        assert mCurrentBuffer == null || mCurrentBuffer.availableWithStride() >= count;
        long writer;
        if (mCurrentBuffer == null || (writer = mCurrentBuffer.appendMappedWithStride(count)) == NULL) {
            // If the GPU mapped buffer failed, ensure we have a sufficiently large CPU address to
            // write to so that GeometrySteps don't have to worry about error handling. The Recording
            // will fail since the map failure is tracked by BufferManager.
            int size = count * mAppendStride;
            return getFailureStorage(size);
        }
        mPendingCount += count;
        return writer;
    }
}

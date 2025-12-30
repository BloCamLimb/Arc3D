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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.RawPtr;
import org.jspecify.annotations.Nullable;

/**
 * Struct that holds slice information sub-allocated from a ring buffer.
 * The ownership of the buffer and its usage in command submission must be tracked by
 * the caller (e.g. as with buffers created by BufferManager).
 */
public final class BufferSliceInfo {

    @RawPtr
    public Buffer mBuffer;
    // offset is valid only if buffer is non-null
    public long mOffset;

    public boolean isValid() {
        return mBuffer != null;
    }

    public void set(@Nullable BufferSliceInfo o) {
        if (o != null) {
            mBuffer = o.mBuffer;
            mOffset = o.mOffset;
        } else {
            mBuffer = null;
        }
    }

    public boolean equals(@Nullable BufferSliceInfo o) {
        return o == null
                ? mBuffer == null
                : mBuffer == o.mBuffer && (mBuffer == null || mOffset == o.mOffset);
    }
}

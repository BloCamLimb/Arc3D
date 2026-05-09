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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.RawPtr;
import org.jspecify.annotations.Nullable;

/**
 * Struct that can be passed into bind buffer calls on the {@link CommandBuffer}.
 * The ownership of the buffer and its usage in command submission must be tracked by
 * the caller (e.g. as with buffers created by BufferManager).
 */
public final class BufferBindInfo {

    @RawPtr
    public Buffer mBuffer;
    // offset and size are valid only if buffer is non-null
    public int mOffset;
    public int mSize;

    public boolean isValid() {
        return mBuffer != null;
    }

    public void set(@Nullable BufferBindInfo o) {
        if (o != null) {
            mBuffer = o.mBuffer;
            mOffset = o.mOffset;
            mSize = o.mSize;
        } else {
            mBuffer = null;
        }
    }

    public boolean equals(@Nullable BufferBindInfo o) {
        return o == null
                ? mBuffer == null
                : mBuffer == o.mBuffer && (mBuffer == null || (mOffset == o.mOffset && mSize == o.mSize));
    }
}

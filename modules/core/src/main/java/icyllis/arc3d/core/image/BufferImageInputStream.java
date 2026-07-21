/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.core.image;

import org.jspecify.annotations.NonNull;

import javax.imageio.stream.ImageInputStreamImpl;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Using an entire {@link ByteBuffer} as input.
 * <p>
 * Like the base class, this does not provide thread safety guarantees.
 */
public class BufferImageInputStream extends ImageInputStreamImpl {

    public final ByteBuffer buffer;

    public BufferImageInputStream(@NonNull ByteBuffer buffer) {
        this.buffer = buffer;
        streamPos = buffer.position();
    }

    @Override
    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        try {
            return buffer.get() & 0xFF;
        } catch (BufferUnderflowException e) {
            return -1;
        }
    }

    @Override
    public int read(byte[] bs, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bs.length);
        checkClosed();
        bitOffset = 0;
        if (len == 0) {
            return 0;
        }
        if (!buffer.hasRemaining()) {
            return -1;
        }
        int n = Math.min(buffer.remaining(), len);
        assert n > 0;
        buffer.get(bs, off, n);
        streamPos += n;
        return n;
    }

    @Override
    public long length() {
        try {
            checkClosed();
            return buffer.limit();
        } catch (IOException e) {
            return -1L;
        }
    }

    @Override
    public void seek(long pos) throws IOException {
        super.seek(pos);
        buffer.position((int) pos);
        streamPos = buffer.position();
    }
}

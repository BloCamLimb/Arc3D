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

package icyllis.arc3d.image.j2d;

import org.jspecify.annotations.NonNull;

import javax.imageio.stream.ImageOutputStreamImpl;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Just writes to {@link OutputStream}, no read or seek support.
 * <p>
 * This is okay because JDK's JPEG and GIF image writers don't use read/seek at all.
 * So MemoryCacheImageOutputStream is completely a waste of memory.
 */
public class PureImageOutputStream extends ImageOutputStreamImpl {

    public final OutputStream stream;

    public PureImageOutputStream(@NonNull OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void write(int b) throws IOException {
        checkClosed();
        stream.write(b);
        streamPos++;
    }

    @Override
    public void write(byte[] bs, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bs.length);
        checkClosed();
        if (len == 0) {
            return;
        }
        stream.write(bs, off, len);
        streamPos += len;
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Image stream is not readable");
    }

    @Override
    public int read(byte[] bs, int off, int len) throws IOException {
        throw new IOException("Image stream is not readable");
    }

    @Override
    public void seek(long pos) throws IOException {
        if (pos != streamPos) {
            throw new IOException("Image stream is not seekable");
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        stream.close();
    }
}

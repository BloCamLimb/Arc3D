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

package icyllis.arc3d.image;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

//PNG/JPEG/GIF/TIFF/Radiance/OpenEXR/KTX2/PNM/PAM/PFM
public abstract class Encoder implements AutoCloseable {

    // either
    protected OutputStream stream;
    protected WritableByteChannel channel;

    public static final int BUFFER_SIZE = 8192;

    protected ByteBuffer buffer;

    public void setOutput(OutputStream in) {
        stream = in;
        channel = null;
        if (buffer != null) {
            buffer.position(buffer.limit());
        }
    }

    public void setOutput(WritableByteChannel ch) {
        channel = ch;
        stream = null;
        if (buffer != null) {
            buffer.position(buffer.limit());
        }
    }

    public void setBuffer(ByteBuffer buf) {
        buffer = buf;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    protected void ensureWriteBuffer() {
        if (buffer == null) {
            if (channel instanceof FileChannel) {
                buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            } else {
                buffer = ByteBuffer.allocate(BUFFER_SIZE);
            }
            buffer.order(ByteOrder.nativeOrder());
        }
    }

    protected void writeByte(byte b) throws IOException {
        if (!buffer.hasRemaining())
            flushBuffer();
        buffer.put(b);
    }

    protected void flushBuffer() throws IOException {
        buffer.flip();
        if (stream != null) {
            int count = buffer.remaining();
            if (count > 0) {
                stream.write(buffer.array(), buffer.arrayOffset(), count);
            }
        } else if (channel != null) {
            while (buffer.hasRemaining()) {
                int n = channel.write(buffer);
                if (n <= 0) {
                    throw new IOException("No bytes written");
                }
            }
        } else {
            throw new IOException("No output");
        }
        buffer.clear();
    }

    /**
     * Releases any resources associated with this encoder instance.
     * <p>
     * This method MUST be invoked once the encoder is no longer required.
     * Any subsequent interactions with this instance after invocation will result
     * in undefined behavior.
     * <p>
     * This method only disposes of encoder-specific resources and will not close
     * the input source (e.g., stream or channel).
     */
    @Override
    public void close() {
    }
}

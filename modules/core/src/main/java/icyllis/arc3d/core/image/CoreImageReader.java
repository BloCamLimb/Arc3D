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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Base class for image decoders provided by Arc3D.
 */
//PNG/JPEG/GIF/PNM/PAM/PFM/OPENEXR/RADIANCE/TIFF
public abstract class CoreImageReader {

    // either
    protected InputStream stream;
    protected ReadableByteChannel channel;

    public static final int BUFFER_SIZE = 8192;

    protected byte[] buffer;
    protected int bufPos, bufEnd;

    public void setInput(InputStream in) {
        stream = in;
        channel = null;
        bufPos = bufEnd;
    }

    public void setInput(ReadableByteChannel ch) {
        channel = ch;
        stream = null;
        bufPos = bufEnd;
    }

    public void setBuffer(byte[] buf, int pos, int end) {
        buffer = buf;
        bufPos = pos;
        bufEnd = end;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getBufferPos() {
        return bufPos;
    }

    public int getBufferEnd() {
        return bufEnd;
    }

    /**
     * Return the next byte from the internal buffer, refilling from source as needed.
     */
    protected byte nextRawByte() throws IOException {
        if (bufPos < bufEnd) return buffer[bufPos++];
        // refill
        int n;
        if (stream != null) {
            n = stream.read(buffer, 0, buffer.length);
        } else if (channel != null) {
            ByteBuffer bb = ByteBuffer.wrap(buffer);
            n = channel.read(bb);
        } else {
            n = -1;
        }
        if (n < 0) throw new EOFException("Unexpected EOF in image data");
        // InputStream is always in blocking mode.
        // If a channel is used, then it should be switched to blocking mode.
        if (n == 0) throw new IOException("No bytes provided");
        bufPos = 0;
        bufEnd = n;
        return buffer[bufPos++];
    }

    /**
     * Push back the last consumed byte (only valid once between nextRawByte calls).
     */
    protected void unget() {
        bufPos--;
    }

    protected static boolean isWS(byte b) {
        // space SP, TAB, CR, LF, VT, FF — matches ANSI C isspace()
        return switch (b) {
            case ' ', '\t', '\r', '\n', '\u000b', '\f' -> true;
            default -> false;
        };
    }

    protected static String hex(byte b) {
        return Integer.toHexString(b & 0xFF);
    }
}

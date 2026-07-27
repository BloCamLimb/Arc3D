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

import icyllis.arc3d.core.ColorProfile;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.Pixmap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * Base class for image readers (and decoders) provided by Arc3D.
 * <p>
 * Most decoders support streaming, some may make use of seeking when available.
 * <p>
 * Decoder instance can be reused for decoding multiple inputs for performance.
 */
//PNG/JPEG/GIF/TIFF/Radiance/OpenEXR/KTX2/PNM/PAM/PFM
public abstract class Decoder implements AutoCloseable {

    // either
    protected InputStream stream;
    protected ReadableByteChannel channel;

    public static final int BUFFER_SIZE = 8192;

    protected ByteBuffer buffer;

    protected boolean readText = false;
    protected boolean readExif = false;
    protected boolean useBT1886 = false;

    public void setInput(InputStream in) {
        stream = in;
        channel = null;
        if (buffer != null) {
            buffer.position(buffer.limit());
        }
    }

    public void setInput(ReadableByteChannel ch) {
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

    public void reset() {
    }

    public boolean getReadText() {
        return readText;
    }

    public void setReadText(boolean readText) {
        this.readText = readText;
    }

    public boolean getReadExif() {
        return readExif;
    }

    public void setReadExif(boolean readExif) {
        this.readExif = readExif;
    }

    public boolean getUseBT1886() {
        return useBT1886;
    }

    /**
     * Whether to use BT.1886 EOTF to handle certain CICP transfers.
     * Otherwise, inverse of BT.709 OETF will be used.
     * <p>
     * This is recommended for display devices.
     */
    public void setUseBT1886(boolean useBT1886) {
        this.useBT1886 = useBT1886;
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract @Nullable ColorProfile getColorProfile();

    public abstract @Nullable ColorSpace getColorSpace();

    /**
     * @throws IOException I/O errors
     * @throws DecoderException decoding errors
     * @throws UnsupportedOperationException input is invalid
     */
    public abstract void readHeader() throws IOException;

    /**
     * Returns the best image info of the encoded image data.
     */
    public abstract @NonNull ImageInfo getInfo();

    /**
     * @param dstPixels destination
     * @throws IOException      I/O errors
     * @throws DecoderException decoding errors
     */
    public abstract void decodeImage(@NonNull Pixmap dstPixels) throws IOException;

    public boolean isAnimated() {
        return false;
    }

    /**
     * Returns the total number of animation frames, excluding the still image.
     * <p>
     * If it's unknown (for GIF only), this returns -1. Anyway, the total number of frames will be
     * known after the first playback.
     * <p>
     * If the image is not animated, 0 is returned.
     */
    public int getFrameCount() {
        return 0;
    }

    /**
     * Returns the total number of plays of the animation.
     * This is not a repetition count (loop count), but rather it plus one.
     * <p>
     * A value of 0 means playing infinitely.
     */
    public int getPlayCount() {
        return 0;
    }

    public void rewindAnimation() throws IOException {
    }

    /**
     * @return whether it has next animation frame
     */
    public boolean decodeNextFrame(@Nullable Pixmap backupFrame, @NonNull Pixmap canvasFrame,
                                   @NonNull FrameInfo outInfo) throws IOException {
        return false;
    }

    protected void ensureReadBuffer() {
        if (buffer == null) {
            if (channel instanceof FileChannel) {
                buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            } else {
                buffer = ByteBuffer.allocate(BUFFER_SIZE);
            }
            buffer.limit(0);
            buffer.order(ByteOrder.nativeOrder());
        }
    }

    /**
     * Return the next byte from the internal buffer, refilling from source as needed.
     */
    protected byte nextRawByte() throws IOException {
        if (!buffer.hasRemaining())
            refill();
        return buffer.get();
    }

    protected void readFully(ByteBuffer dst) throws IOException {
        int avail = buffer != null ? buffer.remaining() : 0;
        if (avail > 0) {
            int copy = Math.min(avail, dst.remaining());
            int dstPos = dst.position();
            int srcPos = buffer.position();
            dst.put(dstPos, buffer, srcPos, copy);
            dst.position(dstPos + copy);
            buffer.position(srcPos + copy);
        }
        if (stream != null) {
            assert buffer != null && buffer.hasArray();
            while (dst.hasRemaining()) {
                int n;
                if (dst.hasArray()) {
                    // directly read into the array
                    int request = Math.min(dst.remaining(), BUFFER_SIZE);
                    n = stream.read(dst.array(), dst.arrayOffset() + dst.position(), request);
                    if (n < 0)
                        break;
                    dst.position(dst.position() + n);
                } else {
                    byte[] buffer = this.buffer.array();
                    int request = Math.min(dst.remaining(), buffer.length);
                    n = stream.read(buffer, 0, request);
                    if (n < 0)
                        break;
                    dst.put(buffer, 0, n);
                }
            }
        } else if (channel != null) {
            while (dst.hasRemaining()) {
                int n = channel.read(dst);
                if (n < 0)
                    break;
            }
        }
        if (dst.hasRemaining())
            throw new EOFException("Insufficient bytes provided: " + dst.remaining() + " bytes more are needed");
    }

    protected void skip(long n) throws IOException {
        if (n <= 0) return;
        if (buffer != null && buffer.hasRemaining()) {
            int count = (int) Math.min(n, buffer.remaining());
            buffer.position(buffer.position() + count);
            n -= count;
        }
        if (n > 0) {
            if (stream != null) {
                stream.skipNBytes(n);
                return;
            } else if (channel != null) {
                if (channel instanceof SeekableByteChannel seekable) {
                    seekable.position(seekable.position() + n);
                    return;
                }
            }
        }
        while (n > 0) {
            refill();
            if (buffer.hasRemaining()) {
                int count = (int) Math.min(n, buffer.remaining());
                buffer.position(buffer.position() + count);
                n -= count;
            }
        }
    }

    protected void refill() throws IOException {
        int n;
        if (stream != null) {
            byte[] buffer = this.buffer.array();
            n = stream.read(buffer, 0, buffer.length);
        } else if (channel != null) {
            n = channel.read(buffer.clear());
        } else {
            n = -1;
        }
        if (n < 0) throw new EOFException("Unexpected EOF in image data");
        // InputStream is always in blocking mode.
        // If a channel is used, then it should be switched to blocking mode.
        if (n == 0) throw new IOException("No bytes provided");
        buffer.position(0);
        buffer.limit(n);
    }

    /**
     * Push back the last consumed byte (only valid once between nextRawByte calls).
     */
    protected void unget() {
        buffer.position(buffer.position() - 1);
    }

    /**
     * Releases any resources associated with this decoder instance.
     * <p>
     * This method MUST be invoked once the decoder is no longer required.
     * Any subsequent interactions with this instance after invocation will result
     * in undefined behavior.
     * <p>
     * This method only disposes of decoder-specific resources and will not close
     * the input source (e.g., stream or channel).
     */
    @Override
    public void close() {
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

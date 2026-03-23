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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * Similar to {@link ChannelImageOutputStream}, but read-only.
 * <p>
 * This class does not provide additional buffering; performance and the ability to
 * decode depend entirely on the implementation of SeekableByteChannel.
 * <p>
 * Like the base class, this does not provide thread safety guarantees.
 * <p>
 * No SPI is registered for this, used directly.
 */
public class ChannelImageInputStream extends ImageInputStreamImpl {

    protected final SeekableByteChannel channel;

    private ByteBuffer bb = null;
    private byte[] bs = null;
    private byte[] b1 = null;

    /**
     * Will close the channel when this is closed. No concurrency.
     */
    public ChannelImageInputStream(@NonNull SeekableByteChannel channel) {
        this.channel = channel;
        try {
            streamPos = channel.position();
        } catch (IOException ignored) {
        }
    }

    @Override
    public int read() throws IOException {
        if (b1 == null)
            b1 = new byte[1];
        int n = this.read(b1);
        if (n == 1)
            return b1[0] & 0xff;
        return -1;
    }

    @Override
    public int read(byte[] bs, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bs.length);
        checkClosed();
        bitOffset = 0;
        if (len == 0) {
            return 0;
        }

        ByteBuffer bb = ((this.bs == bs)
                ? this.bb
                : ByteBuffer.wrap(bs));
        bb.limit(Math.min(off + len, bb.capacity()));
        bb.position(off);
        this.bb = bb;
        this.bs = bs;
        int n = channel.read(bb);
        if (n != -1) {
            streamPos += n;
        }
        return n;
    }

    @Override
    public long length() {
        try {
            checkClosed();
            return channel.size();
        } catch (IOException e) {
            return -1L;
        }
    }

    @Override
    public void seek(long pos) throws IOException {
        super.seek(pos);
        channel.position(pos);
        streamPos = channel.position();
    }

    @Override
    public void close() throws IOException {
        super.close();
        channel.close();
    }
}

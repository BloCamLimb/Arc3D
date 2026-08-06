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

package icyllis.arc3d.core.compress;

import icyllis.arc3d.core.NotThreadSafe;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.zstd.ZSTDInBuffer;
import org.lwjgl.util.zstd.ZSTDOutBuffer;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.zip.DataFormatException;

import static org.lwjgl.util.zstd.Zstd.*;

/**
 * Zstandard decompressor.
 * <p>
 * Check {@link icyllis.arc3d.core.FeatureFlags#ZSTD} first before calling.
 * Input must be direct buffer.
 */
@NotThreadSafe
public class ZstdDecompressor implements Decompressor {

    private long dstream;

    private ByteBuffer input;

    private boolean finished;

    private long bytesRead;
    private long bytesWritten;

    public ZstdDecompressor() {
        dstream = ZSTD_createDStream();
        if (dstream == MemoryUtil.NULL) {
            throw new OutOfMemoryError();
        }
        checkError(ZSTD_DCtx_reset(dstream, ZSTD_reset_session_only));
    }

    @Override
    public void setInput(ByteBuffer input) {
        ensureOpen();
        if (!input.isDirect()) {
            throw new IllegalArgumentException("input must be direct buffer");
        }
        this.input = input;
    }

    @Override
    public void setDictionary(ByteBuffer dictionary) {
        ensureOpen();
        if (dictionary.isDirect()) {
            checkError(ZSTD_DCtx_loadDictionary(dstream, dictionary));
            dictionary.position(dictionary.position() + dictionary.remaining());
        } else {
            ByteBuffer buf = MemoryUtil.memAlloc(dictionary.remaining());
            try {
                buf.put(dictionary).flip();
                checkError(ZSTD_DCtx_loadDictionary(dstream, buf));
            } finally {
                MemoryUtil.memFree(buf);
            }
        }
    }

    @Override
    public boolean needsInput() {
        return input == null || !input.hasRemaining();
    }

    @Override
    public boolean needsDictionary() {
        return false;
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public int decompress(ByteBuffer output) throws DataFormatException {
        if (output.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        ensureOpen();

        if (input == null || !input.hasRemaining() || !output.hasRemaining()) {
            return 0;
        }

        int totalProduced = 0;

        if (output.isDirect()) {
            while (output.hasRemaining() && input.hasRemaining()) {
                int inputRemainingBefore = input.remaining();

                int produced = decompressDirect(output);
                totalProduced += produced;

                if (produced == 0 && input.remaining() == inputRemainingBefore) {
                    break;
                }
            }
        } else {
            try (var stack = MemoryStack.stackPush()) {
                // stack allocated buffer, lwjgl default stack is 64KB, we use 8KB
                int tempCapacity = Math.min(output.remaining(), 8192);
                ByteBuffer temp = stack.malloc(tempCapacity);

                while (output.hasRemaining() && input.hasRemaining()) {
                    int inputRemainingBefore = input.remaining();

                    temp.position(0);
                    temp.limit(Math.min(tempCapacity, output.remaining()));

                    int produced = decompressDirect(temp);

                    temp.flip();
                    output.put(temp);
                    totalProduced += produced;

                    if (produced == 0 && input.remaining() == inputRemainingBefore) {
                        break;
                    }
                }
            }
        }

        return totalProduced;
    }

    private int decompressDirect(ByteBuffer output) throws DataFormatException {
        if (input == null || !input.hasRemaining() || !output.hasRemaining()) {
            return 0;
        }

        int produced;
        try (var stack = MemoryStack.stackPush()) {
            ZSTDInBuffer in = ZSTDInBuffer.malloc(stack);
            in.src(input);
            in.pos(0);

            ZSTDOutBuffer out = ZSTDOutBuffer.malloc(stack);
            out.dst(output);
            out.pos(0);

            long code = ZSTD_decompressStream(dstream, out, in);

            int consumed = (int) in.pos();
            produced = (int) out.pos();

            input.position(input.position() + consumed);
            output.position(output.position() + produced);

            bytesRead += consumed;
            bytesWritten += produced;

            if (ZSTD_isError(code)) {
                throw new DataFormatException(
                        "zstd error: " + ZSTD_getErrorName(code) + " (code=" + code + ")");
            }

            finished = (code == 0);
        }
        return produced;
    }

    @Override
    public long getBytesRead() {
        return bytesRead;
    }

    @Override
    public long getBytesWritten() {
        return bytesWritten;
    }

    @Override
    public void reset() {
        ensureOpen();
        checkError(ZSTD_DCtx_reset(dstream, ZSTD_reset_session_only));
        checkError(ZSTD_DCtx_refDDict(dstream, MemoryUtil.NULL));
        input = null;
        finished = false;
        bytesRead = 0;
        bytesWritten = 0;
    }

    @Override
    public void close() {
        if (dstream != MemoryUtil.NULL) {
            ZSTD_freeDStream(dstream);
            dstream = MemoryUtil.NULL;
        }
    }

    private void ensureOpen() {
        if (dstream == MemoryUtil.NULL) {
            throw new IllegalStateException("ZstdDecompressor has been closed");
        }
    }

    private static void checkError(long code) {
        if (ZSTD_isError(code)) {
            throw new IllegalStateException("zstd error: " + ZSTD_getErrorName(code));
        }
    }
}

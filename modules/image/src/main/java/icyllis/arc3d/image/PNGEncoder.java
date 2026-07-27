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

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.core.Pixmap;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static icyllis.arc3d.image.PNG.*;

public class PNGEncoder extends Encoder {

    private static final Predictor FILTER = Predictor.createInstance();

    public static final int
            FILTER_STRATEGY_MIN_SUM = 0,
            FILTER_STRATEGY_MIN_ENTROPY = 1,
            FILTER_STRATEGY_BIGRAM_ENTROPY = 2; // extremely slow

    private PNGMetadata metadata;

    private CRC32 crc;
    private Deflater deflater;

    private int chunkStart;
    private int chunkLength;

    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
    private int compressionStrategy = Deflater.DEFAULT_STRATEGY;

    private static final int ALL_FILTERS
            = (1 << FILTER_TYPE_NONE) |
              (1 << FILTER_TYPE_SUB) |
              (1 << FILTER_TYPE_UP) |
              (1 << FILTER_TYPE_AVERAGE) |
              (1 << FILTER_TYPE_PAETH);
    private int filterChoice = ALL_FILTERS;

    private int filterStrategy = FILTER_STRATEGY_MIN_ENTROPY;

    public PNGEncoder() {
    }

    public void setMetadata(PNGMetadata metadata) {
        this.metadata = metadata;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void setCompressionStrategy(int compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }

    public void setFilterChoice(int filterChoice) {
        if (filterChoice == 0) {
            throw new IllegalArgumentException();
        }
        this.filterChoice = filterChoice & ALL_FILTERS;
    }

    public void setFilterStrategy(int filterStrategy) {
        this.filterStrategy = filterStrategy;
    }

    public void writeChunks() throws IOException {

        ensureWriteBuffer();

        for (byte b : FILE_SIGNATURE) {
            writeByte(b);
        }

        startChunk(13, IHDR_TYPE);

        writeInt(metadata.IHDR_width);
        writeInt(metadata.IHDR_height);

        writeByte((byte) metadata.IHDR_bitDepth);
        writeByte((byte) metadata.IHDR_colorType);
        writeByte((byte) metadata.IHDR_compressionMethod);
        writeByte((byte) metadata.IHDR_filterMethod);
        writeByte((byte) metadata.IHDR_interlaceMethod);

        endChunk();


        if (metadata.any(PNGMetadata.CHUNK_cICP)) {
            startChunk(4, cICP_TYPE);

            writeByte((byte) metadata.cICP_colorPrimaries);
            writeByte((byte) metadata.cICP_transferCharacteristics);
            writeByte((byte) metadata.cICP_matrixCoefficients);
            writeByte((byte) metadata.cICP_videoFullRangeFlag);

            endChunk();
        }

        if (metadata.any(PNGMetadata.CHUNK_sRGB)) {
            startChunk(1, sRGB_TYPE);

            writeByte((byte) metadata.sRGB_renderingIntent);

            endChunk();
        }

        if (metadata.any(PNGMetadata.CHUNK_PLTE)) {
            startChunk(metadata.PLTE_entries.length, PLTE_TYPE);

            buffer.put(metadata.PLTE_entries);

            endChunk();
        }
    }

    public void encodeImage(Pixmap srcPixels) throws IOException {

        if (deflater == null) {
            deflater = new Deflater();
        } else {
            deflater.reset();
        }
        deflater.setLevel(compressionLevel);
        deflater.setStrategy(compressionStrategy);

        int bitDepth = metadata.IHDR_bitDepth;

        int width = metadata.IHDR_width;
        int height = metadata.IHDR_height;

        Object srcBase = srcPixels.getBase();

        boolean is16 = bitDepth == 16;
        int bytesPerPixel = metadata.numChannels() << (is16 ? 1 : 0);

        int fixedFilter = Integer.bitCount(filterChoice) == 1
                ? MathUtil.ceilLog2(filterChoice) : -1;

        // reserve 64 bytes for the filter type (1 byte) of next row,
        // and tail padding for vector instructions
        int rowBytes = computeRowBytes(width, Predictor.HEADROOM);
        // allocate heap buffer (BIG ENDIAN)
        ByteBuffer currScanlineBuf = ByteBuffer.allocate(rowBytes + Predictor.HEADROOM);
        ByteBuffer prevScanlineBuf = ByteBuffer.allocate(rowBytes + Predictor.HEADROOM);
        ByteBuffer filtScanlineBuf = fixedFilter != 0 ? ByteBuffer.allocate(rowBytes + Predictor.HEADROOM) : null;

        int passRowBytes = computeRowBytes(width, 0);

        startIDAT();

        for (int i = 0; i < height; i++) {
            currScanlineBuf.clear();

            long srcAddr = srcPixels.getAddress(0, i);
            if (srcBase == null) {
                var srcBuf = MemoryUtil.memByteBuffer(srcAddr, passRowBytes);
                if (is16) {
                    // copySwapMemory
                    currScanlineBuf.asShortBuffer().put(srcBuf.asShortBuffer());
                } else {
                    currScanlineBuf.put(srcBuf);
                }
            } else {
                if (is16) {
                    // copySwapMemory
                    currScanlineBuf.asShortBuffer().put(
                            ShortBuffer.wrap((short[]) srcBase, (int) (srcAddr >> 1), (passRowBytes >> 1))
                    );
                } else {
                    PixelUtils.mixedMemCopy(srcBase, srcAddr,
                            currScanlineBuf.array(), 0, passRowBytes);
                }
            }
            currScanlineBuf.position(0).limit(passRowBytes);

            ByteBuffer resultScanline;

            int bestFilter = FILTER_TYPE_NONE;
            if (fixedFilter == FILTER_TYPE_NONE) {
                resultScanline = currScanlineBuf;
            } else if (fixedFilter >= 0) {
                switch (fixedFilter) {
                    case FILTER_TYPE_SUB -> FILTER.encodeSub(currScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                    case FILTER_TYPE_UP -> Predictor.encodeUp(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes);
                    case FILTER_TYPE_AVERAGE -> FILTER.encodeAverage(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                    case FILTER_TYPE_PAETH -> FILTER.encodePaeth(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                }
                bestFilter = fixedFilter;
                resultScanline = filtScanlineBuf;
            } else {
                long bestScore = Long.MAX_VALUE;
                int lastFilter = FILTER_TYPE_NONE;

                for (int filter = FILTER_TYPE_NONE; filter <= FILTER_TYPE_PAETH; filter++) {
                    if ((filterChoice & (1 << filter)) == 0) {
                        continue;
                    }

                    switch (filter) {
                        case FILTER_TYPE_SUB -> FILTER.encodeSub(currScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        case FILTER_TYPE_UP -> Predictor.encodeUp(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes);
                        case FILTER_TYPE_AVERAGE -> FILTER.encodeAverage(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        case FILTER_TYPE_PAETH -> FILTER.encodePaeth(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                    }

                    lastFilter = filter;

                    byte[] arr = filter == FILTER_TYPE_NONE ? currScanlineBuf.array() : filtScanlineBuf.array();

                    long score;
                    if (filterStrategy == FILTER_STRATEGY_MIN_SUM) {
                        score = FILTER.sumOfAbs(arr, passRowBytes);
                    } else if (filterStrategy == FILTER_STRATEGY_MIN_ENTROPY) {
                        int[] counts = new int[256];
                        for (int ii = 0; ii < passRowBytes; ii++) {
                            counts[arr[ii] & 0xFF]++;
                        }
                        score = Long.MAX_VALUE / 2;
                        for (var x : counts) {
                            if (x != 0) {
                                score -= ilog2i(x);
                            }
                        }
                    } else {
                        int[] counts = new int[65536];
                        for (int ii = 1; ii < arr.length; ii++) {
                            int bigram = (((arr[ii-1] & 0xFF) << 8) | (arr[ii] & 0xFF));
                            counts[bigram]++;
                        }
                        score = Long.MAX_VALUE / 2;
                        for (var x : counts) {
                            if (x != 0) {
                                score -= ilog2i(x);
                            }
                        }
                    }
                    if (score <= bestScore) {
                        bestScore = score;
                        bestFilter = filter;

                        if (score == 0) {
                            break;
                        }
                    }
                }

                if (bestFilter != lastFilter) {
                    switch (bestFilter) {
                        case FILTER_TYPE_SUB -> FILTER.encodeSub(currScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        case FILTER_TYPE_UP -> Predictor.encodeUp(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes);
                        case FILTER_TYPE_AVERAGE -> FILTER.encodeAverage(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        case FILTER_TYPE_PAETH -> FILTER.encodePaeth(currScanlineBuf.array(), prevScanlineBuf.array(), filtScanlineBuf.array(), passRowBytes, bytesPerPixel);
                    }
                }

                resultScanline = bestFilter == FILTER_TYPE_NONE ? currScanlineBuf : filtScanlineBuf;
            }

            resultScanline.position(passRowBytes).limit(passRowBytes + 1)
                    .put(passRowBytes, (byte) bestFilter);
            writeScanlineBytes(resultScanline, false);
            resultScanline.position(0).limit(passRowBytes);
            writeScanlineBytes(resultScanline, false);

            ByteBuffer tBuf = prevScanlineBuf;
            prevScanlineBuf = currScanlineBuf;
            currScanlineBuf = tBuf;
        }

        writeScanlineBytes(null, true);

        startChunk(0, IEND_TYPE);
        endChunk();

        flushBuffer();
    }

    private static long ilog2i(int i) {
        long log = MathUtil.floorLog2(i);
        return i * log + ((i - (1L << log)) << 1L);
    }

    private void startIDAT() throws IOException {
        flushBuffer();

        // length is unknown until finished
        writeInt(0);
        chunkStart = buffer.position();
        writeInt(IDAT_TYPE);
    }

    private void endIDAT() throws IOException {
        if (crc == null) {
            crc = new CRC32();
        } else {
            crc.reset();
        }

        // + chunkType
        int chunkLength = buffer.position() - 8;

        crc.update(buffer.slice(chunkStart, buffer.position() - chunkStart));

        ByteOrder prevOrder = buffer.order();
        buffer
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0, chunkLength)
                .order(prevOrder);

        writeInt((int) crc.getValue());
    }

    public int computeRowBytes(int width, int reserve) throws IOException {
        return metadata.computeRowBytes(width, reserve, EncoderException::new);
    }

    private void writeScanlineBytes(ByteBuffer src, boolean finish) throws IOException {
        Deflater def = deflater;
        if (finish) {
            def.finish();
        } else {
            def.setInput(src);
        }
        do {
            if (buffer.remaining() <= 4) {
                endIDAT();
                startIDAT();
            }
            int n = def.deflate(buffer.slice(buffer.position(), buffer.remaining() - 4),
                    Deflater.NO_FLUSH);
            buffer.position(buffer.position() + n);
        } while (finish ? !def.finished() : !def.needsInput());
        if (finish) {
            endIDAT();
        }
    }

    // write chunk with fixed size
    private void startChunk(int chunkLength, int chunkType) throws IOException {
        int chunkSize = chunkLength + 12;
        if (buffer.remaining() < chunkSize) {
            flushBuffer();
        }
        if (buffer.remaining() < chunkSize) {
            // this is impossible because default buffer size is 8KB,
            // unless it's specified by user
            throw new EncoderException("Write buffer is too small");
        }

        writeInt(chunkLength);
        chunkStart = buffer.position();
        writeInt(chunkType);
        this.chunkLength = chunkLength;
    }

    private void endChunk() throws IOException {
        if (crc == null) {
            crc = new CRC32();
        } else {
            crc.reset();
        }

        // + chunkType
        int expectedBytes = chunkLength + 4;
        if (buffer.position() - chunkStart != expectedBytes) {
            throw new IllegalStateException();
        }

        crc.update(buffer.slice(chunkStart, expectedBytes));

        writeInt((int) crc.getValue());
    }

    @Override
    public void close() {
        if (deflater != null) {
            deflater.end();
            deflater = null;
        }
    }

    private void writeInt(int v) throws IOException {
        writeByte((byte) (v >>> 24));
        writeByte((byte) ((v >> 16) & 0xFF));
        writeByte((byte) ((v >> 8) & 0xFF));
        writeByte((byte) (v & 0xFF));
    }
}

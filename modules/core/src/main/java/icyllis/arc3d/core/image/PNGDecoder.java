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
import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.core.Pixmap;
import icyllis.arc3d.image.PNGFilter;
import org.lwjgl.system.MemoryUtil;

import static icyllis.arc3d.core.image.PNGHelpers.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * <a href="https://www.w3.org/TR/2025/REC-png-3-20250624/">Portable Network Graphics (PNG) Specification (Third Edition)</a>
 *
 * <ul>
 *     <li>CRCs are not checked.</li>
 * </ul>
 *
 */
public class PNGDecoder extends CoreImageReader {

    private static final PNGFilter FILTER = PNGFilter.createInstance();

    private PNGMetadata metadata;

    private boolean readText = false;
    private boolean readHistogram = false;
    private boolean readSuggestedPalette = false;
    private boolean readExif = false;

    private static final int STAGE_TOP = 1;
    private static final int STAGE_IHDR = 2;
    private static final int STAGE_FIRST_IDAT = 3;
    private static final int STAGE_AFTER_IDAT = 4;
    private static final int STAGE_IEND = 5;
    private int stage = 0;

    private int chunkType;
    private int chunkLength;
    private int chunkRemaining;

    private Inflater inflater;

    public PNGDecoder() {
    }

    public PNGMetadata getMetadata() {
        return metadata;
    }

    public boolean isReadText() {
        return readText;
    }

    public void setReadText(boolean readText) {
        this.readText = readText;
    }

    public boolean isReadHistogram() {
        return readHistogram;
    }

    public void setReadHistogram(boolean readHistogram) {
        this.readHistogram = readHistogram;
    }

    public boolean isReadSuggestedPalette() {
        return readSuggestedPalette;
    }

    public void setReadSuggestedPalette(boolean readSuggestedPalette) {
        this.readSuggestedPalette = readSuggestedPalette;
    }

    public boolean isReadExif() {
        return readExif;
    }

    public void setReadExif(boolean readExif) {
        this.readExif = readExif;
    }

    public void readHeader() throws IOException {
        ensureReadBuffer();

        metadata = new PNGMetadata();
        stage = 0;

        if (nextRawByte() != (byte)137 ||
                nextRawByte() != (byte)80 ||
                nextRawByte() != (byte)78 ||
                nextRawByte() != (byte)71 ||
                nextRawByte() != (byte)13 ||
                nextRawByte() != (byte)10 ||
                nextRawByte() != (byte)26 ||
                nextRawByte() != (byte)10) {
            throw new DecoderException("Not a PNG image");
        }

        stage = STAGE_TOP;

        int IHDR_length = readInt();
        if (IHDR_length != 13) {
            throw new DecoderException("Bad length for IHDR chunk!");
        }
        int IHDR_type = readInt();
        if (IHDR_type != IHDR_TYPE) {
            throw new DecoderException("Bad type for IHDR chunk!");
        }

        int width = readInt();
        int height = readInt();

        int bitDepth          = nextRawByte() & 0xff;
        int colorType         = nextRawByte() & 0xff;
        int compressionMethod = nextRawByte() & 0xff;
        int filterMethod      = nextRawByte() & 0xff;
        int interlaceMethod   = nextRawByte() & 0xff;

        metadata.IHDR = true;
        metadata.IHDR_width = width;
        metadata.IHDR_height = height;
        metadata.IHDR_bitDepth = bitDepth;
        metadata.IHDR_colorType = colorType;
        metadata.IHDR_compressionMethod = compressionMethod;
        metadata.IHDR_filterMethod = filterMethod;
        metadata.IHDR_interlaceMethod = interlaceMethod;

        metadata.checkIHDR(DecoderException::new);

        stage = STAGE_IHDR;
    }

    public void readChunks() throws IOException {
        if (stage < STAGE_IHDR) {
            throw new DecoderException("No IHDR chunk");
        }
        if (stage >= STAGE_IEND) {
            return;
        }

        for (;;) {
            readChunkHeader();

            System.out.println(Integer.toHexString(chunkType));

            if (chunkType == IDAT_TYPE) {
                if (metadata.IHDR_colorType == COLOR_TYPE_PALETTE && !metadata.PLTE) {
                    throw new DecoderException("Required PLTE chunk missing");
                }

                if (stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("Unexpected IDAT chunk");
                }

                stage = STAGE_FIRST_IDAT;
                return;
            }

            if (isCriticalChunk(chunkType)) {
                if (chunkType == PLTE_TYPE) {
                    if (metadata.bKGD || metadata.hIST || metadata.tRNS || stage >= STAGE_FIRST_IDAT) {
                        throw new DecoderException("PLTE must appear before bKGD, hIST, tRNS, IDAT");
                    }

                    if (metadata.PLTE) {
                        throw new DecoderException("Duplicate PLTE chunk");
                    }

                    switch (metadata.IHDR_colorType) {
                        case COLOR_TYPE_PALETTE:
                        case COLOR_TYPE_RGB:
                        case COLOR_TYPE_RGB_ALPHA:
                            break;
                        default:
                            throw new DecoderException("Gray or gray alpha image cannot have a PLTE chunk");
                    }

                    if (chunkLength % 3 != 0) {
                        throw new DecoderException("Invalid PLTE chunk length");
                    }

                    int numEntries = chunkLength / 3;

                    metadata.checkPLTE(numEntries, DecoderException::new);

                    byte[] entries = new byte[chunkLength];
                    readFully(ByteBuffer.wrap(entries));

                    metadata.PLTE = true;
                    metadata.PLTE_entries = entries;

                } else if (chunkType == IEND_TYPE) {
                    if (stage < STAGE_AFTER_IDAT) {
                        throw new DecoderException("Got IEND without IDAT");
                    }
                    if (chunkLength != 0) {
                        throw new DecoderException("Bad IEND");
                    }
                    readCRC();
                    stage = STAGE_IEND;
                    return;
                } else {
                    throw new DecoderException("Invalid critical chunk: " + Integer.toHexString(chunkType));
                }

            } else if (chunkType == tRNS_TYPE) {
                if (stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("tRNS must appear before IDAT");
                }
                if (metadata.tRNS) {
                    throw new DecoderException("Duplicate tRNS");
                }

                //TODO
                skip(chunkLength);

            } else if (chunkType == cHRM_TYPE) {
                if (metadata.PLTE || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("cHRM must appear before PLTE and IDAT");
                }
                if (metadata.cHRM) {
                    throw new DecoderException("Duplicate cHRM");
                }
                if (chunkLength != 32) {
                    throw new DecoderException("Invalid cHRM");
                }

                metadata.cHRM = true;
                metadata.cHRM_whitePointX = readInt();
                metadata.cHRM_whitePointY = readInt();
                metadata.cHRM_redX = readInt();
                metadata.cHRM_redY = readInt();
                metadata.cHRM_greenX = readInt();
                metadata.cHRM_greenY = readInt();
                metadata.cHRM_blueX = readInt();
                metadata.cHRM_blueY = readInt();

            } else if (chunkType == gAMA_TYPE) {
                if (metadata.PLTE || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("gAMA must appear before PLTE and IDAT");
                }
                if (metadata.gAMA) {
                    throw new DecoderException("Duplicate gAMA");
                }
                if (chunkLength != 4) {
                    throw new DecoderException("Invalid gAMA");
                }

                metadata.gAMA = true;
                metadata.gAMA_gamma = readInt();

            } else {
                skip(chunkLength);
            }

            chunkRemaining = 0;
        }
    }

    private void readCRC() throws IOException {
        readInt();
    }

    // Read the current chunk's crc and the next chunk header
    private void readChunkHeader() throws IOException {
        readCRC();

        chunkLength = readInt();
        chunkType = readInt();

        if (chunkLength < 0) {
            throw new DecoderException("Invalid chunk length: " + chunkLength);
        }

        chunkRemaining = chunkLength;
    }

    public void decodeImage(Pixmap dst) throws IOException {
        if (chunkType != IDAT_TYPE || stage != STAGE_FIRST_IDAT) {
            throw new DecoderException("Not IDAT");
        }

        if (inflater == null) {
            inflater = new Inflater();
        } else {
            inflater.reset();
        }

        int width = metadata.IHDR_width;
        int height = metadata.IHDR_height;

        boolean is16 = metadata.IHDR_bitDepth == 16;
        int bytesPerPixel = metadata.numChannels() << (is16 ? 1 : 0);
        // reserve 32 bytes for the filter type (1 byte) of next row,
        // and tail padding for vector instructions
        int rowBytes = computeRowBytes(width, 32);
        // allocate heap buffer
        ByteBuffer currScanlineBuf = ByteBuffer.allocate(rowBytes + 32);
        ByteBuffer prevScanlineBuf = ByteBuffer.allocate(rowBytes + 32);

        Object dstBase = dst.getBase();

        // read the filter of first scanline
        readScanlineBytes(currScanlineBuf.limit(1));
        int filter = currScanlineBuf.get(0) & 0xFF;

        for (int i = 0; i < height; i++) {
            // we read current row and next row filter
            boolean lastRow = i == height - 1;
            currScanlineBuf
                    .position(0)
                    .limit(lastRow ? rowBytes : rowBytes + 1);
            readScanlineBytes(currScanlineBuf);
            currScanlineBuf.flip();

            int nextFilter = filter;
            if (!lastRow) {
                nextFilter = currScanlineBuf.get(rowBytes) & 0xFF;
                currScanlineBuf.limit(rowBytes);
            }

            switch (filter) {
                case FILTER_NONE:
                    break;
                case FILTER_SUB:
                    switch (bytesPerPixel) {
                        case 1 -> FILTER.decodeSub1(currScanlineBuf.array(), rowBytes);
                        case 2 -> FILTER.decodeSub2(currScanlineBuf.array(), rowBytes);
                        case 3 -> FILTER.decodeSub3(currScanlineBuf.array(), rowBytes);
                        case 4 -> FILTER.decodeSub4(currScanlineBuf.array(), rowBytes);
                        case 6 -> FILTER.decodeSub6(currScanlineBuf.array(), rowBytes);
                        case 8 -> FILTER.decodeSub8(currScanlineBuf.array(), rowBytes);
                    }
                    break;
                case FILTER_UP:
                    PNGFilter.decodeUp(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                    break;
                case FILTER_AVERAGE:
                    switch (bytesPerPixel) {
                        case 3 -> FILTER.decodeAverage3(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 4 -> FILTER.decodeAverage4(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 6 -> FILTER.decodeAverage6(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 8 -> FILTER.decodeAverage8(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        default -> PNGFilter.decodeAverage(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes, bytesPerPixel);
                    }
                    break;
                case FILTER_PAETH:
                    switch (bytesPerPixel) {
                        case 3 -> FILTER.decodePaeth3(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 4 -> FILTER.decodePaeth4(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 6 -> FILTER.decodePaeth6(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        case 8 -> FILTER.decodePaeth8(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes);
                        default -> PNGFilter.decodePaeth(currScanlineBuf.array(), prevScanlineBuf.array(), rowBytes, bytesPerPixel);
                    }
                    break;
                default:
                    throw new DecoderException("Unknown filter type: " + filter);
            }

            //TODO expanding, pack bits...
            long dstAddr = dst.getAddress(0, i);
            if (dstBase == null) {
                var dstBuf = MemoryUtil.memByteBuffer(dstAddr, rowBytes);
                if (is16) {
                    // copySwapMemory
                    dstBuf.asShortBuffer().put(currScanlineBuf.asShortBuffer());
                } else {
                    dstBuf.put(currScanlineBuf);
                }
            } else {
                if (is16) {
                    // copySwapMemory
                    ShortBuffer.wrap((short[]) dstBase, (int) (dstAddr>>1), (rowBytes>>1))
                            .put(currScanlineBuf.asShortBuffer());
                } else {
                    PixelUtils.mixedMemCopy(currScanlineBuf.array(), 0,
                            dst.getBase(), dstAddr, rowBytes);
                }
            }

            filter = nextFilter;

            ByteBuffer tBuf = prevScanlineBuf;
            prevScanlineBuf = currScanlineBuf;
            currScanlineBuf = tBuf;
        }

        skip(chunkRemaining);
        stage = STAGE_AFTER_IDAT;

    }

    private int computeRowBytes(int width, int reserve) throws IOException {
        assert width > 0;
        long rowBytes = (long) metadata.numChannels() *
                metadata.IHDR_bitDepth * width;
        rowBytes = (rowBytes + 7) / 8;
        if (rowBytes <= 0 || rowBytes > Integer.MAX_VALUE - reserve - 8) {
            throw new DecoderException("Scanline is too big");
        }
        return (int) rowBytes;
    }

    private void readScanlineBytes(ByteBuffer dst) throws IOException {
        Inflater inf = inflater;
        do {
            if (inf.finished() || inf.needsDictionary()) {
                throw new DecoderException("Not enough ZLIB data");
            }
            if (inf.needsInput()) {
                while (chunkRemaining == 0) {
                    readChunkHeader();
                    if (chunkType != IDAT_TYPE) {
                        throw new DecoderException("Not enough IDAT chunk");
                    }
                }
                if (!buffer.hasRemaining()) {
                    refill();
                }
                int len = Math.min(buffer.remaining(), chunkRemaining);
                int bufPos = buffer.position();
                inf.setInput(buffer.slice(bufPos, len));
                buffer.position(bufPos + len);
                chunkRemaining -= len;
            }
            try {
                inf.inflate(dst);
            } catch (DataFormatException e) {
                throw new DecoderException("Invalid ZLIB data: " + e.getMessage());
            }
        } while (dst.hasRemaining());
    }

    public void close() {
        if (inflater != null) {
            inflater.end();
            inflater = null;
        }
    }

    private int readInt() throws IOException {
        byte b1 = nextRawByte();
        byte b2 = nextRawByte();
        byte b3 = nextRawByte();
        byte b4 = nextRawByte();
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) |
                ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    public static boolean isCriticalChunk(int chunkType) {
        return (chunkType & (1 << (5 + 24))) == 0;
    }
}

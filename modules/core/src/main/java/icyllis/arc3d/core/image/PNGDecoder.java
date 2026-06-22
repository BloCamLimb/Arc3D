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

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ContentLightLevelInformation;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.MasteringDisplayColorVolume;
import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.core.Pixmap;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.image.PNGFilter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static icyllis.arc3d.core.image.PNG.*;

/**
 * <a href="https://www.w3.org/TR/2025/REC-png-3-20250624/">Portable Network Graphics (PNG) Specification (Third Edition)</a>
 *
 * <ul>
 *     <li>CRCs are not checked.</li>
 * </ul>
 *
 */
public class PNGDecoder extends Decoder {

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

    public void reset() {
        metadata = new PNGMetadata();
        stage = 0;
    }

    @Override
    public int getWidth() {
        return metadata.IHDR_width;
    }

    @Override
    public int getHeight() {
        return metadata.IHDR_height;
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

        reset();

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
        int IHDR_type = readInt();
        if (IHDR_type != IHDR_TYPE) {
            throw new DecoderException("First chunk must be IHDR!");
        }
        if (IHDR_length != 13) {
            throw new DecoderException("Invalid IHDR chunk length");
        }

        int width = readInt();
        int height = readInt();

        int bitDepth          = nextRawByte() & 0xFF;
        int colorType         = nextRawByte() & 0xFF;
        int compressionMethod = nextRawByte() & 0xFF;
        int filterMethod      = nextRawByte() & 0xFF;
        int interlaceMethod   = nextRawByte() & 0xFF;

        metadata.IHDR_width = width;
        metadata.IHDR_height = height;
        metadata.IHDR_bitDepth = bitDepth;
        metadata.IHDR_colorType = colorType;
        metadata.IHDR_compressionMethod = compressionMethod;
        metadata.IHDR_filterMethod = filterMethod;
        metadata.IHDR_interlaceMethod = interlaceMethod;

        metadata.checkIHDR(DecoderException::new);
        metadata.set(PNGMetadata.CHUNK_IHDR);

        stage = STAGE_IHDR;
    }

    public ImageInfo getBestImageInfo() {
        @ColorInfo.ColorType
        int colorType = ColorInfo.CT_UNKNOWN;
        @ColorInfo.AlphaType
        int alphaType = ColorInfo.AT_UNKNOWN;

        int bitDepth = metadata.IHDR_bitDepth;
        boolean tRNS = metadata.any(PNGMetadata.CHUNK_tRNS);

        switch (metadata.IHDR_colorType) {
            case COLOR_TYPE_GRAYSCALE -> {
                if (bitDepth <= 8) {
                    if (tRNS) {
                        colorType = ColorInfo.CT_GRAY_ALPHA_88;
                        alphaType = ColorInfo.AT_UNPREMUL;
                    } else {
                        colorType = ColorInfo.CT_GRAY_8;
                        alphaType = ColorInfo.AT_OPAQUE;
                    }
                } else {
                    assert bitDepth == 16;
                    if (tRNS) {
                        colorType = ColorInfo.CT_GRAY_ALPHA_1616;
                        alphaType = ColorInfo.AT_UNPREMUL;
                    } else {
                        colorType = ColorInfo.CT_GRAY_16;
                        alphaType = ColorInfo.AT_OPAQUE;
                    }
                }
            }
            case COLOR_TYPE_RGB -> {
                if (bitDepth == 8) {
                    if (tRNS) {
                        colorType = ColorInfo.CT_RGBA_8888;
                        alphaType = ColorInfo.AT_UNPREMUL;
                    } else {
                        colorType = ColorInfo.CT_RGB_888;
                        alphaType = ColorInfo.AT_OPAQUE;
                    }
                } else {
                    assert bitDepth == 16;
                    if (tRNS) {
                        colorType = ColorInfo.CT_RGBA_16161616;
                        alphaType = ColorInfo.AT_UNPREMUL;
                    } else {
                        colorType = ColorInfo.CT_RGB_161616;
                        alphaType = ColorInfo.AT_OPAQUE;
                    }
                }
            }
            case COLOR_TYPE_PALETTE -> {
                if (tRNS) {
                    colorType = ColorInfo.CT_RGBA_8888;
                    alphaType = ColorInfo.AT_UNPREMUL;
                } else {
                    colorType = ColorInfo.CT_RGB_888;
                    alphaType = ColorInfo.AT_OPAQUE;
                }
            }
            case COLOR_TYPE_GRAY_ALPHA -> {
                if (bitDepth == 8) {
                    colorType = ColorInfo.CT_GRAY_ALPHA_88;
                } else {
                    assert bitDepth == 16;
                    colorType = ColorInfo.CT_GRAY_ALPHA_1616;
                }
                alphaType = ColorInfo.AT_UNPREMUL;
            }
            case COLOR_TYPE_RGB_ALPHA -> {
                if (bitDepth == 8) {
                    colorType = ColorInfo.CT_RGBA_8888;
                } else {
                    assert bitDepth == 16;
                    colorType = ColorInfo.CT_RGBA_16161616;
                }
                alphaType = ColorInfo.AT_UNPREMUL;
            }
        }

        //TODO packed formats, color space info
        ColorSpace colorSpace = ColorSpaces.SRGB;
        if (metadata.any(PNGMetadata.CHUNK_cICP)) {
            colorSpace = ColorSpaces.fromCICP(metadata.cICP_colorPrimaries, metadata.cICP_transferCharacteristics);
        }

        return ImageInfo.make(metadata.IHDR_width, metadata.IHDR_height,
                colorType, alphaType, colorSpace);
    }

    public void readChunks() throws IOException {
        if (stage < STAGE_IHDR) {
            throw new DecoderException("No IHDR chunk");
        }
        if (stage >= STAGE_IEND) {
            return;
        }

        while (stage < STAGE_FIRST_IDAT || stage >= STAGE_AFTER_IDAT) {
            if (chunkRemaining == 0) {
                readChunkHeader();
            } else if (chunkRemaining != chunkLength) {
                // internal error
                throw new DecoderException("Decoder at wrong position");
            }

            if (chunkType == IDAT_TYPE) {
                if (metadata.IHDR_colorType == COLOR_TYPE_PALETTE &&
                        !metadata.any(PNGMetadata.CHUNK_PLTE)) {
                    throw new DecoderException("PLTE is required before IDAT");
                }

                if (stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("Unexpected IDAT chunk");
                }

                stage = STAGE_FIRST_IDAT;
                return;
            }

            if (isCriticalChunk(chunkType)) {
                if (chunkType == PLTE_TYPE) {
                    if (metadata.any(PNGMetadata.CHUNK_bKGD | PNGMetadata.CHUNK_hIST | PNGMetadata.CHUNK_tRNS)
                            || stage >= STAGE_FIRST_IDAT) {
                        throw new DecoderException("PLTE must appear before bKGD, hIST, tRNS, IDAT");
                    }

                    if (metadata.any(PNGMetadata.CHUNK_PLTE)) {
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

                    metadata.set(PNGMetadata.CHUNK_PLTE);
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
                if (metadata.any(PNGMetadata.CHUNK_tRNS)) {
                    throw new DecoderException("Duplicate tRNS");
                }

                if (metadata.IHDR_colorType == COLOR_TYPE_GRAYSCALE) {
                    if (chunkLength != 2) {
                        throw new DecoderException("tRNS chunk for gray image must have length 2");
                    }

                    metadata.tRNS_gray = readUShort();
                } else if (metadata.IHDR_colorType == COLOR_TYPE_RGB) {
                    if (chunkLength != 6) {
                        throw new DecoderException("tRNS chunk for RGB image must have length 6");
                    }

                    metadata.tRNS_red = readUShort();
                    metadata.tRNS_green = readUShort();
                    metadata.tRNS_blue = readUShort();
                } else if (metadata.IHDR_colorType == COLOR_TYPE_PALETTE) {
                    if (!metadata.any(PNGMetadata.CHUNK_PLTE)) {
                        throw new DecoderException("PLTE is required before tRNS");
                    }
                    if (chunkLength > metadata.PLTE_entries.length / 3) {
                        throw new DecoderException("tRNS chunk has more entries than prior PLTE chunk");
                    }

                    byte[] alpha = new byte[chunkLength];
                    readFully(ByteBuffer.wrap(alpha));
                    metadata.tRNS_alpha = alpha;
                } else {
                    throw new DecoderException("Gray alpha or RGB alpha image cannot have a tRNS chunk");
                }

                metadata.set(PNGMetadata.CHUNK_tRNS);

            } else if (chunkType == cHRM_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("cHRM must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_cHRM)) {
                    throw new DecoderException("Duplicate cHRM");
                }
                if (chunkLength != 32) {
                    throw new DecoderException("Invalid cHRM chunk length");
                }

                metadata.set(PNGMetadata.CHUNK_cHRM);
                metadata.cHRM_whitePointX = readInt();
                metadata.cHRM_whitePointY = readInt();
                metadata.cHRM_redX = readInt();
                metadata.cHRM_redY = readInt();
                metadata.cHRM_greenX = readInt();
                metadata.cHRM_greenY = readInt();
                metadata.cHRM_blueX = readInt();
                metadata.cHRM_blueY = readInt();

            } else if (chunkType == gAMA_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("gAMA must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_gAMA)) {
                    throw new DecoderException("Duplicate gAMA");
                }
                if (chunkLength != 4) {
                    throw new DecoderException("Invalid gAMA chunk length");
                }

                metadata.set(PNGMetadata.CHUNK_gAMA);
                metadata.gAMA_gamma = readInt();

            } else if (chunkType == iCCP_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("iCCP must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_iCCP)) {
                    throw new DecoderException("Duplicate iCCP");
                }
                if (chunkLength == 0) {
                    throw new DecoderException("Invalid iCCP chunk length");
                }

                String profileName = readKeyword("ICC profile name");

                if (chunkRemaining == 0) {
                    throw new DecoderException("Invalid iCCP chunk length");
                }

                int compressionMethod = nextRawByte() & 0xFF;
                chunkRemaining--;
                if (compressionMethod != COMPRESSION_METHOD_DEFLATE) {
                    throw new DecoderException("Unknown iCCP compression method");
                }

                ByteBuffer uncompressedData = readCompressedData("iCCP");
                byte[] data = uncompressedData.array();
                // trim the array
                if (uncompressedData.limit() < uncompressedData.capacity()) {
                    data = Arrays.copyOf(data, uncompressedData.limit());
                }

                metadata.iCCP_profileName = profileName;
                metadata.iCCP_compressionMethod = compressionMethod;
                metadata.iCCP_profile = data;
                metadata.set(PNGMetadata.CHUNK_iCCP);

            } else if (chunkType == sBIT_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("sBIT must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_sBIT)) {
                    throw new DecoderException("Duplicate sBIT");
                }

                if (metadata.IHDR_colorType == COLOR_TYPE_GRAYSCALE) {
                    if (chunkLength != 1) {
                        throw new DecoderException("Invalid sBIT chunk length");
                    }

                    metadata.sBIT_grayBits = nextRawByte() & 0xFF;
                } else if (metadata.IHDR_colorType == COLOR_TYPE_RGB ||
                        metadata.IHDR_colorType == COLOR_TYPE_PALETTE) {
                    if (chunkLength != 3) {
                        throw new DecoderException("Invalid sBIT chunk length");
                    }

                    metadata.sBIT_redBits = nextRawByte() & 0xFF;
                    metadata.sBIT_greenBits = nextRawByte() & 0xFF;
                    metadata.sBIT_blueBits = nextRawByte() & 0xFF;
                } else if (metadata.IHDR_colorType == COLOR_TYPE_GRAY_ALPHA) {
                    if (chunkLength != 2) {
                        throw new DecoderException("Invalid sBIT chunk length");
                    }

                    metadata.sBIT_grayBits = nextRawByte() & 0xFF;
                    metadata.sBIT_alphaBits = nextRawByte() & 0xFF;
                } else if (metadata.IHDR_colorType == COLOR_TYPE_RGB_ALPHA) {
                    if (chunkLength != 4) {
                        throw new DecoderException("Invalid sBIT chunk length");
                    }

                    metadata.sBIT_redBits = nextRawByte() & 0xFF;
                    metadata.sBIT_greenBits = nextRawByte() & 0xFF;
                    metadata.sBIT_blueBits = nextRawByte() & 0xFF;
                    metadata.sBIT_alphaBits = nextRawByte() & 0xFF;
                }

                metadata.check_sBIT(DecoderException::new);

                metadata.set(PNGMetadata.CHUNK_sBIT);

            } else if (chunkType == sRGB_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("sRGB must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_sRGB)) {
                    throw new DecoderException("Duplicate sRGB");
                }

                if (chunkLength != 1) {
                    throw new DecoderException("Invalid sRGB chunk length");
                }

                metadata.sRGB_renderingIntent = nextRawByte() & 0xFF;
                metadata.check_sRGB(DecoderException::new);

                metadata.set(PNGMetadata.CHUNK_sRGB);

            } else if (chunkType == cICP_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("cICP must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_cICP)) {
                    throw new DecoderException("Duplicate cICP");
                }

                if (chunkLength != 4) {
                    throw new DecoderException("Invalid cICP chunk length");
                }

                metadata.cICP_colorPrimaries = nextRawByte() & 0xFF;
                metadata.cICP_transferCharacteristics = nextRawByte() & 0xFF;
                metadata.cICP_matrixCoefficients = nextRawByte() & 0xFF;
                metadata.cICP_videoFullRangeFlag = nextRawByte() & 0xFF;
                metadata.check_cICP(DecoderException::new);

                metadata.set(PNGMetadata.CHUNK_cICP);

            } else if (chunkType == mDCV_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("mDCV must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_mDCV)) {
                    throw new DecoderException("Duplicate mDCV");
                }

                if (chunkLength != 24) {
                    throw new DecoderException("Invalid mDCV chunk length");
                }

                float[] primaries = new float[6];
                float[] whitePoint = new float[2];
                for (int i = 0; i < primaries.length; i++) {
                    primaries[i] = readUShort() * 0.00002f;
                }
                for (int i = 0; i < whitePoint.length; i++) {
                    whitePoint[i] = readUShort() * 0.00002f;
                }
                float maxLuminance = Integer.toUnsignedLong(readInt()) * 0.0001f;
                float minLuminance = Integer.toUnsignedLong(readInt()) * 0.0001f;
                metadata.mDCV = new MasteringDisplayColorVolume(primaries, whitePoint, maxLuminance, minLuminance);
                metadata.set(PNGMetadata.CHUNK_mDCV);

            } else if (chunkType == cLLI_TYPE) {
                if (metadata.any(PNGMetadata.CHUNK_PLTE)
                        || stage >= STAGE_FIRST_IDAT) {
                    throw new DecoderException("mDCV must appear before PLTE and IDAT");
                }
                if (metadata.any(PNGMetadata.CHUNK_cLLI)) {
                    throw new DecoderException("Duplicate cLLI");
                }

                if (chunkLength != 8) {
                    throw new DecoderException("Invalid cLLI chunk length");
                }

                float maxCLL = Integer.toUnsignedLong(readInt()) * 0.0001f;
                float maxFALL = Integer.toUnsignedLong(readInt()) * 0.0001f;
                metadata.cLLI = new ContentLightLevelInformation(maxCLL, maxFALL);
                metadata.set(PNGMetadata.CHUNK_cLLI);

            } else if (chunkType == tEXt_TYPE) {
                if (chunkLength == 0) {
                    throw new DecoderException("Invalid tEXt chunk length");
                }

                String keyword = readKeyword("tEXt keyword");

                String value = "";
                if (chunkRemaining > 0) {
                    byte[] bytes = new byte[chunkRemaining];
                    readFully(ByteBuffer.wrap(bytes));
                    value = new String(bytes, StandardCharsets.ISO_8859_1);
                }

                Text text = new Text();
                text.type = tEXt_TYPE;
                text.keyword = keyword;
                text.text = value;

                metadata.texts.add(text);

            } else if (chunkType == zTXt_TYPE) {
                if (chunkLength == 0) {
                    throw new DecoderException("Invalid zTXt chunk length");
                }

                String keyword = readKeyword("zTXt keyword");

                if (chunkRemaining == 0) {
                    throw new DecoderException("Invalid zTXt chunk length");
                }

                int compressionMethod = nextRawByte() & 0xFF;
                chunkRemaining--;
                if (compressionMethod != COMPRESSION_METHOD_DEFLATE) {
                    throw new DecoderException("Unknown zTXt compression method");
                }

                ByteBuffer uncompressedData = readCompressedData("zTXt");

                Text text = new Text();
                text.type = zTXt_TYPE;
                text.keyword = keyword;
                text.compressionFlag = true;
                text.compressionMethod = compressionMethod;
                text.text = new String(uncompressedData.array(),
                        0, uncompressedData.limit(), StandardCharsets.ISO_8859_1);

                metadata.texts.add(text);

            } else if (chunkType == iTXt_TYPE) {
                if (chunkLength == 0) {
                    throw new DecoderException("Invalid iTXt chunk length");
                }

                String keyword = readKeyword("iTXt keyword");

                if (chunkRemaining < 2) {
                    throw new DecoderException("Invalid iTXt chunk length");
                }

                boolean compressionFlag = nextRawByte() != 0;
                int compressionMethod = nextRawByte() & 0xFF;
                chunkRemaining -= 2;
                if (compressionMethod != COMPRESSION_METHOD_DEFLATE) {
                    throw new DecoderException("Unknown zTXt compression method");
                }

                if (chunkRemaining == 0) {
                    throw new DecoderException("Invalid iTXt chunk length");
                }

                String languageTag = readString(StandardCharsets.ISO_8859_1);

                if (chunkRemaining == 0) {
                    throw new DecoderException("Invalid iTXt chunk length");
                }

                String translatedKeyword = readString(StandardCharsets.UTF_8);

                String value = "";
                if (chunkRemaining > 0) {
                    if (compressionFlag) {
                        ByteBuffer uncompressedData = readCompressedData("iTXt");
                        value = new String(uncompressedData.array(),
                                0, uncompressedData.limit(), StandardCharsets.UTF_8);
                    } else {
                        byte[] bytes = new byte[chunkRemaining];
                        readFully(ByteBuffer.wrap(bytes));
                        value = new String(bytes, StandardCharsets.UTF_8);
                    }
                }

                Text text = new Text();
                text.type = iTXt_TYPE;
                text.keyword = keyword;
                text.compressionFlag = compressionFlag;
                text.compressionMethod = compressionMethod;
                text.languageTag = languageTag;
                text.translatedKeyword = translatedKeyword;
                text.text = value;

                metadata.texts.add(text);

            } else {
                skip(chunkRemaining);
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

    public void skipImage() throws IOException {
        if (chunkType != IDAT_TYPE || stage != STAGE_FIRST_IDAT) {
            throw new DecoderException("Not IDAT");
        }

        while (chunkType == IDAT_TYPE) {
            skip(chunkRemaining);
            chunkRemaining = 0;
            readChunkHeader();
        }
    }

    public void decodeImage(@NonNull Pixmap dstPixels,
                            @Nullable Rect2ic srcRegion) throws IOException {
        if (chunkType != IDAT_TYPE || stage != STAGE_FIRST_IDAT) {
            throw new DecoderException("Not IDAT");
        }

        Object dstBase = dstPixels.getBase();

        int dstCT = dstPixels.getColorType();
        int bitDepth = metadata.IHDR_bitDepth;
        boolean interlace = metadata.IHDR_interlaceMethod != INTERLACE_METHOD_NONE;
        boolean noConversion = !interlace;
        if (noConversion) {
            noConversion = switch (metadata.IHDR_colorType) {
                case COLOR_TYPE_GRAYSCALE -> {
                    if (bitDepth == 8) {
                        yield dstCT == ColorInfo.CT_GRAY_8;
                    } else if (bitDepth == 16) {
                        yield dstCT == ColorInfo.CT_GRAY_16;
                    } else {
                        yield false;
                    }
                }
                case COLOR_TYPE_RGB -> {
                    if (bitDepth == 8) {
                        yield dstCT == ColorInfo.CT_RGB_888;
                    } else if (bitDepth == 16) {
                        yield dstCT == ColorInfo.CT_RGB_161616;
                    } else {
                        yield false;
                    }
                }
                case COLOR_TYPE_PALETTE -> false;
                case COLOR_TYPE_GRAY_ALPHA -> {
                    if (bitDepth == 8) {
                        yield dstCT == ColorInfo.CT_GRAY_ALPHA_88;
                    } else {
                        assert bitDepth == 16;
                        yield dstCT == ColorInfo.CT_GRAY_ALPHA_1616;
                    }
                }
                case COLOR_TYPE_RGB_ALPHA -> {
                    if (bitDepth == 8) {
                        yield dstCT == ColorInfo.CT_RGBA_8888;
                    } else {
                        assert bitDepth == 16;
                        yield dstCT == ColorInfo.CT_RGBA_16161616;
                    }
                }
                default -> throw new DecoderException("Unknown color type");
            };
        }
        if (noConversion) {
            boolean premul = dstPixels.getInfo().alphaType() == ColorInfo.AT_PREMUL &&
                    (metadata.any(PNGMetadata.CHUNK_tRNS) ||
                            metadata.IHDR_colorType == COLOR_TYPE_RGB_ALPHA ||
                            metadata.IHDR_colorType == COLOR_TYPE_GRAY_ALPHA);
            if (premul) {
                noConversion = false;
            }
        }

        if (inflater == null) {
            inflater = new Inflater();
        } else {
            inflater.reset();
        }

        int width = metadata.IHDR_width;
        int height = metadata.IHDR_height;

        boolean is16 = bitDepth == 16;
        int bytesPerPixel = metadata.numChannels() << (is16 ? 1 : 0);
        // reserve 32 bytes for the filter type (1 byte) of next row,
        // and tail padding for vector instructions
        int rowBytes = computeRowBytes(width, 32);
        // allocate heap buffer (BIG ENDIAN)
        ByteBuffer currScanlineBuf = ByteBuffer.allocate(rowBytes + 32);
        ByteBuffer prevScanlineBuf = ByteBuffer.allocate(rowBytes + 32);

        // read the filter of first scanline
        readScanlineBytes(currScanlineBuf.limit(1));
        int filter = currScanlineBuf.get(0) & 0xFF;

        int passCount = (interlace ? 7 : 0);
        while (passCount > 0) {
            int passWidth = computePassWidth(width, passCount - 1);
            int passHeight = computePassHeight(height, passCount - 1);

            if (passWidth != 0 && passHeight != 0) {
                break;
            }
            passCount--;
        }

        for (int pass = interlace ? 0 : -1; pass < passCount; pass++) {

            int passWidth = width;
            int passHeight = height;
            if (pass >= 0) {
                passWidth = computePassWidth(width, pass);
                passHeight = computePassHeight(height, pass);
            }
            if (passWidth <= 0 || passHeight <= 0) {
                continue;
            }
            int passRowBytes = computeRowBytes(passWidth, 0);
            assert passRowBytes <= rowBytes;

            int yStep = pass >= 0 ?adam7YStep[pass] : 1;
            for (int i = 0, j = pass >= 0? adam7YOffset[pass] : 0;
                 i < passHeight && j < height;
                 i++, j += yStep) {
                // we read current row and next row filter
                boolean lastRow = pass == passCount - 1 && i == passHeight - 1;
                currScanlineBuf
                        .position(0)
                        .limit(lastRow ? passRowBytes : passRowBytes + 1);
                readScanlineBytes(currScanlineBuf);
                currScanlineBuf.flip();

                int nextFilter = filter;
                if (!lastRow) {
                    nextFilter = currScanlineBuf.get(passRowBytes) & 0xFF;
                    currScanlineBuf.limit(passRowBytes);
                }

                switch (filter) {
                    case FILTER_TYPE_NONE:
                        break;
                    case FILTER_TYPE_SUB:
                        switch (bytesPerPixel) {
                            case 1 -> FILTER.decodeSub1(currScanlineBuf.array(), passRowBytes);
                            case 2 -> FILTER.decodeSub2(currScanlineBuf.array(), passRowBytes);
                            case 3 -> FILTER.decodeSub3(currScanlineBuf.array(), passRowBytes);
                            case 4 -> FILTER.decodeSub4(currScanlineBuf.array(), passRowBytes);
                            case 6 -> FILTER.decodeSub6(currScanlineBuf.array(), passRowBytes);
                            case 8 -> FILTER.decodeSub8(currScanlineBuf.array(), passRowBytes);
                        }
                        break;
                    case FILTER_TYPE_UP:
                        PNGFilter.decodeUp(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                        break;
                    case FILTER_TYPE_AVERAGE:
                        switch (bytesPerPixel) {
                            case 3 -> FILTER.decodeAverage3(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 4 -> FILTER.decodeAverage4(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 6 -> FILTER.decodeAverage6(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 8 -> FILTER.decodeAverage8(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            default -> PNGFilter.decodeAverage(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        }
                        break;
                    case FILTER_TYPE_PAETH:
                        switch (bytesPerPixel) {
                            case 3 -> FILTER.decodePaeth3(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 4 -> FILTER.decodePaeth4(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 6 -> FILTER.decodePaeth6(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            case 8 -> FILTER.decodePaeth8(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes);
                            default -> PNGFilter.decodePaeth(currScanlineBuf.array(), prevScanlineBuf.array(), passRowBytes, bytesPerPixel);
                        }
                        break;
                    default:
                        throw new DecoderException("Unknown filter type: " + filter);
                }

                if (noConversion) {
                    long dstAddr = dstPixels.getAddress(0, j);
                    if (dstBase == null) {
                        var dstBuf = MemoryUtil.memByteBuffer(dstAddr, passRowBytes);
                        if (is16) {
                            // copySwapMemory
                            dstBuf.asShortBuffer().put(currScanlineBuf.asShortBuffer());
                        } else {
                            dstBuf.put(currScanlineBuf);
                        }
                    } else {
                        if (is16) {
                            // copySwapMemory
                            ShortBuffer.wrap((short[]) dstBase, (int) (dstAddr>>1), (passRowBytes>>1))
                                    .put(currScanlineBuf.asShortBuffer());
                        } else {
                            PixelUtils.mixedMemCopy(currScanlineBuf.array(), 0,
                                    dstPixels.getBase(), dstAddr, passRowBytes);
                        }
                    }
                } else {
                    processScanline(currScanlineBuf, dstPixels, j, passWidth, pass, metadata);
                }

                filter = nextFilter;

                ByteBuffer tBuf = prevScanlineBuf;
                prevScanlineBuf = currScanlineBuf;
                currScanlineBuf = tBuf;
            }
        }

        if (!inflater.finished()) {
            throw new DecoderException("ZLIB stream not finished after image data");
        }
        inflater.reset();

        // Spec: Some images have unused trailing bytes at the end of the final IDAT chunk.
        // This could happen when an entire buffer is stored rather than just the portion
        // of the buffer which is used. This is undesirable. Preferably, an encoder would
        // not include these unused bytes. If it must, setting the bytes to zero will
        // prevent accidental data sharing. A decoder should ignore these trailing bytes.
        skip(chunkRemaining);
        chunkRemaining = 0;
        stage = STAGE_AFTER_IDAT;
    }

    private static int readPackedSample(ByteBuffer scanline, int sampleIndex, int bitDepth) {
        int bitIndex = sampleIndex * bitDepth;
        int byteIndex = bitIndex >>> 3;
        int bitOffset = bitIndex & 7;

        int shift = 8 - bitDepth - bitOffset;
        int mask = (1 << bitDepth) - 1;

        return (scanline.get(byteIndex) & 0xFF) >>> shift & mask;
    }

    // per pixel operations
    @SuppressWarnings("PointlessArithmeticExpression")
    private static void processScanline(ByteBuffer scanline, Pixmap dst, int dstRowNum,
                                 int passWidth, int pass, PNGMetadata metadata) {

        int colorType = metadata.IHDR_colorType;
        int bitDepth = metadata.IHDR_bitDepth;
        byte[] plte = metadata.PLTE_entries;
        boolean tRNS = metadata.any(PNGMetadata.CHUNK_tRNS);
        boolean premul = dst.getInfo().alphaType() == ColorInfo.AT_PREMUL &&
                (tRNS || colorType == COLOR_TYPE_RGB_ALPHA || colorType == COLOR_TYPE_GRAY_ALPHA);

        ByteBuffer bdst = null;
        ShortBuffer sdst = null;
        IntBuffer idst = null;
        long dstAddr = dst.getAddress(0, dstRowNum);
        int minRB = dst.getInfo().minRowBytes();
        int dstCT = dst.getColorType();
        switch (dstCT) {
            case ColorInfo.CT_GRAY_8:
            case ColorInfo.CT_GRAY_ALPHA_88:
            case ColorInfo.CT_RGB_888:
            case ColorInfo.CT_RGBA_8888: {
                Object dstBase = dst.getBase();
                if (dstBase == null) {
                    bdst = MemoryUtil.memByteBuffer(dstAddr, minRB);
                } else {
                    bdst = ByteBuffer.wrap((byte[]) dstBase, (int) dstAddr, minRB);
                }
                break;
            }
            case ColorInfo.CT_GRAY_16:
            case ColorInfo.CT_GRAY_ALPHA_1616:
            case ColorInfo.CT_RGB_161616:
            case ColorInfo.CT_RGBA_16161616: {
                Object dstBase = dst.getBase();
                if (dstBase == null) {
                    sdst = MemoryUtil.memByteBuffer(dstAddr, minRB).asShortBuffer();
                } else {
                    sdst = ShortBuffer.wrap((short[]) dstBase, (int) (dstAddr>>1), minRB>>1);
                }
                break;
            }
        }

        int xStep = pass >= 0 ? adam7XStep[pass] : 1;
        int width = metadata.IHDR_width;
        for (int i = 0, j = pass >= 0 ? adam7XOffset[pass] : 0;
             i < passWidth && j < width;
             i++, j += xStep) {

            int r,g,b,a;
            if (bitDepth < 8) {
                int unpack = readPackedSample(scanline, i, bitDepth);
                if (colorType == COLOR_TYPE_GRAYSCALE) {
                    switch (bitDepth) {
                        case 1:
                            r = g = b = unpack * 255;
                            break;
                        case 2:
                            r = g = b = unpack * 85;
                            break;
                        case 4:
                            r = g = b = (unpack << 4) | unpack;
                            break;
                        default:
                            throw new IllegalStateException("Unknown bit depth");
                    }
                    a = (tRNS && unpack == metadata.tRNS_gray) ? 0 : 255;
                } else {
                    assert colorType == COLOR_TYPE_PALETTE;
                    r = plte[unpack * 3 + 0] & 0xFF;
                    g = plte[unpack * 3 + 1] & 0xFF;
                    b = plte[unpack * 3 + 2] & 0xFF;
                    a = tRNS && unpack < metadata.tRNS_alpha.length
                            ? metadata.tRNS_alpha[unpack]
                            : 255;
                }
            } else if (bitDepth == 8) {
                switch (colorType) {
                    case COLOR_TYPE_GRAYSCALE:
                        r = g = b = scanline.get(i) & 0xFF;
                        a = (tRNS && r == metadata.tRNS_gray) ? 0 : 255;
                        break;
                    case COLOR_TYPE_RGB:
                        r = scanline.get(i * 3 + 0) & 0xFF;
                        g = scanline.get(i * 3 + 1) & 0xFF;
                        b = scanline.get(i * 3 + 2) & 0xFF;
                        a = (tRNS &&
                                r == metadata.tRNS_red &&
                                g == metadata.tRNS_green &&
                                b == metadata.tRNS_blue)
                                ? 0 : 255;
                        break;
                    case COLOR_TYPE_PALETTE: {
                        int plteIndex = scanline.get(i) & 0xFF;
                        r = plte[plteIndex * 3 + 0] & 0xFF;
                        g = plte[plteIndex * 3 + 1] & 0xFF;
                        b = plte[plteIndex * 3 + 2] & 0xFF;
                        a = tRNS && plteIndex < metadata.tRNS_alpha.length
                                ? metadata.tRNS_alpha[plteIndex]
                                : 255;
                        break;
                    }
                    case COLOR_TYPE_GRAY_ALPHA:
                        r = g = b = scanline.get(i * 2 + 0) & 0xFF;
                        a = scanline.get(i * 2 + 1) & 0xFF;
                        break;
                    case COLOR_TYPE_RGB_ALPHA:
                        r = scanline.get(i * 4 + 0) & 0xFF;
                        g = scanline.get(i * 4 + 1) & 0xFF;
                        b = scanline.get(i * 4 + 2) & 0xFF;
                        a = scanline.get(i * 4 + 3) & 0xFF;
                        break;
                    default:
                        throw new IllegalStateException("Unknown color type");
                }
            } else {
                assert bitDepth == 16;
                switch (colorType) {
                    case COLOR_TYPE_GRAYSCALE:
                        r = g = b = scanline.getShort(i*2) & 0xFFFF;
                        a = (tRNS && r == metadata.tRNS_gray) ? 0 : 65535;
                        break;
                    case COLOR_TYPE_RGB:
                        r = scanline.getShort(i * 6 + 0) & 0xFFFF;
                        g = scanline.getShort(i * 6 + 2) & 0xFFFF;
                        b = scanline.getShort(i * 6 + 4) & 0xFFFF;
                        a = (tRNS &&
                                r == metadata.tRNS_red &&
                                g == metadata.tRNS_green &&
                                b == metadata.tRNS_blue)
                                ? 0 : 65535;
                        break;
                    case COLOR_TYPE_GRAY_ALPHA:
                        r = g = b = scanline.getShort(i * 4 + 0) & 0xFFFF;
                        a = scanline.get(i * 4 + 2) & 0xFFFF;
                        break;
                    case COLOR_TYPE_RGB_ALPHA:
                        r = scanline.get(i * 8 + 0) & 0xFFFF;
                        g = scanline.get(i * 8 + 2) & 0xFFFF;
                        b = scanline.get(i * 8 + 4) & 0xFFFF;
                        a = scanline.get(i * 8 + 6) & 0xFFFF;
                        break;
                    default:
                        throw new IllegalStateException("Unknown color type");
                }
            }

            if (premul) {
                if (bitDepth <= 8) {
                    r = (r * a + 127) / 255;
                    g = (g * a + 127) / 255;
                    b = (b * a + 127) / 255;
                } else {
                    r = (int) ((((r & 0xFFFFL) * (a & 0xFFFFL) + 32767L) / 65535L));
                    g = (int) ((((g & 0xFFFFL) * (a & 0xFFFFL) + 32767L) / 65535L));
                    b = (int) ((((b & 0xFFFFL) * (a & 0xFFFFL) + 32767L) / 65535L));
                }
            }

            switch (dstCT) {
                case ColorInfo.CT_GRAY_8 -> {
                    bdst.put(j, (byte) r);
                }
                case ColorInfo.CT_GRAY_ALPHA_88 -> {
                    bdst.put(j*2+0, (byte) r);
                    bdst.put(j*2+1, (byte) a);
                }
                case ColorInfo.CT_RGB_888 -> {
                    bdst.put(j*3+0, (byte) r);
                    bdst.put(j*3+1, (byte) g);
                    bdst.put(j*3+2, (byte) b);
                }
                case ColorInfo.CT_RGBA_8888 -> {
                    bdst.put(j*4+0, (byte) r);
                    bdst.put(j*4+1, (byte) g);
                    bdst.put(j*4+2, (byte) b);
                    bdst.put(j*4+3, (byte) a);
                }
                case ColorInfo.CT_GRAY_16 -> {
                    sdst.put(j, (short) r);
                }
                case ColorInfo.CT_GRAY_ALPHA_1616 -> {
                    sdst.put(j*2+0, (short) r);
                    sdst.put(j*2+1, (short) a);
                }
                case ColorInfo.CT_RGB_161616 -> {
                    sdst.put(j*3+0, (short) r);
                    sdst.put(j*3+1, (short) g);
                    sdst.put(j*3+2, (short) b);
                }
                case ColorInfo.CT_RGBA_16161616 -> {
                    sdst.put(j*4+0, (short) r);
                    sdst.put(j*4+1, (short) g);
                    sdst.put(j*4+2, (short) b);
                    sdst.put(j*4+3, (short) a);
                }
            }
        }
    }

    public int computeRowBytes(int width, int reserve) throws IOException {
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
                throw new DecoderException("Not enough ZLIB data, want " + dst.remaining() + " bytes more");
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

    // Keywords shall contain only printable Latin-1 [ISO_8859-1] characters and
    // spaces; that is, only code points 0x20-7E and 0xA1-FF are allowed.
    // To reduce the chances for human misreading of a keyword, leading spaces,
    // trailing spaces, and consecutive spaces are not permitted in keywords,
    // nor is U+00A0 NON-BREAKING SPACE since it is visually indistinguishable
    // from an ordinary space.
    private @NonNull String readKeyword(String what) throws IOException {
        byte[] name = new byte[80];
        int nameLen = 0; // with NUL terminator
        int nameLimit = Math.min(80, chunkRemaining);
        boolean nullTerminated = false;
        while (nameLen < nameLimit) {
            byte b = nextRawByte();
            if (b == 0) {
                nameLen++;
                nullTerminated = true;
                break;
            } else {
                int c = b & 0xFF;
                if (c >= 0x20 && c <= 0x7E || c >= 0xA1) {
                    name[nameLen++] = b;
                } else {
                    throw new DecoderException("Invalid character in " + what + ": 0x" + hex(b));
                }
            }
        }
        chunkRemaining -= nameLen;
        if (nameLen <= 1 || !nullTerminated) {
            throw new DecoderException("Invalid " + what + " length: " + (nameLen <= 1 ? "zero" : "greater than 79"));
        }

        String str = new String(name, 0, nameLen - 1, StandardCharsets.ISO_8859_1);
        if (str.startsWith(" ") || str.endsWith(" ") || str.contains("  ")) {
            throw new DecoderException("Leading spaces, trailing spaces, and consecutive spaces are not allowed in " + what);
        }
        return str;
    }

    private @NonNull String readString(Charset charset) throws IOException {
        byte[] name = new byte[64];
        int nameLen = 0; // with NUL terminator
        int nameLimit = chunkRemaining;
        while (nameLen < nameLimit) {
            byte b = nextRawByte();
            if (b == 0) {
                nameLen++;
                break;
            } else {
                if (nameLen + 1 >= name.length) {
                    if (name.length >= Integer.MAX_VALUE / 2) {
                        throw new DecoderException("String data is too big, failed to allocate buffer");
                    }
                    name = Arrays.copyOf(name, name.length * 2);
                }
                name[nameLen++] = b;
            }
        }
        chunkRemaining -= nameLen;
        if (nameLen <= 1) {
            return "";
        }

        return new String(name, 0, nameLen - 1, charset);
    }

    private @NonNull ByteBuffer readCompressedData(String what) throws IOException {
        if (inflater == null) {
            inflater = new Inflater();
        } else {
            inflater.reset();
        }

        ByteBuffer dst = ByteBuffer.allocate(512);

        Inflater inf = inflater;
        while (!inf.finished() && !inf.needsDictionary()) {
            if (inf.needsInput()) {
                if (chunkRemaining == 0) {
                    throw new DecoderException("Not enough compressed data in " + what);
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
            if (!dst.hasRemaining()) {
                if (dst.capacity() >= Integer.MAX_VALUE / 2) {
                    throw new DecoderException("Inflated chunk data is too big, failed to allocate buffer");
                }
                ByteBuffer newDst =
                        ByteBuffer.allocate(dst.capacity() + (dst.capacity() >> 1));
                dst.flip();
                newDst.put(dst);
                dst = newDst;
            }
            try {
                inf.inflate(dst);
            } catch (DataFormatException e) {
                throw new DecoderException("Invalid ZLIB data: " + e.getMessage());
            }
        }

        if (!inf.finished()) {
            throw new DecoderException("ZLIB stream not finished");
        }
        inf.reset();

        if (chunkRemaining > 0) {
            throw new DecoderException("Chunk not finished after compressed data");
        }

        return dst.flip();
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

    private char readUShort() throws IOException {
        byte b1 = nextRawByte();
        byte b2 = nextRawByte();
        return (char) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
    }
}

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

import icyllis.arc3d.core.ContentLightLevelInformation;
import icyllis.arc3d.core.MasteringDisplayColorVolume;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Function;

import static icyllis.arc3d.core.image.PNG.*;

public class PNGMetadata {

    static final int
            CHUNK_IHDR = 1 << 0,
            CHUNK_PLTE = 1 << 1,
            CHUNK_tRNS = 1 << 2,
            CHUNK_cHRM = 1 << 3,
            CHUNK_gAMA = 1 << 4,
            CHUNK_iCCP = 1 << 5,
            CHUNK_sBIT = 1 << 6,
            CHUNK_sRGB = 1 << 7,
            CHUNK_cICP = 1 << 8,
            CHUNK_mDCV = 1 << 9,
            CHUNK_cLLI = 1 << 10,
            CHUNK_bKGD = 1 << 11,
            CHUNK_hIST = 1 << 12,
            CHUNK_pHYs = 1 << 13,
            CHUNK_sPLT = 1 << 14,
            CHUNK_eXIf = 1 << 15,
            CHUNK_tIME = 1 << 16,
            CHUNK_acTL = 1 << 17;
    int presentChunks;

    // IHDR chunk
    public int IHDR_width;
    public int IHDR_height;
    public int IHDR_bitDepth;
    public int IHDR_colorType;
    public int IHDR_compressionMethod;
    public int IHDR_filterMethod;
    public int IHDR_interlaceMethod; // 0 == none, 1 == adam7

    // PLTE chunk
    public byte[] PLTE_entries; // rgb0 rgb1 ...

    // tRNS chunk
    public int tRNS_gray;
    public int tRNS_red;
    public int tRNS_green;
    public int tRNS_blue;
    public byte[] tRNS_alpha; // May have fewer entries than PLTE_entries.

    // cHRM chunk
    public int cHRM_whitePointX;
    public int cHRM_whitePointY;
    public int cHRM_redX;
    public int cHRM_redY;
    public int cHRM_greenX;
    public int cHRM_greenY;
    public int cHRM_blueX;
    public int cHRM_blueY;

    // gAMA chunk
    public int gAMA_gamma;

    // iCCP chunk
    public String iCCP_profileName;
    public int iCCP_compressionMethod;
    public byte[] iCCP_profile;

    // sBIT chunk
    public int sBIT_grayBits;
    public int sBIT_redBits;
    public int sBIT_greenBits;
    public int sBIT_blueBits;
    public int sBIT_alphaBits;

    // sRGB chunk
    public int sRGB_renderingIntent;

    // cICP chunk
    public int cICP_colorPrimaries;
    public int cICP_transferCharacteristics;
    public int cICP_matrixCoefficients;
    public int cICP_videoFullRangeFlag;

    // mDCV chunk
    public @Nullable MasteringDisplayColorVolume mDCV;

    // cLLI chunk
    public @Nullable ContentLightLevelInformation cLLI;

    // tEXt, zTXt, iTXt chunk
    public @NonNull ArrayList<@NonNull Text> texts = new ArrayList<>();

    // bKGD chunk
    public int bKGD_gray;
    public int bKGD_red;
    public int bKGD_green;
    public int bKGD_blue;
    public int bKGD_index;

    // hIST chunk
    public char[] hIST_histogram;

    // pHYs chunk
    public int pHYs_pixelsPerUnitXAxis;
    public int pHYs_pixelsPerUnitYAxis;
    public int pHYs_unitSpecifier; // 0 == unknown, 1 == meter

    // sPLT chunk
    public @NonNull ArrayList<@NonNull SuggestedPalette> suggestedPalettes = new ArrayList<>();

    // eXIf chunk
    public byte[] eXIf_data;

    // tIME chunk
    public int tIME_year;
    public int tIME_month;     // 1-12
    public int tIME_day;       // 1-31
    public int tIME_hour;      // 0-23
    public int tIME_minute;    // 0-59
    public int tIME_second;    // 0-60

    // acTL chunk
    public int acTL_numFrames;
    public int acTL_numPlays;

    boolean any(int chunkMask) {
        return (presentChunks & chunkMask) != 0;
    }

    public void set(int chunkMask) {
        presentChunks |= chunkMask;
    }

    public boolean hasChunk(int chunkType) {
        int mask = switch (chunkType) {
            case IHDR_TYPE -> CHUNK_IHDR;
            case PLTE_TYPE -> CHUNK_PLTE;
            case tRNS_TYPE -> CHUNK_tRNS;
            case cHRM_TYPE -> CHUNK_cHRM;
            case gAMA_TYPE -> CHUNK_gAMA;
            case iCCP_TYPE -> CHUNK_iCCP;
            case sBIT_TYPE -> CHUNK_sBIT;
            case sRGB_TYPE -> CHUNK_sRGB;
            case cICP_TYPE -> CHUNK_cICP;
            case mDCV_TYPE -> CHUNK_mDCV;
            case cLLI_TYPE -> CHUNK_cLLI;
            case bKGD_TYPE -> CHUNK_bKGD;
            case hIST_TYPE -> CHUNK_hIST;
            case pHYs_TYPE -> CHUNK_pHYs;
            case sPLT_TYPE -> CHUNK_sPLT;
            case eXIf_TYPE -> CHUNK_eXIf;
            case tIME_TYPE -> CHUNK_tIME;
            case acTL_TYPE -> CHUNK_acTL;
            default -> 0;
        };
        return (presentChunks & mask) != 0;
    }

    public void setIHDR(int width, int height, int bitDepth, int colorType,
                        int interlaceMethod) {
        set(CHUNK_IHDR);
        IHDR_width = width;
        IHDR_height = height;
        IHDR_bitDepth = bitDepth;
        IHDR_colorType = colorType;
        IHDR_compressionMethod = COMPRESSION_METHOD_DEFLATE;
        IHDR_filterMethod = FILTER_METHOD_ADAPTIVE;
        IHDR_interlaceMethod = interlaceMethod;
    }

    public void setPLTE(int[] colors) {
        set(CHUNK_PLTE);
        PLTE_entries = new byte[colors.length * 3];
        for (int i = 0; i < colors.length; i++) {
            int col = colors[i];
            PLTE_entries[i * 3] = (byte) ((col >>> 16) & 0xFF);
            PLTE_entries[i * 3 + 1] = (byte) ((col >>> 8) & 0xFF);
            PLTE_entries[i * 3 + 2] = (byte) ((col) & 0xFF);
        }
    }

    public void checkIHDR(Function<String, ? extends IOException> ex) throws IOException {
        if (IHDR_width <= 0) {
            throw ex.apply("Invalid image width: " + IHDR_width);
        }
        if (IHDR_height <= 0) {
            throw ex.apply("Invalid image height: " + IHDR_height);
        }
        int bitDepth = IHDR_bitDepth;
        if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 &&
                bitDepth != 8 && bitDepth != 16) {
            throw ex.apply("Bit depth must be 1, 2, 4, 8, or 16, found: " + bitDepth);
        }
        int colorType = IHDR_colorType;
        if (colorType != COLOR_TYPE_GRAYSCALE &&
                colorType != COLOR_TYPE_RGB &&
                colorType != COLOR_TYPE_PALETTE &&
                colorType != COLOR_TYPE_GRAY_ALPHA &&
                colorType != COLOR_TYPE_RGB_ALPHA) {
            throw ex.apply("Color type must be 0, 2, 3, 4, or 6, found: " + colorType);
        }
        if (colorType == COLOR_TYPE_PALETTE && bitDepth == 16) {
            throw ex.apply("Bad color type/bit depth combination!");
        }
        if ((colorType == COLOR_TYPE_RGB ||
                colorType == COLOR_TYPE_RGB_ALPHA ||
                colorType == COLOR_TYPE_GRAY_ALPHA) &&
                (bitDepth != 8 && bitDepth != 16)) {
            throw ex.apply("Bad color type/bit depth combination!");
        }
        if (IHDR_compressionMethod != COMPRESSION_METHOD_DEFLATE) {
            throw ex.apply("Unknown compression method: " + IHDR_compressionMethod + " (not 0)");
        }
        if (IHDR_filterMethod != FILTER_METHOD_ADAPTIVE) {
            throw ex.apply("Unknown filter method: " + IHDR_filterMethod + " (not 0)");
        }
        if (IHDR_interlaceMethod != INTERLACE_METHOD_NONE && IHDR_interlaceMethod != INTERLACE_METHOD_ADAM7) {
            throw ex.apply("Unknown interlace method: " + IHDR_interlaceMethod + " (not 0 or 1)");
        }
    }

    public void checkPLTE(int numEntries, Function<String, ? extends IOException> ex) throws IOException {
        if (numEntries <= 0 ||
                numEntries > 256) {
            throw ex.apply("Invalid number of palette entries: " + numEntries);
        }

        if (IHDR_colorType == COLOR_TYPE_PALETTE) {
            // PNG spec: The number of palette entries shall not exceed the range that can be represented
            // in the image bit depth (for example, 2^4 = 16 for a bit depth of 4). It is permissible to
            // have fewer entries than the bit depth would allow. In that case, any out-of-range pixel
            // value found in the image data is an error.
            if (numEntries > (1 << IHDR_bitDepth)) {
                throw ex.apply("Too many palette entries, " + numEntries + " for bit depth " + IHDR_bitDepth);
            }
        }
    }

    public void check_sBIT(Function<String, ? extends IOException> ex) throws IOException {
        if (IHDR_colorType == COLOR_TYPE_GRAYSCALE) {
            if (sBIT_grayBits <= 0 || sBIT_grayBits > IHDR_bitDepth) {
                throw ex.apply("Invalid sBIT depth");
            }
        } else if (IHDR_colorType == COLOR_TYPE_RGB ||
                IHDR_colorType == COLOR_TYPE_PALETTE) {

            int sampleDepth = IHDR_colorType == COLOR_TYPE_PALETTE
                    ? 8
                    : IHDR_bitDepth;

            if (sBIT_redBits <= 0 || sBIT_redBits > sampleDepth ||
                    sBIT_greenBits <= 0 || sBIT_greenBits > sampleDepth ||
                    sBIT_blueBits <= 0 || sBIT_blueBits > sampleDepth) {
                throw ex.apply("Invalid sBIT depth");
            }
        } else if (IHDR_colorType == COLOR_TYPE_GRAY_ALPHA) {
            if (sBIT_grayBits <= 0 || sBIT_grayBits > IHDR_bitDepth ||
                    sBIT_alphaBits <= 0 || sBIT_alphaBits > IHDR_bitDepth) {
                throw ex.apply("Invalid sBIT depth");
            }
        } else if (IHDR_colorType == COLOR_TYPE_RGB_ALPHA) {
            if (sBIT_redBits <= 0 || sBIT_redBits > IHDR_bitDepth ||
                    sBIT_greenBits <= 0 || sBIT_greenBits > IHDR_bitDepth ||
                    sBIT_blueBits <= 0 || sBIT_blueBits > IHDR_bitDepth ||
                    sBIT_alphaBits <= 0 || sBIT_alphaBits > IHDR_bitDepth) {
                throw ex.apply("Invalid sBIT depth");
            }
        }
    }

    public void check_sRGB(Function<String, ? extends IOException> ex) throws IOException {
        if (sRGB_renderingIntent < 0 || sRGB_renderingIntent > 3) {
            throw ex.apply("Invalid sRGB rendering intent: " + sRGB_renderingIntent);
        }
    }

    public void set_cICP(int colorPrimaries, int transferCharacteristics, int matrixCoefficients, int videoFullRangeFlag) {
        set(CHUNK_cICP);
        cICP_colorPrimaries = colorPrimaries;
        cICP_transferCharacteristics = transferCharacteristics;
        cICP_matrixCoefficients = matrixCoefficients;
        cICP_videoFullRangeFlag = videoFullRangeFlag;
    }

    public void set_sRGB(int renderingIntent) {
        set(CHUNK_sRGB);
        sRGB_renderingIntent = renderingIntent;
    }

    public void check_cICP(Function<String, ? extends IOException> ex) throws IOException {
        if (cICP_matrixCoefficients != 0) {
            throw ex.apply("cICP matrix coefficients must be 0!");
        }
        if (cICP_videoFullRangeFlag != 0 && cICP_videoFullRangeFlag != 1) {
            throw ex.apply("cICP video full range flag must be 0 or 1!");
        }
    }

    public int numChannels() {
        return PNG.numChannels(IHDR_colorType);
    }

    public int computeRowBytes(int width, int reserve,
                               Function<String, ? extends IOException> ex) throws IOException {
        assert width > 0;
        long rowBytes = (long) numChannels() *
                IHDR_bitDepth * width;
        rowBytes = (rowBytes + 7) / 8;
        if (rowBytes <= 0 || rowBytes > Integer.MAX_VALUE - reserve - 8) {
            throw ex.apply("Scanline is too big");
        }
        return (int) rowBytes;
    }
}

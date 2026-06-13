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

public class PNG {

    //   Color    Allowed    Interpretation
    //   Type    Bit Depths
    //
    //   0       1,2,4,8,16  Each pixel is a grayscale sample.
    //
    //   2       8,16        Each pixel is an R,G,B triple.
    //
    //   3       1,2,4,8     Each pixel is a palette index;
    //                       a PLTE chunk must appear.
    //
    //   4       8,16        Each pixel is a grayscale sample,
    //                       followed by an alpha sample.
    //
    //   6       8,16        Each pixel is an R,G,B triple,
    //                       followed by an alpha sample.
    public static final int COLOR_TYPE_GRAYSCALE = 0;
    public static final int COLOR_TYPE_RGB = 2;
    public static final int COLOR_TYPE_PALETTE = 3;
    public static final int COLOR_TYPE_GRAY_ALPHA = 4;
    public static final int COLOR_TYPE_RGB_ALPHA = 6;

    public static int numChannels(int colorType) {
        return switch (colorType) {
            case COLOR_TYPE_GRAYSCALE, COLOR_TYPE_PALETTE -> 1;
            case COLOR_TYPE_GRAY_ALPHA -> 2;
            case COLOR_TYPE_RGB -> 3;
            case COLOR_TYPE_RGB_ALPHA -> 4;
            default -> 0;
        };
    }

    // Critical chunks
    public static final int IHDR_TYPE = 0x49484452;
    public static final int PLTE_TYPE = 0x504c5445;
    public static final int IDAT_TYPE = 0x49444154;
    public static final int IEND_TYPE = 0x49454e44;

    // Ancillary chunks
    // Transparency information
    public static final int tRNS_TYPE = 0x74524e53;
    // Color space information
    public static final int cHRM_TYPE = 0x6348524d;
    public static final int gAMA_TYPE = 0x67414d41;
    public static final int iCCP_TYPE = 0x69434350;
    public static final int sBIT_TYPE = 0x73424954;
    public static final int sRGB_TYPE = 0x73524742;
    public static final int cICP_TYPE = 0x63494350;
    public static final int mDCV_TYPE = 0x6D444356;
    public static final int cLLI_TYPE = 0x634C4C49;
    // Textual information
    public static final int tEXt_TYPE = 0x74455874;
    public static final int zTXt_TYPE = 0x7a545874;
    public static final int iTXt_TYPE = 0x69545874;
    // Miscellaneous information
    public static final int bKGD_TYPE = 0x624b4744;
    public static final int hIST_TYPE = 0x68495354;
    public static final int pHYs_TYPE = 0x70485973;
    public static final int sPLT_TYPE = 0x73504c54;
    public static final int eXIf_TYPE = 0x65584966;
    // Time stamp information
    public static final int tIME_TYPE = 0x74494d45;
    // Animation information
    public static final int acTL_TYPE = 0x6163544C;
    public static final int fcTL_TYPE = 0x6663544C;
    public static final int fdAT_TYPE = 0x66644154;

    public static boolean isCriticalChunk(int chunkType) {
        return (chunkType & (1 << (5 + 24))) == 0;
    }

    // Filter types
    public static final int FILTER_NONE = 0;
    public static final int FILTER_SUB = 1;
    public static final int FILTER_UP = 2;
    public static final int FILTER_AVERAGE = 3;
    public static final int FILTER_PAETH = 4;

    protected PNG() {
        throw new UnsupportedOperationException();
    }
}

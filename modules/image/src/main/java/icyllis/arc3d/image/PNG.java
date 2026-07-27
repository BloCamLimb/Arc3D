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

public final class PNG {

    public static final byte[] FILE_SIGNATURE = {
            (byte) 137, (byte) 80, (byte) 78, (byte) 71,
            (byte) 13, (byte) 10, (byte) 26, (byte) 10
    };

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

    // Interlace methods
    public static final int INTERLACE_METHOD_NONE = 0;
    public static final int INTERLACE_METHOD_ADAM7 = 1;

    static final int[] adam7XOffset = { 0, 4, 0, 2, 0, 1, 0 };
    static final int[] adam7YOffset = { 0, 0, 4, 0, 2, 0, 1 };
    static final int[] adam7XStep = { 8, 8, 4, 4, 2, 2, 1 };
    static final int[] adam7YStep = { 8, 8, 8, 4, 4, 2, 2 };

    static int computePassWidth(int width, int pass) {
        return width <= adam7XOffset[pass]
                ? 0
                : (width - adam7XOffset[pass] + adam7XStep[pass] - 1) / adam7XStep[pass];
    }

    static int computePassHeight(int height, int pass) {
        return height <= adam7YOffset[pass]
                ? 0
                : (height - adam7YOffset[pass] + adam7YStep[pass] - 1) / adam7YStep[pass];
    }

    // Filter methods
    public static final int FILTER_METHOD_ADAPTIVE = 0;

    // Filter types
    public static final int FILTER_TYPE_NONE = 0;
    public static final int FILTER_TYPE_SUB = 1;
    public static final int FILTER_TYPE_UP = 2;
    public static final int FILTER_TYPE_AVERAGE = 3;
    public static final int FILTER_TYPE_PAETH = 4;

    // Compression methods
    public static final int COMPRESSION_METHOD_DEFLATE = 0; // zlib format

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

    public static boolean isSafeToCopyChunk(int chunkType) {
        return (chunkType & (1 << 5)) != 0;
    }

    /**
     * Short title or caption for the image.
     */
    public static final String TITLE_KEYWORD = "Title";
    /**
     * Name of the image creator.
     */
    public static final String AUTHOR_KEYWORD = "Author";
    /**
     * Description of the image.
     */
    public static final String DESCRIPTION_KEYWORD = "Description";
    /**
     * Copyright notice associated with the image.
     */
    public static final String COPYRIGHT_KEYWORD = "Copyright";
    /**
     * Time when the original image was created.
     * <p>
     * The date format SHOULD be in the RFC 3339 date-time format or in the
     * date format defined in section 5.2.14 of RFC 1123.
     * The RFC3339 date-time format is preferred.
     */
    public static final String CREATION_TIME_KEYWORD = "Creation Time";
    /**
     * Software used to create the image.
     */
    public static final String SOFTWARE_KEYWORD = "Software";
    /**
     * Legal disclaimer associated with the image.
     */
    public static final String DISCLAIMER_KEYWORD = "Disclaimer";
    /**
     * Warning regarding the nature or content of the image.
     */
    public static final String WARNING_KEYWORD = "Warning";
    /**
     * Device or source used to create the image.
     */
    public static final String SOURCE_KEYWORD = "Source";
    /**
     * Miscellaneous comment associated with the image.
     */
    public static final String COMMENT_KEYWORD = "Comment";
    /**
     * Extensible Metadata Platform (XMP) information encoded according
     * to the XMP specification.
     * <p>
     * The use of iTXt, with Compression Flag set to 0, and both
     * Language Tag and Translated Keyword set to the null string,
     * are recommended for XMP compliance.
     */
    public static final String XMP_KEYWORD = "XML:com.adobe.xmp";
    /**
     * Name of a collection to which the image belongs.
     * Multiple collection names may be stored using separate text chunks.
     */
    public static final String COLLECTION_KEYWORD = "Collection";

    private PNG() {
    }
}

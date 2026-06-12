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

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Function;

import static icyllis.arc3d.core.image.PNGHelpers.*;

public class PNGMetadata {

    // IHDR chunk
    public boolean IHDR;
    public int IHDR_width;
    public int IHDR_height;
    public int IHDR_bitDepth;
    public int IHDR_colorType;
    public int IHDR_compressionMethod;
    public int IHDR_filterMethod;
    public int IHDR_interlaceMethod; // 0 == none, 1 == adam7

    // PLTE chunk
    public boolean PLTE;
    public byte[] PLTE_entries; // rgb0 rgb1 ...

    // tRNS chunk
    public boolean tRNS;
    public int tRNS_gray;
    public int tRNS_red;
    public int tRNS_green;
    public int tRNS_blue;
    public byte[] tRNS_alpha; // May have fewer entries than PLTE_entries.

    // cHRM chunk
    public boolean cHRM;
    public int cHRM_whitePointX;
    public int cHRM_whitePointY;
    public int cHRM_redX;
    public int cHRM_redY;
    public int cHRM_greenX;
    public int cHRM_greenY;
    public int cHRM_blueX;
    public int cHRM_blueY;

    // gAMA chunk
    public boolean gAMA;
    public int gAMA_gamma;

    // iCCP chunk
    public boolean iCCP;
    public String iCCP_profileName;
    public int iCCP_compressionMethod;
    public byte[] iCCP_compressedProfile;

    // sBIT chunk
    public boolean sBIT;
    public int sBIT_grayBits;
    public int sBIT_redBits;
    public int sBIT_greenBits;
    public int sBIT_blueBits;
    public int sBIT_alphaBits;

    // sRGB chunk
    public boolean sRGB;
    public int sRGB_renderingIntent;

    // cICP chunk
    public boolean cICP;
    public int cICP_colorPrimaries;
    public int cICP_transferFunction;
    public int cICP_matrixCoefficients;
    public int cICP_videoFullRangeFlag;

    // tEXt chunk
    public ArrayList<Text> tEXt = new ArrayList<>();

    // zTXt chunk
    public ArrayList<Text> zTXt = new ArrayList<>();

    // iTXt chunk
    public ArrayList<Text> iTXt = new ArrayList<>();

    // bKGD chunk
    public boolean bKGD;
    public int bKGD_gray;
    public int bKGD_red;
    public int bKGD_green;
    public int bKGD_blue;
    public int bKGD_index;

    // hIST chunk
    public boolean hIST;
    public char[] hIST_histogram;

    // pHYs chunk
    public boolean pHYs;
    public int pHYs_pixelsPerUnitXAxis;
    public int pHYs_pixelsPerUnitYAxis;
    public int pHYs_unitSpecifier; // 0 == unknown, 1 == meter

    // sPLT chunk
    public ArrayList<SuggestedPalette> sPLT = new ArrayList<>();

    // eXIf chunk
    public boolean eXIf;
    public byte[] eXIf_data;

    public void checkIHDR(Function<String, ? extends IOException> ex) throws IOException {
        if (IHDR_width <= 0) {
            throw ex.apply("Image width <= 0!");
        }
        if (IHDR_height <= 0) {
            throw ex.apply("Image height <= 0!");
        }
        int bitDepth = IHDR_bitDepth;
        if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 &&
                bitDepth != 8 && bitDepth != 16) {
            throw ex.apply("Bit depth must be 1, 2, 4, 8, or 16!");
        }
        int colorType = IHDR_colorType;
        if (colorType != COLOR_TYPE_GRAYSCALE &&
                colorType != COLOR_TYPE_RGB &&
                colorType != COLOR_TYPE_PALETTE &&
                colorType != COLOR_TYPE_GRAY_ALPHA &&
                colorType != COLOR_TYPE_RGB_ALPHA) {
            throw ex.apply("Color type must be 0, 2, 3, 4, or 6!");
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
        if (IHDR_compressionMethod != 0) {
            throw ex.apply("Unknown compression method (not 0)!");
        }
        if (IHDR_filterMethod != 0) {
            throw ex.apply("Unknown filter method (not 0)!");
        }
        if (IHDR_interlaceMethod != 0 && IHDR_interlaceMethod != 1) {
            throw ex.apply("Unknown interlace method (not 0 or 1)!");
        }
    }

    public void checkPLTE(int numEntries, Function<String, ? extends IOException> ex) throws IOException {
        if (!IHDR) {
            throw ex.apply("No IHDR chunk");
        }
        if (numEntries == 0 ||
                numEntries > 256) {
            throw ex.apply("Invalid number of palette entries");
        }

        if (IHDR_colorType == COLOR_TYPE_PALETTE) {
            // PNG spec: The number of palette entries shall not exceed the range that can be represented
            // in the image bit depth (for example, 2^4 = 16 for a bit depth of 4). It is permissible to
            // have fewer entries than the bit depth would allow. In that case, any out-of-range pixel
            // value found in the image data is an error.
            if (numEntries > (1 << IHDR_bitDepth)) {
                throw ex.apply("Too many palette entries");
            }
        }
    }

    public int numChannels() {
        return PNGHelpers.numChannels(IHDR_colorType);
    }
}

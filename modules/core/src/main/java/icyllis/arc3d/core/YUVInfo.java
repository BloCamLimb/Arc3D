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

package icyllis.arc3d.core;

/**
 * YUV are formats based on a luma component (Y) and two chroma components (UV).
 * A YUV space is based on an RGB color space, which is represented by
 * {@link ColorSpace}. This structure provides enough information for
 * the conversion between YUV and RGB data.
 */
public class YUVInfo {

    /**
     * Chroma subsampling.
     */
    public static final int
            SUBSAMPLING_UNKNOWN = 0,
            SUBSAMPLING_444 = 1,
            SUBSAMPLING_440 = 2,
            SUBSAMPLING_422 = 3,
            SUBSAMPLING_420 = 4,
            SUBSAMPLING_411 = 5,
            SUBSAMPLING_410 = 6,
            SUBSAMPLING_400 = 7; // Y only

    public static final int LAYOUT_UNKNOWN = 0;
    /**
     * Plane 0: Y, Plane 1: U,  Plane 2: V
     * <p>
     * e.g. yuv420p
     */
    public static final int LAYOUT_Y_U_V = 1;
    /**
     * Plane 0: Y, Plane 1: V,  Plane 2: U
     * <p>
     * e.g. yv12
     */
    public static final int LAYOUT_Y_V_U = 2;
    /**
     * Plane 0: Y, Plane 1: UV interleaved
     * <p>
     * e.g. nv12
     */
    public static final int LAYOUT_Y_UV = 3;
    /**
     * Plane 0: Y, Plane 1: VU interleaved
     * <p>
     * e.g. nv21
     */
    public static final int LAYOUT_Y_VU = 4;
    /**
     * Plane 0: YUV
     * <p>
     * e.g. yuyv422, y410
     */
    public static final int LAYOUT_YUV = 5;
    /**
     * Plane 0: UYV
     * <p>
     * e.g. uyvy422
     */
    public static final int LAYOUT_UYV = 6;

    /**
     * Chroma siting, also known as chroma 4:2:0 sample location type.
     * These values match the ones defined by ISO/IEC 23091-2_2019 subclause 8.7.
     */
    //@formatter:off
    public static final int
            SITING_LEFT       = 0, // H.264
            SITING_CENTER     = 1, // JPEG
            SITING_TOPLEFT    = 2, // H.265 AV1
            SITING_TOP        = 3,
            SITING_BOTTOMLEFT = 4,
            SITING_BOTTOM     = 5;
    //@formatter:on

    /**
     * Matrix coefficients for YUV<->RGB conversion, also known as YUV color space.
     * These values match the ones defined by ISO/IEC 23091-2_2019 subclause 8.3.
     */
    //@formatter:off
    public static final int
            MATRIX_COEFFICIENTS_IDENTITY    = 0,  // may be RGB, GBR, XYZ, YZX
            MATRIX_COEFFICIENTS_BT709       = 1,
            MATRIX_COEFFICIENTS_UNSPECIFIED = 2,
            // Value 3 is reserved.
            MATRIX_COEFFICIENTS_FCC         = 4,
            MATRIX_COEFFICIENTS_BT470BG     = 5,
            MATRIX_COEFFICIENTS_SMPTE170M   = 6,
            MATRIX_COEFFICIENTS_SMPTE240M   = 7,
            MATRIX_COEFFICIENTS_YCGCO       = 8,
            MATRIX_COEFFICIENTS_BT2020_NCL  = 9,  // ITU-R BT2020 non-constant luminance system
            MATRIX_COEFFICIENTS_BT2020_CL   = 10, // ITU-R BT2020 constant luminance system
            MATRIX_COEFFICIENTS_SMPTE2085   = 11; // Y'D'zD'x
    //@formatter:on

    private final int width;
    private final int height;
    private final byte subsampling;
    private final byte layout;
    private final byte siting;
    private final byte matrix;

    private final byte bitDepth;
    private final boolean isFullRange;

    public YUVInfo(int width, int height,
                   int subsampling, int layout,
                   int siting, int matrix,
                   int bitDepth, boolean isFullRange) {
        this.width = width;
        this.height = height;
        this.subsampling = (byte) subsampling;
        this.layout = (byte) layout;
        this.siting = (byte) siting;
        this.matrix = (byte) matrix;
        this.bitDepth = (byte) bitDepth;
        this.isFullRange = isFullRange;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int subsampling() {
        return subsampling;
    }

    public int layout() {
        return layout;
    }

    public int siting() {
        return siting;
    }

    public int matrix() {
        return matrix;
    }

    public int bitDepth() {
        return bitDepth;
    }

    public boolean isFullRange() {
        return isFullRange;
    }
}

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

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static icyllis.arc3d.core.ColorSpace.*;

/**
 * <p>List of common, named color spaces.</p>
 *
 * <p>The properties of each color space are described below (see {@link #SRGB sRGB}
 * for instance). When applicable, the color gamut of each color space is compared
 * to the color gamut of sRGB using a CIE 1931 xy chromaticity diagram. This diagram
 * shows the location of the color space's primaries and white point.</p>
 */
public final class ColorSpaces {

    /**
     * <p>{@link ColorSpaceRGB RGB} color space sRGB standardized as IEC 61966-2.1:1999.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.640</td><td>0.300</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.330</td><td>0.600</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">sRGB IEC61966-2.1</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{sRGB} = \begin{cases} 12.92 \times C_{linear} & C_{linear} \lt 0.0031308 \\\
     *             1.055 \times C_{linear}^{\frac{1}{2.4}} - 0.055 & C_{linear} \ge 0.0031308 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{sRGB}}{12.92} & C_{sRGB} \lt 0.04045 \\\
     *             \left( \frac{C_{sRGB} + 0.055}{1.055} \right) ^{2.4} & C_{sRGB} \ge 0.04045 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_srgb.png" />
     *     <figcaption style="text-align: center;">sRGB</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB SRGB = new ColorSpaceRGB(
            "sRGB IEC61966-2.1",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
            0
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space sRGB standardized as IEC 61966-2.1:1999.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.640</td><td>0.300</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.330</td><td>0.600</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">sRGB IEC61966-2.1 (Linear)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{sRGB} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{sRGB}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_srgb.png" />
     *     <figcaption style="text-align: center;">sRGB</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB LINEAR_SRGB = new ColorSpaceRGB(
            "sRGB IEC61966-2.1 (Linear)",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            0.0f, 1.0f,
            1
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space scRGB-nl standardized as IEC 61966-2-2:2003.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.640</td><td>0.300</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.330</td><td>0.600</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">scRGB-nl IEC 61966-2-2:2003</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{scRGB} = \begin{cases} sign(C_{linear}) 12.92 \times \left| C_{linear} \right| &
     *                      \left| C_{linear} \right| \lt 0.0031308 \\\
     *             sign(C_{linear}) 1.055 \times \left| C_{linear} \right| ^{\frac{1}{2.4}} - 0.055 &
     *                      \left| C_{linear} \right| \ge 0.0031308 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}sign(C_{scRGB}) \frac{\left| C_{scRGB} \right|}{12.92} &
     *                  \left| C_{scRGB} \right| \lt 0.04045 \\\
     *             sign(C_{scRGB}) \left( \frac{\left| C_{scRGB} \right| + 0.055}{1.055} \right) ^{2.4} &
     *                  \left| C_{scRGB} \right| \ge 0.04045 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([-0.799..2.399[\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_scrgb.png" />
     *     <figcaption style="text-align: center;">Extended sRGB (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB EXTENDED_SRGB = new ColorSpaceRGB(
            "scRGB-nl IEC 61966-2-2:2003",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            x -> absRcpResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
            x -> absResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
            -0.799f, 2.399f,
            ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
            2
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space scRGB standardized as IEC 61966-2-2:2003.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.640</td><td>0.300</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.330</td><td>0.600</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">scRGB IEC 61966-2-2:2003</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{scRGB} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{scRGB}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([-0.5..7.499[\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_scrgb.png" />
     *     <figcaption style="text-align: center;">Extended sRGB (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB LINEAR_EXTENDED_SRGB = new ColorSpaceRGB(
            "scRGB IEC 61966-2-2:2003",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            -0.5f, 7.499f,
            3
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space BT.709 standardized as Rec. ITU-R BT.709-5.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.640</td><td>0.300</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.330</td><td>0.600</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Rec. ITU-R BT.709-5</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{BT709} = \begin{cases} 4.5 \times C_{linear} & C_{linear} \lt 0.018 \\\
     *             1.099 \times C_{linear}^{\frac{1}{2.2}} - 0.099 & C_{linear} \ge 0.018 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{BT709}}{4.5} & C_{BT709} \lt 0.081 \\\
     *             \left( \frac{C_{BT709} + 0.099}{1.099} \right) ^{2.2} & C_{BT709} \ge 0.081 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_bt709.png" />
     *     <figcaption style="text-align: center;">BT.709</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB BT709 = new ColorSpaceRGB(
            "Rec. ITU-R BT.709-5",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
            4
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space BT.2020 standardized as Rec. ITU-R BT.2020-1.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.708</td><td>0.170</td><td>0.131</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.292</td><td>0.797</td><td>0.046</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Rec. ITU-R BT.2020-1</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{BT2020} = \begin{cases} 4.5 \times C_{linear} & C_{linear} \lt 0.0181 \\\
     *             1.0993 \times C_{linear}^{\frac{1}{2.2}} - 0.0993 & C_{linear} \ge 0.0181 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{BT2020}}{4.5} & C_{BT2020} \lt 0.08145 \\\
     *             \left( \frac{C_{BT2020} + 0.0993}{1.0993} \right) ^{2.2} & C_{BT2020} \ge 0.08145 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_bt2020.png" />
     *     <figcaption style="text-align: center;">BT.2020 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB BT2020 = new ColorSpaceRGB(
            "Rec. ITU-R BT.2020-1",
            BT2020_PRIMARIES,
            ILLUMINANT_D65,
            null,
            new ColorSpaceRGB.TransferParameters(1 / 1.0993, 0.0993 / 1.0993, 1 / 4.5, 0.08145, 1 / 0.45),
            5
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space DCI-P3 standardized as SMPTE RP 431-2-2007.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.680</td><td>0.265</td><td>0.150</td><td>0.314</td></tr>
     *     <tr><td>y</td><td>0.320</td><td>0.690</td><td>0.060</td><td>0.351</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">SMPTE RP 431-2-2007 DCI (P3)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">N/A</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{P3} = C_{linear}^{\frac{1}{2.6}}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{P3}^{2.6}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_dci_p3.png" />
     *     <figcaption style="text-align: center;">DCI-P3 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB DCI_P3 = new ColorSpaceRGB(
            "SMPTE RP 431-2-2007 DCI (P3)",
            DCI_P3_PRIMARIES,
            new float[]{0.314f, 0.351f},
            2.6,
            0.0f, 1.0f,
            6
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space Display P3 based on SMPTE RP 431-2-2007 and IEC
     * 61966-2.1:1999.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.680</td><td>0.265</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.320</td><td>0.690</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Display P3</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{DisplayP3} = \begin{cases} 12.92 \times C_{linear} & C_{linear} \lt 0.0030186 \\\
     *             1.055 \times C_{linear}^{\frac{1}{2.4}} - 0.055 & C_{linear} \ge 0.0030186 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{DisplayP3}}{12.92} & C_{sRGB} \lt 0.04045 \\\
     *             \left( \frac{C_{DisplayP3} + 0.055}{1.055} \right) ^{2.4} & C_{sRGB} \ge 0.04045 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_display_p3.png" />
     *     <figcaption style="text-align: center;">Display P3 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB DISPLAY_P3 = new ColorSpaceRGB(
            "Display P3",
            DCI_P3_PRIMARIES,
            ILLUMINANT_D65,
            null,
            ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
            7
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space NTSC, 1953 standard.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.67</td><td>0.21</td><td>0.14</td><td>0.310</td></tr>
     *     <tr><td>y</td><td>0.33</td><td>0.71</td><td>0.08</td><td>0.316</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">NTSC (1953)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">C</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{BT709} = \begin{cases} 4.5 \times C_{linear} & C_{linear} \lt 0.018 \\\
     *             1.099 \times C_{linear}^{\frac{1}{2.2}} - 0.099 & C_{linear} \ge 0.018 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{BT709}}{4.5} & C_{BT709} \lt 0.081 \\\
     *             \left( \frac{C_{BT709} + 0.099}{1.099} \right) ^{2.2} & C_{BT709} \ge 0.081 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_ntsc_1953.png" />
     *     <figcaption style="text-align: center;">NTSC 1953 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB NTSC_1953 = new ColorSpaceRGB(
            "NTSC (1953)",
            NTSC_1953_PRIMARIES,
            ILLUMINANT_C,
            null,
            ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
            8
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space SMPTE C.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.630</td><td>0.310</td><td>0.155</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.340</td><td>0.595</td><td>0.070</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">SMPTE-C RGB</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{BT709} = \begin{cases} 4.5 \times C_{linear} & C_{linear} \lt 0.018 \\\
     *             1.099 \times C_{linear}^{\frac{1}{2.2}} - 0.099 & C_{linear} \ge 0.018 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{BT709}}{4.5} & C_{BT709} \lt 0.081 \\\
     *             \left( \frac{C_{BT709} + 0.099}{1.099} \right) ^{2.2} & C_{BT709} \ge 0.081 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_smpte_c.png" />
     *     <figcaption style="text-align: center;">SMPTE-C (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB SMPTE_C = new ColorSpaceRGB(
            "SMPTE-C RGB",
            new float[]{0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f},
            ILLUMINANT_D65,
            null,
            ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
            9
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space Adobe RGB (1998).</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.64</td><td>0.21</td><td>0.15</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.33</td><td>0.71</td><td>0.06</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Adobe RGB (1998)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{RGB} = C_{linear}^{\frac{1}{2.2}}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{RGB}^{2.2}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_adobe_rgb.png" />
     *     <figcaption style="text-align: center;">Adobe RGB (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB ADOBE_RGB = new ColorSpaceRGB(
            "Adobe RGB (1998)",
            new float[]{0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f},
            ILLUMINANT_D65,
            2.2,
            0.0f, 1.0f,
            10
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space ProPhoto RGB standardized as ROMM RGB ISO 22028-2:2013.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.7347</td><td>0.1596</td><td>0.0366</td><td>0.3457</td></tr>
     *     <tr><td>y</td><td>0.2653</td><td>0.8404</td><td>0.0001</td><td>0.3585</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">ROMM RGB ISO 22028-2:2013</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{ROMM} = \begin{cases} 16 \times C_{linear} & C_{linear} \lt 0.001953 \\\
     *             C_{linear}^{\frac{1}{1.8}} & C_{linear} \ge 0.001953 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(\begin{equation}
     *             C_{linear} = \begin{cases}\frac{C_{ROMM}}{16} & C_{ROMM} \lt 0.031248 \\\
     *             C_{ROMM}^{1.8} & C_{ROMM} \ge 0.031248 \end{cases}
     *             \end{equation}\)
     *         </td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_pro_photo_rgb.png" />
     *     <figcaption style="text-align: center;">ProPhoto RGB (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB PRO_PHOTO_RGB = new ColorSpaceRGB(
            "ROMM RGB ISO 22028-2:2013",
            new float[]{0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f},
            ILLUMINANT_D50,
            null,
            new ColorSpaceRGB.TransferParameters(1.0, 0.0, 1 / 16.0, 0.031248, 1.8),
            11
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space ACES standardized as SMPTE ST 2065-1:2012.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.73470</td><td>0.00000</td><td>0.00010</td><td>0.32168</td></tr>
     *     <tr><td>y</td><td>0.26530</td><td>1.00000</td><td>-0.07700</td><td>0.33767</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">SMPTE ST 2065-1:2012 ACES</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D60</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{ACES} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{ACES}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([-65504.0, 65504.0]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_aces.png" />
     *     <figcaption style="text-align: center;">ACES (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB ACES = new ColorSpaceRGB(
            "SMPTE ST 2065-1:2012 ACES",
            new float[]{0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.0770f},
            ILLUMINANT_D60,
            1.0,
            -65504.0f, 65504.0f,
            12
    );
    /**
     * <p>{@link ColorSpaceRGB RGB} color space ACEScg standardized as Academy S-2014-004.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.713</td><td>0.165</td><td>0.128</td><td>0.32168</td></tr>
     *     <tr><td>y</td><td>0.293</td><td>0.830</td><td>0.044</td><td>0.33767</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Academy S-2014-004 ACEScg</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D60</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{ACEScg} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{ACEScg}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([-65504.0, 65504.0]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_acescg.png" />
     *     <figcaption style="text-align: center;">ACEScg (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final ColorSpaceRGB ACESCG = new ColorSpaceRGB(
            "Academy S-2014-004 ACEScg",
            new float[]{0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f},
            ILLUMINANT_D60,
            1.0,
            -65504.0f, 65504.0f,
            13
    );
    /**
     * <p>{@link ColorSpaceXYZ XYZ} color space CIE XYZ. This color space assumes standard
     * illuminant D50 as its white point.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D50)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
     *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
     * </table>
     */
    public static final ColorSpaceXYZ CIE_XYZ_D50 = new ColorSpaceXYZ(
            "CIE 1931 XYZ (D50)",
            ILLUMINANT_D50,
            14
    );
    /**
     * <p>{@link ColorSpaceXYZ XYZ} color space CIE XYZ. This color space assumes standard
     * illuminant D65 as its white point.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D65)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
     * </table>
     */
    public static final ColorSpaceXYZ CIE_XYZ_D65 = new ColorSpaceXYZ(
            "CIE 1931 XYZ (D65)",
            ILLUMINANT_D65,
            15
    );
    /**
     * <p>{@link ColorSpace#MODEL_LAB Lab} color space CIE L*a*b*. This color space uses CIE XYZ D50
     * as a profile conversion space.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Generic L*a*b*</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
     *     <tr><td>Range</td><td colspan="4">\(L: [0.0, 100.0], a: [-128, 128], b: [-128, 128]\)</td></tr>
     * </table>
     */
    public static final ColorSpace CIE_LAB = new ColorSpaceLab(
            "Generic L*a*b*",
            16
    );
    /**
     * <p>{@link ColorSpace#MODEL_LAB Lab} color space OkLab standardized as
     * OkLab</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Oklab</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Range</td>
     *         <td colspan="4">\(L: `[0.0, 1.0]`, a: `[-2, 2]`, b: `[-2, 2]`\)</td>
     *     </tr>
     * </table>
     */
    public static final ColorSpace OK_LAB = new ColorSpaceOklab(
            "Oklab",
            17
    );

    static final ColorSpace[] sNamedColorSpaces = new ColorSpace[18];

    static {
        sNamedColorSpaces[SRGB.getId()] = SRGB;
        sNamedColorSpaces[LINEAR_SRGB.getId()] = LINEAR_SRGB;
        sNamedColorSpaces[EXTENDED_SRGB.getId()] = EXTENDED_SRGB;
        sNamedColorSpaces[LINEAR_EXTENDED_SRGB.getId()] = LINEAR_EXTENDED_SRGB;
        sNamedColorSpaces[BT709.getId()] = BT709;
        sNamedColorSpaces[BT2020.getId()] = BT2020;
        sNamedColorSpaces[DCI_P3.getId()] = DCI_P3;
        sNamedColorSpaces[DISPLAY_P3.getId()] = DISPLAY_P3;
        sNamedColorSpaces[NTSC_1953.getId()] = NTSC_1953;
        sNamedColorSpaces[SMPTE_C.getId()] = SMPTE_C;
        sNamedColorSpaces[ADOBE_RGB.getId()] = ADOBE_RGB;
        sNamedColorSpaces[PRO_PHOTO_RGB.getId()] = PRO_PHOTO_RGB;
        sNamedColorSpaces[ACES.getId()] = ACES;
        sNamedColorSpaces[ACESCG.getId()] = ACESCG;
        sNamedColorSpaces[CIE_XYZ_D50.getId()] = CIE_XYZ_D50;
        sNamedColorSpaces[CIE_XYZ_D65.getId()] = CIE_XYZ_D65;
        sNamedColorSpaces[CIE_LAB.getId()] = CIE_LAB;
        sNamedColorSpaces[OK_LAB.getId()] = OK_LAB;
    }

    @SuppressWarnings("Java9CollectionFactory")
    public static @NonNull @Unmodifiable List<ColorSpace> getNamedColorSpaces() {
        return Collections.unmodifiableList(Arrays.asList(sNamedColorSpaces));
    }
}

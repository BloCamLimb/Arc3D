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
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
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
     * <p>{@link RGBColorSpace RGB} color space sRGB standardized as IEC 61966-2.1:1999.</p>
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
    public static final RGBColorSpace SRGB = new RGBColorSpace(
            "sRGB IEC61966-2.1",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SRGB,
            0
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space sRGB standardized as IEC 61966-2.1:1999.</p>
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
    public static final RGBColorSpace LINEAR_SRGB = new RGBColorSpace(
            "sRGB IEC61966-2.1 (Linear)",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            0.0f, 1.0f,
            1
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space scRGB-nl standardized as IEC 61966-2-2:2003.</p>
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
    public static final RGBColorSpace EXTENDED_SRGB = new RGBColorSpace(
            "scRGB-nl IEC 61966-2-2:2003",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            -0.799f, 2.399f,
            TransferFunction.SRGB,
            2
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space scRGB standardized as IEC 61966-2-2:2003.</p>
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
    public static final RGBColorSpace LINEAR_EXTENDED_SRGB = new RGBColorSpace(
            "scRGB IEC 61966-2-2:2003",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            -0.5f, 7.499f,
            3
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space BT.709 standardized as Rec. ITU-R BT.709-5.</p>
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
    public static final RGBColorSpace BT709 = new RGBColorSpace(
            "Rec. ITU-R BT.709-5",
            SRGB_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SMPTE_170M,
            4
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space BT.2020 standardized as Rec. ITU-R BT.2020-1.</p>
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
    public static final RGBColorSpace BT2020 = new RGBColorSpace(
            "Rec. ITU-R BT.2020-1",
            BT2020_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SMPTE_170M,
            5
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space BT.2020 standardized as Rec. ITU-R BT.2020-1.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.708</td><td>0.170</td><td>0.131</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.292</td><td>0.797</td><td>0.046</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Rec. ITU-R BT.2020-1 (Linear)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{BT2020} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{BT2020}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_bt2020.png" />
     *     <figcaption style="text-align: center;">BT.2020 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final RGBColorSpace LINEAR_BT2020 = new RGBColorSpace(
            "Rec. ITU-R BT.2020-1 (Linear)",
            BT2020_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            0.0f, 1.0f,
            18
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space DCI-P3 standardized as SMPTE RP 431-2-2007.</p>
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
    public static final RGBColorSpace DCI_P3 = new RGBColorSpace(
            "SMPTE RP 431-2-2007 DCI (P3)",
            DCI_P3_PRIMARIES,
            ILLUMINANT_DCI,
            2.6,
            0.0f, 1.0f,
            6
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space Display P3 based on SMPTE RP 431-2-2007 and IEC
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
    public static final RGBColorSpace DISPLAY_P3 = new RGBColorSpace(
            "Display P3",
            DCI_P3_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SRGB,
            7
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space Display P3 based on SMPTE RP 431-2-2007 and IEC
     * 61966-2.1:1999.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.680</td><td>0.265</td><td>0.150</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.320</td><td>0.690</td><td>0.060</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Display P3 (Linear)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Opto-electronic transfer function (OETF)</td>
     *         <td colspan="4">\(C_{DisplayP3} = C_{linear}\)</td>
     *     </tr>
     *     <tr>
     *         <td>Electro-optical transfer function (EOTF)</td>
     *         <td colspan="4">\(C_{linear} = C_{DisplayP3}\)</td>
     *     </tr>
     *     <tr><td>Range</td><td colspan="4">\([0..1]\)</td></tr>
     * </table>
     * <p>
     *     <img style="display: block; margin: 0 auto;" src="https://developer.android
     *     .com/reference/android/images/graphics/colorspace_display_p3.png" />
     *     <figcaption style="text-align: center;">Display P3 (orange) vs sRGB (white)</figcaption>
     * </p>
     */
    public static final RGBColorSpace LINEAR_DISPLAY_P3 = new RGBColorSpace(
            "Display P3 (Linear)",
            DCI_P3_PRIMARIES,
            ILLUMINANT_D65,
            1.0,
            0.0f, 1.0f,
            19
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space NTSC, 1953 standard.</p>
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
    public static final RGBColorSpace NTSC_1953 = new RGBColorSpace(
            "NTSC (1953)",
            NTSC_1953_PRIMARIES,
            ILLUMINANT_C,
            null,
            TransferFunction.SMPTE_170M,
            8
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space BT.470 B/G, or BT.601 625-line.</p>
     * <table summary="Color space definition">
     *     <tr>
     *         <th>Chromaticity</th><th>Red</th><th>Green</th><th>Blue</th><th>White point</th>
     *     </tr>
     *     <tr><td>x</td><td>0.64</td><td>0.29</td><td>0.15</td><td>0.3127</td></tr>
     *     <tr><td>y</td><td>0.33</td><td>0.60</td><td>0.06</td><td>0.3290</td></tr>
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">BT.470 B/G (PAL)</td></tr>
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
     */
    public static final RGBColorSpace BT470_BG = new RGBColorSpace(
            "BT.470 B/G (PAL)",
            BT470_BG_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SMPTE_170M,
            20
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space SMPTE C, or BT.601 525-line.</p>
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
    public static final RGBColorSpace SMPTE_C = new RGBColorSpace(
            "SMPTE-C RGB",
            SMPTE_C_PRIMARIES,
            ILLUMINANT_D65,
            null,
            TransferFunction.SMPTE_170M,
            9
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space Adobe RGB (1998).</p>
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
    public static final RGBColorSpace ADOBE_RGB = new RGBColorSpace(
            "Adobe RGB (1998)",
            new float[]{0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f},
            ILLUMINANT_D65,
            2.2,
            0.0f, 1.0f,
            10
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space ProPhoto RGB standardized as ROMM RGB ISO 22028-2:2013.</p>
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
     *             C_{linear} = \begin{cases}\frac{C_{ROMM}}{16} & C_{ROMM} \lt 0.03125 \\\
     *             C_{ROMM}^{1.8} & C_{ROMM} \ge 0.03125 \end{cases}
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
    public static final RGBColorSpace PRO_PHOTO_RGB = new RGBColorSpace(
            "ROMM RGB ISO 22028-2:2013",
            new float[]{0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f},
            ILLUMINANT_D50,
            null,
            new TransferFunction(1.0, 0.0, 1 / 16.0, 0.03125, 1.8),
            11
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space ACES standardized as SMPTE ST 2065-1:2012.</p>
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
    public static final RGBColorSpace ACES = new RGBColorSpace(
            "SMPTE ST 2065-1:2012 ACES",
            new float[]{0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.07700f},
            ILLUMINANT_D60,
            1.0,
            -65504.0f, 65504.0f,
            12
    );
    /**
     * <p>{@link RGBColorSpace RGB} color space ACEScg standardized as Academy S-2014-004.</p>
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
    public static final RGBColorSpace ACESCG = new RGBColorSpace(
            "Academy S-2014-004 ACEScg",
            new float[]{0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f},
            ILLUMINANT_D60,
            1.0,
            -65504.0f, 65504.0f,
            13
    );
    /**
     * <p>{@link ColorSpace#MODEL_XYZ XYZ} color space CIE XYZ. This color space assumes standard
     * illuminant D50 as its white point.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D50)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
     *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
     * </table>
     */
    public static final ColorSpace CIE_XYZ_D50 = new XYZColorSpace(
            "CIE 1931 XYZ (D50)",
            ILLUMINANT_D50,
            14
    );
    /**
     * <p>{@link ColorSpace#MODEL_XYZ XYZ} color space CIE XYZ. This color space assumes standard
     * illuminant D65 as its white point.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D65)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
     * </table>
     */
    public static final ColorSpace CIE_XYZ_D65 = new XYZColorSpace(
            "CIE 1931 XYZ (D65)",
            ILLUMINANT_D65,
            16
    );
    /**
     * <p>{@link ColorSpace#MODEL_XYZ XYZ} color space CIE XYZ. This color space assumes standard
     * illuminant E as its white point.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (E)</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">E</td></tr>
     *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
     * </table>
     */
    public static final ColorSpace CIE_XYZ_E = new XYZColorSpace(
            "CIE 1931 XYZ (E)",
            ILLUMINANT_E,
            21
    );
    /**
     * <p>{@link ColorSpace#MODEL_LAB Lab} color space CIE L*a*b*. This color space uses CIE XYZ D50
     * as a profile conversion space.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">CIE 1976 L*a*b*</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
     *     <tr><td>Range</td><td colspan="4">\(L: [0.0, 100.0], a: [-128, 128], b: [-128, 128]\)</td></tr>
     * </table>
     */
    public static final ColorSpace CIE_LAB = new LabColorSpace(
            "CIE 1976 L*a*b*",
            15
    );
    /**
     * <p>{@link ColorSpace#MODEL_LAB Lab} color space OkLab standardized as
     * OkLab.</p>
     * <table summary="Color space definition">
     *     <tr><th>Property</th><th colspan="4">Value</th></tr>
     *     <tr><td>Name</td><td colspan="4">Oklab</td></tr>
     *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
     *     <tr>
     *         <td>Range</td>
     *         <td colspan="4">\(L: `[0.0, 1.0]`, a: `[-0.5, 0.5]`, b: `[-0.5, 0.5]`\)</td>
     *     </tr>
     * </table>
     */
    public static final ColorSpace OK_LAB = new OkLabColorSpace(
            "Oklab",
            17
    );

    /**
     * Try to return a ColorSpace for the given coding-independent code points
     * for video signal type identification, from ITU-T H.273.
     * <p>
     * Defined constants can be found in {@link Color}.
     * The return value would be {@link RGBColorSpace} in most cases.
     * If the code points are reserved, or not supported by Arc3D, null is returned.
     *
     * @param primaries the color primaries code point
     * @param transfer  the transfer characteristics code point
     * @param useBT1886 whether to use BT.1886 EOTF
     * @return a color space representing the CICP, or null if not supported
     */
    public static @Nullable ColorSpace fromCICP(int primaries, int transfer, boolean useBT1886) {
        float[] pri;
        float[] wp;
        switch (primaries) {
            case Color.COLOR_PRIMARIES_BT709,
                 Color.COLOR_PRIMARIES_UNSPECIFIED -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_BT709,
                         Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                         Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                        if (!useBT1886) {
                            return BT709;
                        }
                    }
                    case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                        // there's no difference between non-extended and extended version
                        return LINEAR_SRGB;
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                        // there's no difference between non-extended and extended version
                        return BT709;
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1 -> {
                        // there's no difference between non-extended and extended version
                        return SRGB;
                    }
                }

                pri = SRGB_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            case Color.COLOR_PRIMARIES_BT470M -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_BT709,
                         Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                         Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                        if (!useBT1886) {
                            return NTSC_1953;
                        }
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                        // there's no difference between non-extended and extended version
                        return NTSC_1953;
                    }
                }

                pri = NTSC_1953_PRIMARIES;
                wp = ILLUMINANT_C;
            }
            case Color.COLOR_PRIMARIES_BT470BG -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_BT709,
                         Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                         Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                        if (!useBT1886) {
                            return BT470_BG;
                        }
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                        // there's no difference between non-extended and extended version
                        return BT470_BG;
                    }
                }

                pri = BT470_BG_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            case Color.COLOR_PRIMARIES_SMPTE170M,
                 Color.COLOR_PRIMARIES_SMPTE240M -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_BT709,
                         Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                         Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                        if (!useBT1886) {
                            return SMPTE_C;
                        }
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                        // there's no difference between non-extended and extended version
                        return SMPTE_C;
                    }
                }

                pri = SMPTE_C_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            case Color.COLOR_PRIMARIES_GENERIC_FILM -> {

                pri = new float[]{0.681f, 0.319f, 0.243f, 0.692f, 0.145f, 0.049f};
                wp = ILLUMINANT_C;
            }
            case Color.COLOR_PRIMARIES_BT2020 -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_BT709,
                         Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                         Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                         Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                        if (!useBT1886) {
                            return BT2020;
                        }
                    }
                    case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                        // there's no difference between non-extended and extended version
                        return LINEAR_BT2020;
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                        // there's no difference between non-extended and extended version
                        return BT2020;
                    }
                }

                pri = BT2020_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            case Color.COLOR_PRIMARIES_SMPTE428 -> {
                if (transfer == Color.TRANSFER_CHARACTERISTICS_LINEAR) {
                    return CIE_XYZ_E;
                }

                pri = XYZ_PRIMARIES;
                wp = ILLUMINANT_E;
            }
            case Color.COLOR_PRIMARIES_SMPTE431 -> {
                if (transfer == Color.TRANSFER_CHARACTERISTICS_SMPTE428) {
                    return DCI_P3;
                }

                pri = DCI_P3_PRIMARIES;
                wp = ILLUMINANT_DCI;
            }
            case Color.COLOR_PRIMARIES_SMPTE432 -> {
                switch (transfer) {
                    case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                        // there's no difference between non-extended and extended version
                        return LINEAR_DISPLAY_P3;
                    }
                    case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1 -> {
                        // there's no difference between non-extended and extended version
                        return DISPLAY_P3;
                    }
                }

                pri = DCI_P3_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            case Color.COLOR_PRIMARIES_EBU3213 -> {

                pri = EBU3213_PRIMARIES;
                wp = ILLUMINANT_D65;
            }
            default -> {
                return null;
            }
        }

        TransferFunction tf = TransferFunction.fromCICP(transfer, useBT1886);
        if (tf == null) {
            return null;
        }
        String name = nameCICP(primaries, transfer, useBT1886);

        return new RGBColorSpace(name != null ? name : "Some RGB", pri, wp, tf);
    }

    public static @Nullable String nameCICP(int primaries, int transfer, boolean useBT1886) {
        String pn;
        switch (primaries) {
            case Color.COLOR_PRIMARIES_BT709,
                 Color.COLOR_PRIMARIES_UNSPECIFIED -> {
                pn = "BT709";
            }
            case Color.COLOR_PRIMARIES_BT470M -> {
                pn = "BT470M";
            }
            case Color.COLOR_PRIMARIES_BT470BG -> {
                pn = "BT470BG";
            }
            case Color.COLOR_PRIMARIES_SMPTE170M,
                 Color.COLOR_PRIMARIES_SMPTE240M -> {
                pn = "SMPTE170M";
            }
            case Color.COLOR_PRIMARIES_GENERIC_FILM -> {
                pn = "FILM";
            }
            case Color.COLOR_PRIMARIES_BT2020 -> {
                pn = "BT2020";
            }
            case Color.COLOR_PRIMARIES_SMPTE428 -> {
                pn = "XYZ";
            }
            case Color.COLOR_PRIMARIES_SMPTE431 -> {
                pn = "P3-DCI";
            }
            case Color.COLOR_PRIMARIES_SMPTE432 -> {
                pn = "P3-D65";
            }
            case Color.COLOR_PRIMARIES_EBU3213 -> {
                pn = "EBU3213";
            }
            default -> {
                return null;
            }
        }
        String tn;
        switch (transfer) {
            case Color.TRANSFER_CHARACTERISTICS_BT709,
                 Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                 Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                tn = useBT1886 ? "2.4" : "SMPTE170M";
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470M -> {
                tn = "2.2";
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470BG -> {
                tn = "2.8";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE240M -> {
                tn = "SMPTE240M";
            }
            case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                tn = "Linear";
            }
            case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                tn = "SMPTE170M";
            }
            case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1 -> {
                tn = "sRGB";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE2084 -> {
                tn = "PQ";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE428 -> {
                tn = "2.6";
            }
            case Color.TRANSFER_CHARACTERISTICS_ARIB_STD_B67 -> {
                tn = "HLG";
            }
            default -> {
                return null;
            }
        }
        return pn + " primaries with " + tn + " transfer";
    }

    public static @Nullable String namePrimaries(@Size(6) float @NonNull [] primaries,
                                                 @Size(2) float @NonNull [] whitePoint) {
        String pn;
        if (compare(primaries, SRGB_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "BT709";
        } else if (compare(primaries, NTSC_1953_PRIMARIES) && compare(whitePoint, ILLUMINANT_C)) {
            pn = "BT470M";
        } else if (compare(primaries, BT470_BG_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "BT470BG";
        } else if (compare(primaries, SMPTE_C_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "SMPTE170M";
        } else if (compare(primaries, DCI_P3_PRIMARIES) && compare(whitePoint, ILLUMINANT_DCI)) {
            pn = "P3-DCI";
        } else if (compare(primaries, DCI_P3_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "P3-D65";
        } else if (compare(primaries, BT2020_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "BT2020";
        } else if (compare(primaries, XYZ_PRIMARIES) && compare(whitePoint, ILLUMINANT_E)) {
            pn = "XYZ";
        } else if (compare(primaries, EBU3213_PRIMARIES) && compare(whitePoint, ILLUMINANT_D65)) {
            pn = "EBU3213";
        } else {
            pn = null;
        }
        return pn;
    }

    public static @Nullable String nameTransfer(@NonNull TransferFunction tf) {
        String tn;
        if (TransferFunction.compare(tf, TransferFunction.SRGB)) {
            tn = "sRGB";
        } else if (TransferFunction.compare(tf, TransferFunction.SMPTE_170M)) {
            tn = "SMPTE170M";
        } else if (TransferFunction.compare(tf, TransferFunction.SMPTE_240M)) {
            tn = "SMPTE240M";
        } else if (TransferFunction.compare(tf, TransferFunction.LINEAR)) {
            tn = "Linear";
        } else if (tf.g == TransferFunction.TYPE_PQ) {
            tn = "PQ";
        } else if (tf.g == TransferFunction.TYPE_HLG) {
            tn = "HLG";
        } else if (tf.a == 1 && tf.b == 0 && tf.c == 0 && tf.d == 0 && tf.e == 0 && tf.f == 0) {
            tn = new DecimalFormat("0.0##").format(tf.g);
        } else {
            tn = null;
        }
        return tn;
    }

    static final ColorSpace[] sNamedColorSpaces = new ColorSpace[22];

    static {
        sNamedColorSpaces[SRGB.getId()] = SRGB;
        sNamedColorSpaces[LINEAR_SRGB.getId()] = LINEAR_SRGB;
        sNamedColorSpaces[EXTENDED_SRGB.getId()] = EXTENDED_SRGB;
        sNamedColorSpaces[LINEAR_EXTENDED_SRGB.getId()] = LINEAR_EXTENDED_SRGB;
        sNamedColorSpaces[BT709.getId()] = BT709;
        sNamedColorSpaces[BT2020.getId()] = BT2020;
        sNamedColorSpaces[LINEAR_BT2020.getId()] = LINEAR_BT2020;
        sNamedColorSpaces[DCI_P3.getId()] = DCI_P3;
        sNamedColorSpaces[DISPLAY_P3.getId()] = DISPLAY_P3;
        sNamedColorSpaces[LINEAR_DISPLAY_P3.getId()] = LINEAR_DISPLAY_P3;
        sNamedColorSpaces[NTSC_1953.getId()] = NTSC_1953;
        sNamedColorSpaces[BT470_BG.getId()] = BT470_BG;
        sNamedColorSpaces[SMPTE_C.getId()] = SMPTE_C;
        sNamedColorSpaces[ADOBE_RGB.getId()] = ADOBE_RGB;
        sNamedColorSpaces[PRO_PHOTO_RGB.getId()] = PRO_PHOTO_RGB;
        sNamedColorSpaces[ACES.getId()] = ACES;
        sNamedColorSpaces[ACESCG.getId()] = ACESCG;
        sNamedColorSpaces[CIE_XYZ_D50.getId()] = CIE_XYZ_D50;
        sNamedColorSpaces[CIE_XYZ_D65.getId()] = CIE_XYZ_D65;
        sNamedColorSpaces[CIE_XYZ_E.getId()] = CIE_XYZ_E;
        sNamedColorSpaces[CIE_LAB.getId()] = CIE_LAB;
        sNamedColorSpaces[OK_LAB.getId()] = OK_LAB;
    }

    /**
     * <p>Returns a named instance of {@link RGBColorSpace} that matches
     * the specified RGB to CIE XYZ transform and transfer functions. If no
     * instance can be found, this method returns null.</p>
     *
     * <p>The color transform matrix is assumed to target the CIE XYZ space
     * an ICC Profile D50 (0.9642, 1.0000, 0.8249) standard illuminant, which is
     * slightly different from the exact {@link ColorSpace#ILLUMINANT_D50 D50}
     * standard illuminant.</p>
     *
     * @param toXYZD50 3x3 column-major transform matrix from RGB to the profile
     *                 connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param function the transfer function
     * @return A non-null {@link RGBColorSpace} if a match is found, null otherwise
     */
    @Nullable
    public static RGBColorSpace match(
            @Size(9) float @NonNull [] toXYZD50,
            @NonNull TransferFunction function) {

        for (ColorSpace colorSpace : sNamedColorSpaces) {
            if (colorSpace.getModel() == MODEL_RGB) {
                RGBColorSpace rgb = RGBColorSpace.adapt((RGBColorSpace) colorSpace, ICC_ILLUMINANT_D50_XYZ);
                if (ColorSpace.compare(toXYZD50, rgb.mTransform) &&
                        TransferFunction.compare(function, rgb.mTransferFunction)) {
                    return (RGBColorSpace) colorSpace;
                }
            }
        }

        return null;
    }

    /**
     * <p>Returns a named instance of {@link RGBColorSpace} that matches
     * the specified primaries, white point and transfer functions. If no
     * instance can be found, this method returns null.</p>
     *
     * @param primaries  the primaries
     * @param whitePoint the white point
     * @param function   the transfer function
     * @return A non-null {@link RGBColorSpace} if a match is found, null otherwise
     */
    @Nullable
    public static RGBColorSpace match(
            @Size(min = 6) float @NonNull [] primaries,
            @Size(min = 2) float @NonNull [] whitePoint,
            @NonNull TransferFunction function) {

        float[] xyPrimaries = RGBColorSpace.xyPrimaries(primaries);
        float[] xyWhitePoint = xyWhitePoint(whitePoint);

        for (ColorSpace colorSpace : sNamedColorSpaces) {
            if (colorSpace.getModel() == MODEL_RGB) {
                RGBColorSpace rgb = (RGBColorSpace) colorSpace;
                if (ColorSpace.compare(xyPrimaries, rgb.mPrimaries) &&
                        ColorSpace.compare(xyWhitePoint, rgb.mWhitePoint) &&
                        TransferFunction.compare(function, rgb.mTransferFunction)) {
                    return rgb;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("Java9CollectionFactory")
    public static @NonNull @Unmodifiable List<ColorSpace> getNamedColorSpaces() {
        return Collections.unmodifiableList(Arrays.asList(sNamedColorSpaces));
    }
}

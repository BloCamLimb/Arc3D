/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;

/**
 * <p>A {@link ColorSpace} is used to identify a specific organization of colors.
 * Each color space is characterized by a color model that defines
 * how a color value is represented (for instance the {@link #MODEL_RGB RGB} color
 * model defines a color value as a triplet of numbers).</p>
 *
 * <p>Each component of a color must fall within a valid range, specific to each
 * color space, defined by {@link #getMinValue(int)} and {@link #getMaxValue(int)}
 * This range is commonly \([0..1]\). While it is recommended to use values in the
 * valid range, a color space always clamps input and output values when performing
 * operations such as converting to a different color space.</p>
 *
 * <h3>Color model</h3>
 *
 * <p>
 * A color model is required by a {@link ColorSpace} to describe the
 * way colors can be represented as tuples of numbers. A common color
 * model is the {@link #MODEL_RGB RGB} color model which defines a color
 * as represented by a tuple of 3 numbers (red, green and blue).
 * </p>
 *
 * <h3>Using color spaces</h3>
 *
 * <p>This implementation provides a pre-defined set of common color spaces
 * described in the {@link Named} enum. To obtain an instance of one of the
 * pre-defined color spaces, simply invoke {@link #get(Named)}:</p>
 *
 * <pre>{@code
 * ColorSpace sRgb = ColorSpace.get(ColorSpace.Named.SRGB);
 * }</pre>
 *
 * <p>The {@link #get(Named)} method always returns the same instance for a given
 * name. Color spaces with an {@link #MODEL_RGB RGB} color model can be safely
 * cast to {@link ColorSpaceRGB}. Doing so gives you access to more APIs to query various
 * properties of RGB color models: color gamut primaries, transfer functions,
 * conversions to and from linear space, etc. Please refer to {@link ColorSpaceRGB} for
 * more information.</p>
 *
 * <p>The documentation of {@link Named} provides a detailed description of the
 * various characteristics of each available color space.</p>
 *
 * <h3>Color space conversions</h3>
 *
 * <p>To allow conversion between color spaces, this implementation uses the CIE
 * XYZ profile connection space (PCS). Color values can be converted to and from
 * this PCS using {@link #toXYZ(float[])} and {@link #fromXYZ(float[])}.</p>
 *
 * <p>Color spaces use their
 * native white point (D65 for {@link Named#SRGB sRGB} for instance) and must
 * undergo {@link ChromaticAdaptation chromatic adaptation} as necessary.</p>
 *
 * <p>Since the white point of the PCS is not defined for RGB color space, it is
 * highly recommended to use the variants of the {@link ColorTransform}
 * method to perform conversions between color spaces. A color space can be
 * manually adapted to a specific white point using {@link #adapt(ColorSpace, float[])}.
 * Please refer to the documentation of {@link ColorSpaceRGB RGB color spaces} for more
 * information. Several common CIE standard illuminants are provided in this
 * class as reference (see {@link #ILLUMINANT_D65} or {@link #ILLUMINANT_D50}
 * for instance).</p>
 *
 * <p>Here is an example of how to convert from a color space to another:</p>
 *
 * <pre>{@code
 * // Convert from DCI-P3 to Rec.2020
 * ColorTransform transform = new ColorTransform(
 *         ColorSpace.get(ColorSpace.Named.DCI_P3),
 *         ColorSpace.get(ColorSpace.Named.BT2020));
 *
 * float[] bt2020 = transform.transform(p3r, p3g, p3b);
 * }</pre>
 *
 * <p>Conversions also work between color spaces with different color models:</p>
 *
 * <pre class="prettyprint">
 * // Convert from CIE L*a*b* (color model Lab) to Rec.709 (color model RGB)
 * ColorTransform transform = new ColorTransform(
 *         ColorSpace.get(ColorSpace.Named.CIE_LAB),
 *         ColorSpace.get(ColorSpace.Named.BT709));
 * </pre>
 *
 * <h3>Color spaces and multi-threading</h3>
 *
 * <p>Color spaces and other related classes ({@link ColorTransform} for instance)
 * are immutable and stateless. They can be safely used from multiple concurrent
 * threads.</p>
 *
 * <p>Static factory methods provided by this class, such as {@link #get(Named)},
 * are also guaranteed to be thread-safe.</p>
 *
 * @see #get(Named)
 * @see Named
 * @see ColorTransform
 * @see ChromaticAdaptation
 * @see ColorSpaceXYZ
 * @see ColorSpaceRGB
 */
// modified from Android
@SuppressWarnings("unused")
public abstract sealed class ColorSpace permits ColorSpaceXYZ, ColorSpaceRGB,
        ColorSpace.Lab, ColorSpace.OkLab {

    /**
     * Standard CIE 1931 2° illuminant A, encoded in xyY.
     * This illuminant has a color temperature of 2856K.
     */
    public static final float[] ILLUMINANT_A = {0.44757f, 0.40745f};
    /**
     * Standard CIE 1931 2° illuminant B, encoded in xyY.
     * This illuminant has a color temperature of 4874K.
     */
    public static final float[] ILLUMINANT_B = {0.34842f, 0.35161f};
    /**
     * Standard CIE 1931 2° illuminant C, encoded in xyY.
     * This illuminant has a color temperature of 6774K.
     */
    public static final float[] ILLUMINANT_C = {0.31006f, 0.31616f};
    /**
     * Standard CIE 1931 2° illuminant D50, encoded in xyY.
     * This illuminant has a color temperature of 5003K. This illuminant
     * is used by the profile connection space in ICC profiles.
     */
    public static final float[] ILLUMINANT_D50 = {0.34567f, 0.35850f};
    /**
     * Standard CIE 1931 2° illuminant D55, encoded in xyY.
     * This illuminant has a color temperature of 5503K.
     */
    public static final float[] ILLUMINANT_D55 = {0.33242f, 0.34743f};
    /**
     * Standard CIE 1931 2° illuminant D60, encoded in xyY.
     * This illuminant has a color temperature of 6004K.
     */
    public static final float[] ILLUMINANT_D60 = {0.32168f, 0.33767f};
    /**
     * Standard CIE 1931 2° illuminant D65, encoded in xyY.
     * This illuminant has a color temperature of 6504K. This illuminant
     * is commonly used in RGB color spaces such as sRGB, BT.709, etc.
     */
    public static final float[] ILLUMINANT_D65 = {0.31271f, 0.32902f};
    /**
     * Standard CIE 1931 2° illuminant D75, encoded in xyY.
     * This illuminant has a color temperature of 7504K.
     */
    public static final float[] ILLUMINANT_D75 = {0.29902f, 0.31485f};
    /**
     * Standard CIE 1931 2° illuminant E, encoded in xyY.
     * This illuminant has a color temperature of 5454K.
     */
    public static final float[] ILLUMINANT_E = {0.33333f, 0.33333f};

    /**
     * The minimum ID value a color space can have.
     *
     * @see #getId()
     */
    public static final int MIN_ID = -1; // Do not change
    /**
     * The maximum ID value a color space can have.
     *
     * @see #getId()
     */
    public static final int MAX_ID = 63; // Do not change, used to encode in longs

    /**
     * The XYZ model is a color model with 3 components that
     * are used to model human color vision on a basic sensory
     * level.
     */
    public static final int MODEL_XYZ = 0;

    /**
     * The Lab model is a color model with 3 components used
     * to describe a color space that is more perceptually
     * uniform than XYZ.
     */
    public static final int MODEL_LAB = 1;

    /**
     * The RGB model is a color model with 3 components that
     * refer to the three additive primaries: red, green
     * and blue.
     */
    public static final int MODEL_RGB = 5;

    /**
     * The CMYK model is a color model with 4 components that
     * refer to four inks used in color printing: cyan, magenta,
     * yellow and black (or key). CMYK is a subtractive color
     * model.
     */
    public static final int MODEL_CMYK = 9;

    static final float[] SRGB_PRIMARIES = {0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f};
    static final float[] NTSC_1953_PRIMARIES = {0.67f, 0.33f, 0.21f, 0.71f, 0.14f, 0.08f};
    private static final float[] DCI_P3_PRIMARIES =
            { 0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f };
    private static final float[] BT2020_PRIMARIES =
            { 0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f };
    /**
     * A gray color space does not have meaningful primaries, so we use this arbitrary set.
     */
    static final float[] GRAY_PRIMARIES = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};

    static final float[] ILLUMINANT_D50_XYZ = {0.964212f, 1.0f, 0.825188f};

    @NonNull
    private final String mName;
    @MagicConstant(intValues = {MODEL_XYZ, MODEL_LAB, MODEL_RGB, MODEL_CMYK})
    private final int mModel;
    @Range(from = MIN_ID, to = MAX_ID)
    private final int mId;

    @Size(2)
    final float @NonNull[] mWhitePoint;

    /**
     * <p>List of common, named color spaces. A corresponding instance of
     * {@link ColorSpace} can be obtained by calling {@link ColorSpace#get(Named)}:</p>
     *
     * <pre>{@code
     * ColorSpace cs = ColorSpace.get(ColorSpace.Named.DCI_P3);
     * }</pre>
     *
     * <p>The properties of each color space are described below (see {@link #SRGB sRGB}
     * for instance). When applicable, the color gamut of each color space is compared
     * to the color gamut of sRGB using a CIE 1931 xy chromaticity diagram. This diagram
     * shows the location of the color space's primaries and white point.</p>
     *
     * @see ColorSpace#get(Named)
     */
    public enum Named {
        // NOTE: Do NOT change the order of the enum
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
        SRGB,
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
        LINEAR_SRGB,
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
        EXTENDED_SRGB,
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
        LINEAR_EXTENDED_SRGB,
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
        BT709,
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
        BT2020,
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
        DCI_P3,
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
        DISPLAY_P3,
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
        NTSC_1953,
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
        SMPTE_C,
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
        ADOBE_RGB,
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
        PRO_PHOTO_RGB,
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
        ACES,
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
        ACESCG,
        /**
         * <p>{@link #MODEL_XYZ XYZ} color space CIE XYZ. This color space assumes standard
         * illuminant D50 as its white point.</p>
         * <table summary="Color space definition">
         *     <tr><th>Property</th><th colspan="4">Value</th></tr>
         *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D50)</td></tr>
         *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
         *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
         * </table>
         */
        CIE_XYZ_D50,
        /**
         * <p>{@link #MODEL_XYZ XYZ} color space CIE XYZ. This color space assumes standard
         * illuminant D65 as its white point.</p>
         * <table summary="Color space definition">
         *     <tr><th>Property</th><th colspan="4">Value</th></tr>
         *     <tr><td>Name</td><td colspan="4">CIE 1931 XYZ (D65)</td></tr>
         *     <tr><td>CIE standard illuminant</td><td colspan="4">D65</td></tr>
         *     <tr><td>Range</td><td colspan="4">\([-2.0, 2.0]\)</td></tr>
         * </table>
         */
        CIE_XYZ_D65,
        /**
         * <p>{@link #MODEL_LAB Lab} color space CIE L*a*b*. This color space uses CIE XYZ D50
         * as a profile conversion space.</p>
         * <table summary="Color space definition">
         *     <tr><th>Property</th><th colspan="4">Value</th></tr>
         *     <tr><td>Name</td><td colspan="4">Generic L*a*b*</td></tr>
         *     <tr><td>CIE standard illuminant</td><td colspan="4">D50</td></tr>
         *     <tr><td>Range</td><td colspan="4">\(L: [0.0, 100.0], a: [-128, 128], b: [-128, 128]\)</td></tr>
         * </table>
         */
        CIE_LAB,
        /**
         * <p>{@link ColorSpace.Lab Lab} color space OkLab standardized as
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
        OK_LAB;
        // Update the initialization block next to #get(Named) when adding new values

        // See static initialization block next to #get(Named)
        static final ColorSpace[] sNamedColorSpaces = new ColorSpace[Named.values().length];

        static {
            sNamedColorSpaces[Named.SRGB.ordinal()] = new ColorSpaceRGB(
                    "sRGB IEC61966-2.1",
                    SRGB_PRIMARIES,
                    ILLUMINANT_D65,
                    null,
                    ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
                    Named.SRGB.ordinal()
            );
            sNamedColorSpaces[Named.LINEAR_SRGB.ordinal()] = new ColorSpaceRGB(
                    "sRGB IEC61966-2.1 (Linear)",
                    SRGB_PRIMARIES,
                    ILLUMINANT_D65,
                    1.0,
                    0.0f, 1.0f,
                    Named.LINEAR_SRGB.ordinal()
            );
            sNamedColorSpaces[Named.EXTENDED_SRGB.ordinal()] = new ColorSpaceRGB(
                    "scRGB-nl IEC 61966-2-2:2003",
                    SRGB_PRIMARIES,
                    ILLUMINANT_D65,
                    null,
                    x -> absRcpResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                    x -> absResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                    -0.799f, 2.399f,
                    ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
                    Named.EXTENDED_SRGB.ordinal()
            );
            sNamedColorSpaces[Named.LINEAR_EXTENDED_SRGB.ordinal()] = new ColorSpaceRGB(
                    "scRGB IEC 61966-2-2:2003",
                    SRGB_PRIMARIES,
                    ILLUMINANT_D65,
                    1.0,
                    -0.5f, 7.499f,
                    Named.LINEAR_EXTENDED_SRGB.ordinal()
            );
            sNamedColorSpaces[Named.BT709.ordinal()] = new ColorSpaceRGB(
                    "Rec. ITU-R BT.709-5",
                    SRGB_PRIMARIES,
                    ILLUMINANT_D65,
                    null,
                    ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
                    Named.BT709.ordinal()
            );
            sNamedColorSpaces[Named.BT2020.ordinal()] = new ColorSpaceRGB(
                    "Rec. ITU-R BT.2020-1",
                    BT2020_PRIMARIES,
                    ILLUMINANT_D65,
                    null,
                    new ColorSpaceRGB.TransferParameters(1 / 1.0993, 0.0993 / 1.0993, 1 / 4.5, 0.08145, 1 / 0.45),
                    Named.BT2020.ordinal()
            );
            sNamedColorSpaces[Named.DCI_P3.ordinal()] = new ColorSpaceRGB(
                    "SMPTE RP 431-2-2007 DCI (P3)",
                    DCI_P3_PRIMARIES,
                    new float[]{0.314f, 0.351f},
                    2.6,
                    0.0f, 1.0f,
                    Named.DCI_P3.ordinal()
            );
            sNamedColorSpaces[Named.DISPLAY_P3.ordinal()] = new ColorSpaceRGB(
                    "Display P3",
                    DCI_P3_PRIMARIES,
                    ILLUMINANT_D65,
                    null,
                    ColorSpaceRGB.TransferParameters.SRGB_TRANSFER_PARAMETERS,
                    Named.DISPLAY_P3.ordinal()
            );
            sNamedColorSpaces[Named.NTSC_1953.ordinal()] = new ColorSpaceRGB(
                    "NTSC (1953)",
                    NTSC_1953_PRIMARIES,
                    ILLUMINANT_C,
                    null,
                    ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
                    Named.NTSC_1953.ordinal()
            );
            sNamedColorSpaces[Named.SMPTE_C.ordinal()] = new ColorSpaceRGB(
                    "SMPTE-C RGB",
                    new float[]{0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f},
                    ILLUMINANT_D65,
                    null,
                    ColorSpaceRGB.TransferParameters.SMPTE_170M_TRANSFER_PARAMETERS,
                    Named.SMPTE_C.ordinal()
            );
            sNamedColorSpaces[Named.ADOBE_RGB.ordinal()] = new ColorSpaceRGB(
                    "Adobe RGB (1998)",
                    new float[]{0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f},
                    ILLUMINANT_D65,
                    2.2,
                    0.0f, 1.0f,
                    Named.ADOBE_RGB.ordinal()
            );
            sNamedColorSpaces[Named.PRO_PHOTO_RGB.ordinal()] = new ColorSpaceRGB(
                    "ROMM RGB ISO 22028-2:2013",
                    new float[]{0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f},
                    ILLUMINANT_D50,
                    null,
                    new ColorSpaceRGB.TransferParameters(1.0, 0.0, 1 / 16.0, 0.031248, 1.8),
                    Named.PRO_PHOTO_RGB.ordinal()
            );
            sNamedColorSpaces[Named.ACES.ordinal()] = new ColorSpaceRGB(
                    "SMPTE ST 2065-1:2012 ACES",
                    new float[]{0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.0770f},
                    ILLUMINANT_D60,
                    1.0,
                    -65504.0f, 65504.0f,
                    Named.ACES.ordinal()
            );
            sNamedColorSpaces[Named.ACESCG.ordinal()] = new ColorSpaceRGB(
                    "Academy S-2014-004 ACEScg",
                    new float[]{0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f},
                    ILLUMINANT_D60,
                    1.0,
                    -65504.0f, 65504.0f,
                    Named.ACESCG.ordinal()
            );
            sNamedColorSpaces[Named.CIE_XYZ_D50.ordinal()] = new ColorSpaceXYZ(
                    "CIE 1931 XYZ (D50)",
                    ILLUMINANT_D50,
                    Named.CIE_XYZ_D50.ordinal()
            );
            sNamedColorSpaces[Named.CIE_XYZ_D65.ordinal()] = new ColorSpaceXYZ(
                    "CIE 1931 XYZ (D65)",
                    ILLUMINANT_D65,
                    Named.CIE_XYZ_D65.ordinal()
            );
            sNamedColorSpaces[Named.CIE_LAB.ordinal()] = new ColorSpace.Lab(
                    "Generic L*a*b*",
                    Named.CIE_LAB.ordinal()
            );
            sNamedColorSpaces[Named.OK_LAB.ordinal()] = new ColorSpace.OkLab(
                    "Oklab",
                    Named.OK_LAB.ordinal()
            );
        }
    }

    /**
     * Returns the number of components for this color model.
     *
     * @return An integer between 1 and 4
     */
    public static int getComponentCount(int model) {
        return switch (model) {
            case MODEL_XYZ,
                 MODEL_LAB,
                 MODEL_RGB -> 3;
            case MODEL_CMYK -> 4;
            default -> throw new AssertionError(model);
        };
    }

    ColorSpace(@NonNull @Size(min = 1) String name,
               @MagicConstant(intValues = {MODEL_XYZ, MODEL_LAB, MODEL_RGB, MODEL_CMYK}) int model,
               @Size(min = 2, max = 3) float @NonNull[] whitePoint,
               @Range(from = MIN_ID, to = MAX_ID) int id) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The name of a color space cannot be null and " +
                    "must contain at least 1 character");
        }
        if (whitePoint.length != 2 && whitePoint.length != 3) {
            throw new IllegalArgumentException("The color space's white point must be " +
                    "defined as an array of 2 floats in xyY or 3 float in XYZ");
        }
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("The id must be between " +
                    MIN_ID + " and " + MAX_ID);
        }
        mName = name;
        mModel = model;
        mId = id;
        mWhitePoint = xyWhitePoint(whitePoint);
    }

    /**
     * <p>Returns the name of this color space. The name is never null
     * and contains always at least 1 character.</p>
     *
     * <p>Color space names are recommended to be unique but are not
     * guaranteed to be. There is no defined format but the name usually
     * falls in one of the following categories:</p>
     * <ul>
     *     <li>Generic names used to identify color spaces in non-RGB
     *     color models. For instance: {@link Named#CIE_LAB Generic L*a*b*}.</li>
     *     <li>Names tied to a particular specification. For instance:
     *     {@link Named#SRGB sRGB IEC61966-2.1} or
     *     {@link Named#ACES SMPTE ST 2065-1:2012 ACES}.</li>
     *     <li>Ad-hoc names, often generated procedurally or by the user
     *     during a calibration workflow. These names often contain the
     *     make and model of the display.</li>
     * </ul>
     *
     * <p>Because the format of color space names is not defined, it is
     * not recommended to programmatically identify a color space by its
     * name alone. Names can be used as a first approximation.</p>
     *
     * <p>It is however perfectly acceptable to display color space names to
     * users in a UI, or in debuggers and logs. When displaying a color space
     * name to the user, it is recommended to add extra information to avoid
     * ambiguities: color model, a representation of the color space's gamut,
     * white point, etc.</p>
     *
     * @return A non-null String of length >= 1
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the ID of this color space. Positive IDs match the color
     * spaces enumerated in {@link Named}. A negative ID indicates a
     * color space created by calling one of the public constructors.
     *
     * @return An integer between {@link #MIN_ID} and {@link #MAX_ID}
     */
    @Range(from = MIN_ID, to = MAX_ID)
    public int getId() {
        return mId;
    }

    /**
     * Return the color model of this color space.
     *
     * @return A model
     * @see #getComponentCount()
     */
    @MagicConstant(intValues = {MODEL_XYZ, MODEL_LAB, MODEL_RGB, MODEL_CMYK})
    public int getModel() {
        return mModel;
    }

    /**
     * Returns the number of components that form a color value according
     * to this color space's color model.
     *
     * @return An integer between 1 and 4
     * @see #getModel()
     */
    @Range(from = 1, to = 4)
    public int getComponentCount() {
        return getComponentCount(mModel);
    }

    /**
     * Returns whether this color space is a wide-gamut color space.
     * An RGB color space is wide-gamut if its gamut entirely contains
     * the {@link Named#SRGB sRGB} gamut and if the area of its gamut is
     * 90% of greater than the area of the {@link Named#NTSC_1953 NTSC}
     * gamut.
     *
     * @return True if this color space is a wide-gamut color space,
     * false otherwise
     */
    public abstract boolean isWideGamut();

    /**
     * <p>Indicates whether this color space is the sRGB color space or
     * equivalent to the sRGB color space.</p>
     * <p>A color space is considered sRGB if it meets all the following
     * conditions:</p>
     * <ul>
     *     <li>Its color model is {@link #MODEL_RGB}.</li>
     *     <li>
     *         Its primaries are within 1e-3 of the true
     *         {@link Named#SRGB sRGB} primaries.
     *     </li>
     *     <li>
     *         Its white point is within 1e-3 of the CIE standard
     *         illuminant {@link #ILLUMINANT_D65 D65}.
     *     </li>
     *     <li>Its opto-electronic transfer function is not linear.</li>
     *     <li>Its electro-optical transfer function is not linear.</li>
     *     <li>Its transfer functions yield values within 1e-3 of {@link Named#SRGB}.</li>
     *     <li>Its range is \([0..1]\).</li>
     * </ul>
     * <p>This method always returns true for {@link Named#SRGB}.</p>
     *
     * @return True if this color space is the sRGB color space (or a
     * close approximation), false otherwise
     */
    public boolean isSRGB() {
        return false;
    }

    /**
     * Similar to {@link #isSRGB()}, but no range check.
     */
    public boolean isExtendedSRGB() {
        return false;
    }

    /**
     * Returns the minimum valid value for the specified component of this
     * color space's color model.
     *
     * @param component The index of the component
     * @return A floating point value less than {@link #getMaxValue(int)}
     * @see #getMaxValue(int)
     * @see #getComponentCount()
     */
    public abstract float getMinValue(@Range(from = 0, to = 3) int component);

    /**
     * Returns the maximum valid value for the specified component of this
     * color space's color model.
     *
     * @param component The index of the component
     * @return A floating point value greater than {@link #getMinValue(int)}
     * @see #getMinValue(int)
     * @see #getComponentCount()
     */
    public abstract float getMaxValue(@Range(from = 0, to = 3) int component);


    /**
     * Copies the non-adapted CIE xyY white point of this color space in
     * specified array. The Y component is assumed to be 1 and is therefore
     * not copied into the destination. The x and y components are written
     * in the array at positions 0 and 1 respectively.
     *
     * @param whitePoint The destination array, cannot be null, its length
     *                   must be >= 2
     * @return The destination array passed as a parameter
     * @see #getWhitePoint()
     */
    @Size(min = 2)
    public final float @NonNull[] getWhitePoint(@Size(min = 2) float @NonNull[] whitePoint) {
        whitePoint[0] = mWhitePoint[0];
        whitePoint[1] = mWhitePoint[1];
        return whitePoint;
    }


    /**
     * Returns the non-adapted CIE xyY white point of this color space as
     * a new array of 2 floats. The Y component is assumed to be 1 and is
     * therefore not copied into the destination. The x and y components
     * are written in the array at positions 0 and 1 respectively.
     *
     * @return A new non-null array of 2 floats
     * @see #getWhitePoint(float[])
     */
    @Size(2)
    public final float @NonNull[] getWhitePoint() {
        return mWhitePoint.clone();
    }


    /**
     * <p>Converts a color value from this color space's model to
     * tristimulus CIE XYZ values.</p>
     *
     * <p>This method is a convenience for color spaces with a model
     * of 3 components ({@link #MODEL_RGB RGB} or {@link #MODEL_LAB}
     * for instance). With color spaces using fewer or more components,
     * use {@link #toXYZ(float[])} instead</p>.
     *
     * @param r The first component of the value to convert from (typically R in RGB)
     * @param g The second component of the value to convert from (typically G in RGB)
     * @param b The third component of the value to convert from (typically B in RGB)
     * @return A new array of 3 floats, containing tristimulus XYZ values
     * @see #toXYZ(float[])
     * @see #fromXYZ(float, float, float)
     */
    @Size(3)
    public float @NonNull[] toXYZ(float r, float g, float b) {
        return toXYZ(new float[]{r, g, b});
    }


    /**
     * <p>Converts a color value from this color space's model to
     * tristimulus CIE XYZ values.</p>
     *
     * <p class="note">The specified array's length  must be at least
     * equal to to the number of color components as returned by
     * {@link #getComponentCount()}.</p>
     *
     * @param v An array of color components containing the color space's
     *          color value to convert to XYZ, and large enough to hold
     *          the resulting tristimulus XYZ values
     * @return The array passed in parameter
     * @see #toXYZ(float, float, float)
     * @see #fromXYZ(float[])
     */
    @Size(min = 3)
    public abstract float @NonNull[] toXYZ(@Size(min = 3) float @NonNull[] v);

    /**
     * Similar to {@link #toXYZ}, but results are not clamped to {@link #getMinValue(int)} and
     * {@link #getMaxValue(int)}.
     *
     * @param v An array of color components containing the color space's
     *          color value to convert to XYZ, and large enough to hold
     *          the resulting tristimulus XYZ values
     * @return The array passed in parameter
     * @see #toXYZ(float[])
     */
    @Size(min = 3)
    public abstract float @NonNull[] toXYZExtended(@Size(min = 3) float @NonNull[] v);


    /**
     * <p>Converts tristimulus values from the CIE XYZ space to this
     * color space's color model.</p>
     *
     * @param x The X component of the color value
     * @param y The Y component of the color value
     * @param z The Z component of the color value
     * @return A new array whose size is equal to the number of color
     * components as returned by {@link #getComponentCount()}
     * @see #fromXYZ(float[])
     * @see #toXYZ(float, float, float)
     */
    @Size(min = 3)
    public float @NonNull[] fromXYZ(float x, float y, float z) {
        float[] xyz = new float[getComponentCount()];
        xyz[0] = x;
        xyz[1] = y;
        xyz[2] = z;
        return fromXYZ(xyz);
    }


    /**
     * <p>Converts tristimulus values from the CIE XYZ space to this color
     * space's color model. The resulting value is passed back in the specified
     * array.</p>
     *
     * <p class="note">The specified array's length  must be at least equal to
     * to the number of color components as returned by
     * {@link #getComponentCount()}, and its first 3 values must
     * be the XYZ components to convert from.</p>
     *
     * @param v An array of color components containing the XYZ values
     *          to convert from, and large enough to hold the number
     *          of components of this color space's model
     * @return The array passed in parameter
     * @see #fromXYZ(float, float, float)
     * @see #toXYZ(float[])
     */
    @Size(min = 3)
    public abstract float @NonNull[] fromXYZ(@Size(min = 3) float @NonNull[] v);

    /**
     * Similar to {@link #fromXYZ}, but results are not clamped to {@link #getMinValue(int)} and
     * {@link #getMaxValue(int)}.
     *
     * @param v An array of color components containing the XYZ values
     *          to convert from, and large enough to hold the number
     *          of components of this color space's model
     * @return The array passed in parameter
     * @see #fromXYZ(float[])
     */
    @Size(min = 3)
    public abstract float @NonNull[] fromXYZExtended(@Size(min = 3) float @NonNull[] v);

    @Override
    public int hashCode() {
        int result = mId;
        result = 31 * result + mModel;
        result = 31 * result + Arrays.hashCode(mWhitePoint);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColorSpace that = (ColorSpace) o;
        return mId == that.mId &&
                mModel == that.mModel &&
                Arrays.equals(mWhitePoint, that.mWhitePoint);
    }

    public boolean equals(ColorSpace that, boolean extended) {
        if (!extended) {
            return equals(that);
        }
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;
        return mModel == that.mModel &&
                Arrays.equals(mWhitePoint, that.mWhitePoint);
    }

    /**
     * <p>Returns a string representation of the object. This method returns
     * a string equal to the value of:</p>
     *
     * <pre class="prettyprint">
     * getName() + "(id=" + getId() + ", model=" + getModel() + ")"
     * </pre>
     *
     * <p>For instance, the string representation of the {@link Named#SRGB sRGB}
     * color space is equal to the following value:</p>
     *
     * <pre>
     * sRGB IEC61966-2.1 (id=0, model=RGB)
     * </pre>
     *
     * @return A string representation of the object
     */
    @NonNull
    @Override
    public String toString() {
        return mName + " (id=" + mId + ", model=" + mModel + ")";
    }

    /**
     * <p>Performs the chromatic adaptation of a color space from its native
     * white point to the specified white point.</p>
     *
     * <p>The chromatic adaptation is performed using the
     * {@link ChromaticAdaptation#BRADFORD} matrix.</p>
     *
     * <p class="note">The color space returned by this method always has
     * an ID of {@link #MIN_ID}.</p>
     *
     * @param colorSpace The color space to chromatically adapt
     * @param whitePoint The new white point
     * @return A {@link ColorSpace} instance with the same name, primaries,
     * transfer functions and range as the specified color space
     * @see ChromaticAdaptation
     * @see #adapt(ColorSpace, float[], ChromaticAdaptation)
     */
    @NonNull
    public static ColorSpace adapt(@NonNull ColorSpace colorSpace,
            @Size(min = 2, max = 3) float @NonNull[] whitePoint) {
        return adapt(colorSpace, whitePoint, ChromaticAdaptation.BRADFORD);
    }

    /**
     * <p>Performs the chromatic adaptation of a color space from its native
     * white point to the specified white point. If the specified color space
     * does not have an {@link #MODEL_RGB RGB} color model, or if the color
     * space already has the target white point, the color space is returned
     * unmodified.</p>
     *
     * <p>The chromatic adaptation is performed using the von Kries method
     * described in the documentation of {@link ChromaticAdaptation}.</p>
     *
     * <p class="note">The color space returned by this method always has
     * an ID of {@link #MIN_ID}.</p>
     *
     * @param colorSpace The color space to chromatically adapt
     * @param whitePoint The new white point
     * @param adaptation The adaptation matrix
     * @return A new color space if the specified color space has an RGB
     * model and a white point different from the specified white
     * point; the specified color space otherwise
     * @see ChromaticAdaptation
     * @see #adapt(ColorSpace, float[])
     */
    @NonNull
    public static ColorSpace adapt(@NonNull ColorSpace colorSpace,
            @Size(min = 2, max = 3) float @NonNull[] whitePoint,
                                   @NonNull ChromaticAdaptation adaptation) {
        if (colorSpace.getModel() == MODEL_RGB) {
            ColorSpaceRGB rgb = (ColorSpaceRGB) colorSpace;
            if (compare(rgb.mWhitePoint, whitePoint)) return colorSpace;

            float[] xyz = whitePoint.length == 3 ?
                    Arrays.copyOf(whitePoint, 3) : xyYToXYZ(whitePoint);
            float[] adaptationTransform = adaptation.computeTransform(
                    xyYToXYZ(rgb.mWhitePoint), xyz);
            float[] transform = mul3x3(adaptationTransform, rgb.mTransform);

            return new ColorSpaceRGB(rgb, transform, whitePoint);
        }
        return colorSpace;
    }

    /**
     * <p>Returns an instance of {@link ColorSpace} whose ID matches the
     * specified ID.</p>
     *
     * <p>This method always returns the same instance for a given ID.</p>
     *
     * <p>This method is thread-safe.</p>
     *
     * @param index An integer ID between {@link #MIN_ID} and {@link #MAX_ID}
     * @return A non-null {@link ColorSpace} instance
     * @throws IllegalArgumentException If the ID does not match the ID of one of the
     *                                  {@link Named named color spaces}
     */
    @NonNull
    static ColorSpace get(@Range(from = MIN_ID, to = MAX_ID) int index) {
        if (index < 0 || index >= Named.sNamedColorSpaces.length) {
            throw new IllegalArgumentException("Invalid ID, must be in the range [0.." +
                    Named.sNamedColorSpaces.length + ")");
        }
        return Named.sNamedColorSpaces[index];
    }

    /**
     * <p>Returns an instance of {@link ColorSpace} identified by the specified
     * name. The list of names provided in the {@link Named} enum gives access
     * to a variety of common RGB color spaces.</p>
     *
     * <p>This method always returns the same instance for a given name.</p>
     *
     * <p>This method is thread-safe.</p>
     *
     * @param name The name of the color space to get an instance of
     * @return A non-null {@link ColorSpace} instance
     */
    @NonNull
    public static ColorSpace get(@NonNull Named name) {
        return Named.sNamedColorSpaces[name.ordinal()];
    }


    /**
     * Helper method for internal color space transformation.
     * <p>
     * This essentially calls adapt on a ColorSpace that has not been fully
     * created. It also does not fully create the adapted ColorSpace, but
     * just returns the transform.
     */
    @Size(9)
    public static float @NonNull[] adaptToIlluminantD50(
            @Size(2) float @NonNull[] origWhitePoint,
            @Size(9) float @NonNull[] origTransform) {
        float[] desired = ILLUMINANT_D50;
        if (compare(origWhitePoint, desired)) return origTransform;

        float[] xyz = xyYToXYZ(desired);
        float[] adaptationTransform = ChromaticAdaptation.BRADFORD.computeTransform(
                xyYToXYZ(origWhitePoint), xyz);
        return mul3x3(adaptationTransform, origTransform);
    }

    // Reciprocal piecewise gamma response
    public static double rcpResponse(double x, double a, double b, double c, double d, double g) {
        return x >= d * c ? (Math.pow(x, 1.0 / g) - b) / a : x / c;
    }

    // Piecewise gamma response
    public static double response(double x, double a, double b, double c, double d, double g) {
        return x >= d ? Math.pow(a * x + b, g) : c * x;
    }

    // Reciprocal piecewise gamma response
    public static double rcpResponse(double x, double a, double b, double c, double d,
                                      double e, double f, double g) {
        return x >= d * c ? (Math.pow(x - e, 1.0 / g) - b) / a : (x - f) / c;
    }

    // Piecewise gamma response
    public static double response(double x, double a, double b, double c, double d,
                                   double e, double f, double g) {
        return x >= d ? Math.pow(a * x + b, g) + e : c * x + f;
    }

    // Reciprocal piecewise gamma response, encoded as sign(x).f(abs(x)) for color
    // spaces that allow negative values
    public static double absRcpResponse(double x, double g) {
        return Math.copySign(Math.pow(x < 0.0 ? -x : x, 1.0 / g), x);
    }

    // Piecewise gamma response, encoded as sign(x).f(abs(x)) for color spaces that
    // allow negative values
    public static double absResponse(double x, double g) {
        return Math.copySign(Math.pow(x < 0.0 ? -x : x, g), x);
    }

    // Reciprocal piecewise gamma response, encoded as sign(x).f(abs(x)) for color
    // spaces that allow negative values
    public static double absRcpResponse(double x, double a, double b, double c, double d, double g) {
        return Math.copySign(rcpResponse(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    // Piecewise gamma response, encoded as sign(x).f(abs(x)) for color spaces that
    // allow negative values
    public static double absResponse(double x, double a, double b, double c, double d, double g) {
        return Math.copySign(response(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    // Reciprocal piecewise gamma response, encoded as sign(x).f(abs(x)) for color
    // spaces that allow negative values
    public static double absRcpResponse(double x, double a, double b, double c, double d,
                                         double e, double f, double g) {
        return Math.copySign(rcpResponse(x < 0.0 ? -x : x, a, b, c, d, e, f, g), x);
    }

    // Piecewise gamma response, encoded as sign(x).f(abs(x)) for color spaces that
    // allow negative values
    public static double absResponse(double x, double a, double b, double c, double d,
                                      double e, double f, double g) {
        return Math.copySign(response(x < 0.0 ? -x : x, a, b, c, d, e, f, g), x);
    }

    /**
     * Compares two arrays of float with a precision of 1e-3.
     *
     * @param a The first array to compare
     * @param b The second array to compare
     * @return True if the two arrays are equal, false otherwise
     */
    static boolean compare(float @NonNull[] a, float @NonNull[] b) {
        if (a == b) return true;
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], b[i]) != 0 && Math.abs(a[i] - b[i]) > 1e-3f) return false;
        }
        return true;
    }


    /**
     * Inverts a 3x3 matrix. This method assumes the matrix is invertible.
     *
     * @param m A 3x3 matrix as a non-null array of 9 floats
     * @return A new array of 9 floats containing the inverse of the input matrix
     */
    @Size(9)
    static float @NonNull[] inverse3x3(@Size(9) float @NonNull[] m) {
        float a = m[0];
        float b = m[3];
        float c = m[6];
        float d = m[1];
        float e = m[4];
        float f = m[7];
        float g = m[2];
        float h = m[5];
        float i = m[8];

        float A = e * i - f * h;
        float B = f * g - d * i;
        float C = d * h - e * g;

        float det = a * A + b * B + c * C;

        float[] inverted = new float[m.length];
        inverted[0] = A / det;
        inverted[1] = B / det;
        inverted[2] = C / det;
        inverted[3] = (c * h - b * i) / det;
        inverted[4] = (a * i - c * g) / det;
        inverted[5] = (b * g - a * h) / det;
        inverted[6] = (b * f - c * e) / det;
        inverted[7] = (c * d - a * f) / det;
        inverted[8] = (a * e - b * d) / det;
        return inverted;
    }


    /**
     * Multiplies two 3x3 matrices, represented as non-null arrays of 9 floats.
     *
     * @param lhs 3x3 matrix, as a non-null array of 9 floats
     * @param rhs 3x3 matrix, as a non-null array of 9 floats
     * @return A new array of 9 floats containing the result of the multiplication
     * of rhs by lhs
     */
    @Size(9)
    public static float @NonNull[] mul3x3(
            @Size(9) float @NonNull[] lhs, @Size(9) float @NonNull[] rhs) {
        float[] r = new float[9];
        r[0] = lhs[0] * rhs[0] + lhs[3] * rhs[1] + lhs[6] * rhs[2];
        r[1] = lhs[1] * rhs[0] + lhs[4] * rhs[1] + lhs[7] * rhs[2];
        r[2] = lhs[2] * rhs[0] + lhs[5] * rhs[1] + lhs[8] * rhs[2];
        r[3] = lhs[0] * rhs[3] + lhs[3] * rhs[4] + lhs[6] * rhs[5];
        r[4] = lhs[1] * rhs[3] + lhs[4] * rhs[4] + lhs[7] * rhs[5];
        r[5] = lhs[2] * rhs[3] + lhs[5] * rhs[4] + lhs[8] * rhs[5];
        r[6] = lhs[0] * rhs[6] + lhs[3] * rhs[7] + lhs[6] * rhs[8];
        r[7] = lhs[1] * rhs[6] + lhs[4] * rhs[7] + lhs[7] * rhs[8];
        r[8] = lhs[2] * rhs[6] + lhs[5] * rhs[7] + lhs[8] * rhs[8];
        return r;
    }


    /**
     * Multiplies a vector of 3 components by a 3x3 matrix and stores the
     * result in the input vector.
     *
     * @param lhs 3x3 matrix, as a non-null array of 9 floats
     * @param rhs Vector of 3 components, as a non-null array of 3 floats
     * @return The array of 3 passed as the rhs parameter
     */
    @Size(min = 3)
    public static float @NonNull[] mul3x3Float3(
            @Size(9) float @NonNull[] lhs, @Size(min = 3) float @NonNull[] rhs) {
        float r0 = rhs[0];
        float r1 = rhs[1];
        float r2 = rhs[2];
        rhs[0] = lhs[0] * r0 + lhs[3] * r1 + lhs[6] * r2;
        rhs[1] = lhs[1] * r0 + lhs[4] * r1 + lhs[7] * r2;
        rhs[2] = lhs[2] * r0 + lhs[5] * r1 + lhs[8] * r2;
        return rhs;
    }


    /**
     * Multiplies a diagonal 3x3 matrix lhs, represented as an array of 3 floats,
     * by a 3x3 matrix represented as an array of 9 floats.
     *
     * @param lhs Diagonal 3x3 matrix, as a non-null array of 3 floats
     * @param rhs 3x3 matrix, as a non-null array of 9 floats
     * @return A new array of 9 floats containing the result of the multiplication
     * of rhs by lhs
     */
    @Size(9)
    static float @NonNull[] mul3x3Diag(
            @Size(3) float @NonNull[] lhs, @Size(9) float @NonNull[] rhs) {
        return new float[]{
                lhs[0] * rhs[0], lhs[1] * rhs[1], lhs[2] * rhs[2],
                lhs[0] * rhs[3], lhs[1] * rhs[4], lhs[2] * rhs[5],
                lhs[0] * rhs[6], lhs[1] * rhs[7], lhs[2] * rhs[8]
        };
    }


    /**
     * Converts a value from CIE xyY to CIE XYZ. Y is assumed to be 1 so the
     * input xyY array only contains the x and y components.
     *
     * @param xyY The xyY value to convert to XYZ, cannot be null, length must be 2
     * @return A new float array of length 3 containing XYZ values
     */
    @Size(3)
    static float @NonNull[] xyYToXYZ(@Size(2) float @NonNull[] xyY) {
        return new float[]{xyY[0] / xyY[1], 1.0f, (1 - xyY[0] - xyY[1]) / xyY[1]};
    }





    /**
     * <p>Computes the chromaticity coordinates of a specified correlated color
     * temperature (CCT) on the Planckian locus. The specified CCT must be
     * greater than 0. A meaningful CCT range is [1667, 25000].</p>
     *
     * <p>The transform is computed using the methods in Kang et
     * al., <i>Design of Advanced Color - Temperature Control System for HDTV
     * Applications</i>, Journal of Korean Physical Society 41, 865-871
     * (2002).</p>
     *
     * @param cct The correlated color temperature, in Kelvin
     * @return Corresponding XYZ values
     * @throws IllegalArgumentException If cct is invalid
     */
    @Size(3)
    public static float @NonNull[] cctToXYZ(@Range(from = 1, to = Integer.MAX_VALUE) int cct) {
        if (cct < 1) {
            throw new IllegalArgumentException("Temperature must be greater than 0");
        }

        final float icct = 1e3f / cct;
        final float icct2 = icct * icct;
        final float x = cct <= 4000.0f ?
                0.179910f + 0.8776956f * icct - 0.2343589f * icct2 - 0.2661239f * icct2 * icct :
                0.240390f + 0.2226347f * icct + 2.1070379f * icct2 - 3.0258469f * icct2 * icct;

        final float x2 = x * x;
        final float y = cct <= 2222.0f ?
                -0.20219683f + 2.18555832f * x - 1.34811020f * x2 - 1.1063814f * x2 * x :
                cct <= 4000.0f ?
                        -0.16748867f + 2.09137015f * x - 1.37418593f * x2 - 0.9549476f * x2 * x :
                        -0.37001483f + 3.75112997f * x - 5.8733867f * x2 + 3.0817580f * x2 * x;

        return xyYToXYZ(new float[]{x, y});
    }

    /**
     * Converts the specified white point to xyY if needed. The white point
     * can be specified as an array of 2 floats (in CIE xyY) or 3 floats
     * (in CIE XYZ). If no conversion is needed, the input array is copied.
     *
     * @param whitePoint The white point in xyY or XYZ
     * @return A new array of 2 floats containing the white point in xyY
     */
    @Size(2)
    public static float @NonNull[] xyWhitePoint(@Size(min = 2, max = 3) float @NonNull[] whitePoint) {
        float[] xyWhitePoint = new float[2];

        // XYZ to xyY
        if (whitePoint.length == 3) {
            float sum = whitePoint[0] + whitePoint[1] + whitePoint[2];
            xyWhitePoint[0] = whitePoint[0] / sum;
            xyWhitePoint[1] = whitePoint[1] / sum;
        } else {
            System.arraycopy(whitePoint, 0, xyWhitePoint, 0, 2);
        }

        return xyWhitePoint;
    }

    /**
     * Implementation of the CIE L*a*b* color space. Its PCS is CIE XYZ
     * with a white point of D50.
     */
    private static final class Lab extends ColorSpace {

        private static final float A = 216.0f / 24389.0f;
        private static final float B = 841.0f / 108.0f;
        private static final float C = 4.0f / 29.0f;
        private static final float D = 6.0f / 29.0f;

        private Lab(@NonNull String name,
                    @Range(from = MIN_ID, to = MAX_ID) int id) {
            super(name, MODEL_LAB, ILLUMINANT_D50, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(@Range(from = 0, to = 3) int component) {
            return component == 0 ? 0.0f : -128.0f;
        }

        @Override
        public float getMaxValue(@Range(from = 0, to = 3) int component) {
            return component == 0 ? 100.0f : 128.0f;
        }


        @Override
        public float @NonNull[] toXYZ(@Size(min = 3) float @NonNull[] v) {
            v[0] = MathUtil.clamp(v[0], 0.0f, 100.0f);
            v[1] = MathUtil.clamp(v[1], -128.0f, 128.0f);
            v[2] = MathUtil.clamp(v[2], -128.0f, 128.0f);

            float fy = (v[0] + 16.0f) / 116.0f;
            float fx = fy + (v[1] * 0.002f);
            float fz = fy - (v[2] * 0.005f);
            float X = fx > D ? fx * fx * fx : (1.0f / B) * (fx - C);
            float Y = fy > D ? fy * fy * fy : (1.0f / B) * (fy - C);
            float Z = fz > D ? fz * fz * fz : (1.0f / B) * (fz - C);

            v[0] = X * ILLUMINANT_D50_XYZ[0];
            v[1] = Y * ILLUMINANT_D50_XYZ[1];
            v[2] = Z * ILLUMINANT_D50_XYZ[2];

            return v;
        }

        @Override
        public float @NonNull[] toXYZExtended(@Size(min = 3) float @NonNull[] v) {
            float fy = (v[0] + 16.0f) / 116.0f;
            float fx = fy + (v[1] * 0.002f);
            float fz = fy - (v[2] * 0.005f);
            float X = fx > D ? fx * fx * fx : (1.0f / B) * (fx - C);
            float Y = fy > D ? fy * fy * fy : (1.0f / B) * (fy - C);
            float Z = fz > D ? fz * fz * fz : (1.0f / B) * (fz - C);

            v[0] = X * ILLUMINANT_D50_XYZ[0];
            v[1] = Y * ILLUMINANT_D50_XYZ[1];
            v[2] = Z * ILLUMINANT_D50_XYZ[2];

            return v;
        }

        @Override
        public float @NonNull[] fromXYZ(@Size(min = 3) float @NonNull[] v) {
            float X = v[0] / ILLUMINANT_D50_XYZ[0];
            float Y = v[1] / ILLUMINANT_D50_XYZ[1];
            float Z = v[2] / ILLUMINANT_D50_XYZ[2];

            float fx = X > A ? (float) Math.pow(X, 1.0 / 3.0) : B * X + C;
            float fy = Y > A ? (float) Math.pow(Y, 1.0 / 3.0) : B * Y + C;
            float fz = Z > A ? (float) Math.pow(Z, 1.0 / 3.0) : B * Z + C;

            float L = 116.0f * fy - 16.0f;
            float a = 500.0f * (fx - fy);
            float b = 200.0f * (fy - fz);

            v[0] = MathUtil.clamp(L, 0.0f, 100.0f);
            v[1] = MathUtil.clamp(a, -128.0f, 128.0f);
            v[2] = MathUtil.clamp(b, -128.0f, 128.0f);

            return v;
        }

        @Override
        public float @NonNull[] fromXYZExtended(@Size(min = 3) float @NonNull[] v) {
            float X = v[0] / ILLUMINANT_D50_XYZ[0];
            float Y = v[1] / ILLUMINANT_D50_XYZ[1];
            float Z = v[2] / ILLUMINANT_D50_XYZ[2];

            float fx = X > A ? (float) Math.pow(X, 1.0 / 3.0) : B * X + C;
            float fy = Y > A ? (float) Math.pow(Y, 1.0 / 3.0) : B * Y + C;
            float fz = Z > A ? (float) Math.pow(Z, 1.0 / 3.0) : B * Z + C;

            float L = 116.0f * fy - 16.0f;
            float a = 500.0f * (fx - fy);
            float b = 200.0f * (fy - fz);

            v[0] = L;
            v[1] = a;
            v[2] = b;

            return v;
        }
    }

    /**
     * Implementation of the Oklab color space. Oklab uses a D65 white point.
     */
    private static final class OkLab extends ColorSpace {

        /**
         * Matrix applied before the nonlinear transform.
         */
        private static final float[] M1 = {
                0.8189330101f, 0.0329845436f, 0.0482003018f,
                0.3618667424f, 0.9293118715f, 0.2643662691f,
                -0.1288597137f, 0.0361456387f, 0.6338517070f
        };

        /**
         * Matrix applied after the nonlinear transform.
         */
        private static final float[] M2 = {
                0.2104542553f, 1.9779984951f, 0.0259040371f,
                0.7936177850f, -2.4285922050f, 0.7827717662f,
                -0.0040720468f, 0.4505937099f, -0.8086757660f
        };

        /**
         * The inverse of the [M1] matrix, transforming back to XYZ (D65)
         */
        private static final float[] INVERSE_M1 = inverse3x3(M1);

        /**
         * The inverse of the [M2] matrix, doing the first linear transform in the
         * Oklab-to-XYZ before doing the nonlinear transform.
         */
        private static final float[] INVERSE_M2 = inverse3x3(M2);

        private OkLab(@NonNull String name,
                      @Range(from = MIN_ID, to = MAX_ID) int id) {
            super(name, MODEL_LAB, ILLUMINANT_D65, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(@Range(from = 0, to = 3) int component) {
            return component == 0 ? 0.0f : -0.5f;
        }

        @Override
        public float getMaxValue(@Range(from = 0, to = 3) int component) {
            return component == 0 ? 1.0f : 0.5f;
        }

        @Override
        public float @NonNull[] toXYZ(@Size(min = 3) float @NonNull[] v) {
            v[0] = MathUtil.clamp(v[0], 0.0f, 1.0f);
            v[1] = MathUtil.clamp(v[1], -0.5f, 0.5f);
            v[2] = MathUtil.clamp(v[2], -0.5f, 0.5f);

            mul3x3Float3(INVERSE_M2, v);

            v[0] = v[0] * v[0] * v[0];
            v[1] = v[1] * v[1] * v[1];
            v[2] = v[2] * v[2] * v[2];

            mul3x3Float3(INVERSE_M1, v);

            return v;
        }

        @Override
        public float @NonNull[] toXYZExtended(@Size(min = 3) float @NonNull[] v) {
            mul3x3Float3(INVERSE_M2, v);
            v[0] = v[0] * v[0] * v[0];
            v[1] = v[1] * v[1] * v[1];
            v[2] = v[2] * v[2] * v[2];
            mul3x3Float3(INVERSE_M1, v);
            return v;
        }

        @Override
        public float @NonNull[] fromXYZ(@Size(min = 3) float @NonNull[] v) {
            mul3x3Float3(M1, v);

            v[0] = (float) Math.cbrt(v[0]);
            v[1] = (float) Math.cbrt(v[1]);
            v[2] = (float) Math.cbrt(v[2]);

            mul3x3Float3(M2, v);

            v[0] = MathUtil.clamp(v[0], 0.0f, 1.0f);
            v[1] = MathUtil.clamp(v[1], -0.5f, 0.5f);
            v[2] = MathUtil.clamp(v[2], -0.5f, 0.5f);

            return v;
        }

        @Override
        public float @NonNull[] fromXYZExtended(@Size(min = 3) float @NonNull[] v) {
            mul3x3Float3(M1, v);
            v[0] = (float) Math.cbrt(v[0]);
            v[1] = (float) Math.cbrt(v[1]);
            v[2] = (float) Math.cbrt(v[2]);
            mul3x3Float3(M2, v);
            return v;
        }
    }

}

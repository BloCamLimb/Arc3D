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

import org.jetbrains.annotations.Range;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * {@usesMathJax}
 *
 * <p>An RGB color space is an additive color space using the
 * {@link #MODEL_RGB RGB} color model (a color is therefore represented
 * by a tuple of 3 numbers).</p>
 *
 * <p>A specific RGB color space is defined by the following properties:</p>
 * <ul>
 *     <li>Three chromaticities of the red, green and blue primaries, which
 *     define the gamut of the color space.</li>
 *     <li>A white point chromaticity that defines the stimulus to which
 *     color space values are normalized (also just called "white").</li>
 *     <li>An opto-electronic transfer function, also called opto-electronic
 *     conversion function or often, and approximately, gamma function.</li>
 *     <li>An electro-optical transfer function, also called electo-optical
 *     conversion function or often, and approximately, gamma function.</li>
 *     <li>A range of valid RGB values (most commonly \([0..1]\)).</li>
 * </ul>
 *
 * <p>The most commonly used RGB color space is {@link Named#SRGB sRGB}.</p>
 *
 * <h3>Primaries and white point chromaticities</h3>
 * <p>In this implementation, the chromaticity of the primaries and the white
 * point of an RGB color space is defined in the CIE xyY color space. This
 * color space separates the chromaticity of a color, the x and y components,
 * and its luminance, the Y component. Since the primaries and the white
 * point have full brightness, the Y component is assumed to be 1 and only
 * the x and y components are needed to encode them.</p>
 * <p>For convenience, this implementation also allows to define the
 * primaries and white point in the CIE XYZ space. The tristimulus XYZ values
 * are internally converted to xyY.</p>
 *
 * <p>
 *     <img style="display: block; margin: 0 auto;" src="{@docRoot}reference/android/images/graphics
 *     /colorspace_srgb.png" />
 *     <figcaption style="text-align: center;">sRGB primaries and white point</figcaption>
 * </p>
 *
 * <h3>Transfer functions</h3>
 * <p>A transfer function is a color component conversion function, defined as
 * a single variable, monotonic mathematical function. It is applied to each
 * individual component of a color. They are used to perform the mapping
 * between linear tristimulus values and non-linear electronic signal value.</p>
 * <p>The <em>opto-electronic transfer function</em> (OETF or OECF) encodes
 * tristimulus values in a scene to a non-linear electronic signal value.
 * An OETF is often expressed as a power function with an exponent between
 * 0.38 and 0.55 (the reciprocal of 1.8 to 2.6).</p>
 * <p>The <em>electro-optical transfer function</em> (EOTF or EOCF) decodes
 * a non-linear electronic signal value to a tristimulus value at the display.
 * An EOTF is often expressed as a power function with an exponent between
 * 1.8 and 2.6.</p>
 * <p>Transfer functions are used as a compression scheme. For instance,
 * linear sRGB values would normally require 11 to 12 bits of precision to
 * store all values that can be perceived by the human eye. When encoding
 * sRGB values using the appropriate OETF (see {@link Named#SRGB sRGB} for
 * an exact mathematical description of that OETF), the values can be
 * compressed to only 8 bits precision.</p>
 * <p>When manipulating RGB values, particularly sRGB values, it is safe
 * to assume that these values have been encoded with the appropriate
 * OETF (unless noted otherwise). Encoded values are often said to be in
 * "gamma space". They are therefore defined in a non-linear space. This
 * in turns means that any linear operation applied to these values is
 * going to yield mathematically incorrect results (any linear interpolation
 * such as gradient generation for instance, most image processing functions
 * such as blurs, etc.).</p>
 * <p>To properly process encoded RGB values you must first apply the
 * EOTF to decode the value into linear space. After processing, the RGB
 * value must be encoded back to non-linear ("gamma") space. Here is a
 * formal description of the process, where \(f\) is the processing
 * function to apply:</p>
 * <p>
 * $$RGB_{out} = OETF(f(EOTF(RGB_{in})))$$
 *
 * <p>If the transfer functions of the color space can be expressed as an
 * ICC parametric curve as defined in ICC.1:2004-10, the numeric parameters
 * can be retrieved by calling {@link #getTransferParameters()}. This can
 * be useful to match color spaces for instance.</p>
 *
 * <p class="note">Some RGB color spaces, such as {@link Named#ACES} and
 * {@link Named#LINEAR_EXTENDED_SRGB scRGB}, are said to be linear because
 * their transfer functions are the identity function: \(f(x) = x\).
 * If the source and/or destination are known to be linear, it is not
 * necessary to invoke the transfer functions.</p>
 *
 * <h3>Range</h3>
 * <p>Most RGB color spaces allow RGB values in the range \([0..1]\). There
 * are however a few RGB color spaces that allow much larger ranges. For
 * instance, {@link Named#EXTENDED_SRGB scRGB} is used to manipulate the
 * range \([-0.5..7.5]\) while {@link Named#ACES ACES} can be used throughout
 * the range \([-65504, 65504]\).</p>
 *
 * <p>
 *     <img style="display: block; margin: 0 auto;" src="{@docRoot}reference/android/images/graphics
 *     /colorspace_scrgb.png" />
 *     <figcaption style="text-align: center;">Extended sRGB and its large range</figcaption>
 * </p>
 *
 * <h3>Converting between RGB color spaces</h3>
 * <p>Conversion between two color spaces is achieved by using an intermediate
 * color space called the profile connection space (PCS). The PCS used by
 * this implementation is CIE XYZ. The conversion operation is defined
 * as such:</p>
 * <p>
 * $$RGB_{out} = OETF(T_{dst}^{-1} \cdot T_{src} \cdot EOTF(RGB_{in}))$$
 *
 * <p>Where \(T_{src}\) is the {@link #getTransform() RGB to XYZ transform}
 * of the source color space and \(T_{dst}^{-1}\) the {@link #getInverseTransform()
 * XYZ to RGB transform} of the destination color space.</p>
 * <p>Many RGB color spaces commonly used with electronic devices use the
 * standard illuminant {@link #ILLUMINANT_D65 D65}. Care must be take however
 * when converting between two RGB color spaces if their white points do not
 * match. This can be achieved by either calling
 * {@link #adapt(ColorSpace, float[])} to adapt one or both color spaces to
 * a single common white point. This can be achieved automatically by calling
 * {@link ColorTransform}, which also handles
 * non-RGB color spaces.</p>
 * <p>To learn more about the white point adaptation process, refer to the
 * documentation of {@link ChromaticAdaptation}.</p>
 */
public non-sealed class ColorSpaceRGB extends ColorSpace {
    /**
     * {@usesMathJax}
     *
     * <p>Defines the parameters for the ICC parametric curve type 4, as
     * defined in ICC.1:2004-10, section 10.15.</p>
     *
     * <p>The EOTF is of the form:</p>
     * <p>
     * \(\begin{equation}
     * Y = \begin{cases}c X + f & X \lt d \\\
     * \left( a X + b \right) ^{g} + e & X \ge d \end{cases}
     * \end{equation}\)
     *
     * <p>The corresponding OETF is simply the inverse function.</p>
     *
     * <p>The parameters defined by this class form a valid transfer
     * function only if all the following conditions are met:</p>
     * <ul>
     *     <li>No parameter is a {@link Double#isNaN(double) Not-a-Number}</li>
     *     <li>\(d\) is in the range \([0..1]\)</li>
     *     <li>The function is not constant</li>
     *     <li>The function is positive and increasing</li>
     * </ul>
     */
    public static class TransferParameters {
        public static final TransferParameters SRGB_TRANSFER_PARAMETERS =
                new TransferParameters(1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4);
        public static final TransferParameters SMPTE_170M_TRANSFER_PARAMETERS =
                new TransferParameters(1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081, 1 / 0.45);
        /**
         * Variable \(a\) in the equation of the EOTF described above.
         */
        public final double a;
        /**
         * Variable \(b\) in the equation of the EOTF described above.
         */
        public final double b;
        /**
         * Variable \(c\) in the equation of the EOTF described above.
         */
        public final double c;
        /**
         * Variable \(d\) in the equation of the EOTF described above.
         */
        public final double d;
        /**
         * Variable \(e\) in the equation of the EOTF described above.
         */
        public final double e;
        /**
         * Variable \(f\) in the equation of the EOTF described above.
         */
        public final double f;
        /**
         * Variable \(g\) in the equation of the EOTF described above.
         */
        public final double g;

        /**
         * <p>Defines the parameters for the ICC parametric curve type 3, as
         * defined in ICC.1:2004-10, section 10.15.</p>
         *
         * <p>The EOTF is of the form:</p>
         * <p>
         * \(\begin{equation}
         * Y = \begin{cases}c X & X \lt d \\\
         * \left( a X + b \right) ^{g} & X \ge d \end{cases}
         * \end{equation}\)
         *
         * <p>This constructor is equivalent to setting  \(e\) and \(f\) to 0.</p>
         *
         * @param a The value of \(a\) in the equation of the EOTF described above
         * @param b The value of \(b\) in the equation of the EOTF described above
         * @param c The value of \(c\) in the equation of the EOTF described above
         * @param d The value of \(d\) in the equation of the EOTF described above
         * @param g The value of \(g\) in the equation of the EOTF described above
         * @throws IllegalArgumentException If the parameters form an invalid transfer function
         */
        public TransferParameters(double a, double b, double c, double d, double g) {
            this(a, b, c, d, 0.0, 0.0, g);
        }

        /**
         * <p>Defines the parameters for the ICC parametric curve type 4, as
         * defined in ICC.1:2004-10, section 10.15.</p>
         *
         * @param a The value of \(a\) in the equation of the EOTF described above
         * @param b The value of \(b\) in the equation of the EOTF described above
         * @param c The value of \(c\) in the equation of the EOTF described above
         * @param d The value of \(d\) in the equation of the EOTF described above
         * @param e The value of \(e\) in the equation of the EOTF described above
         * @param f The value of \(f\) in the equation of the EOTF described above
         * @param g The value of \(g\) in the equation of the EOTF described above
         * @throws IllegalArgumentException If the parameters form an invalid transfer function
         */
        public TransferParameters(double a, double b, double c, double d, double e,
                                  double f, double g) {

            if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c) ||
                    Double.isNaN(d) || Double.isNaN(e) || Double.isNaN(f) ||
                    Double.isNaN(g)) {
                throw new IllegalArgumentException("Parameters cannot be NaN");
            }

            // Next representable float after 1.0
            // We use doubles here but the representation inside our native code is often floats
            if (!(d >= 0.0 && d <= 1.0f + Math.ulp(1.0f))) {
                throw new IllegalArgumentException("Parameter d must be in the range [0..1], " +
                        "was " + d);
            }

            if (d == 0.0 && (a == 0.0 || g == 0.0)) {
                throw new IllegalArgumentException(
                        "Parameter a or g is zero, the transfer function is constant");
            }

            if (d >= 1.0 && c == 0.0) {
                throw new IllegalArgumentException(
                        "Parameter c is zero, the transfer function is constant");
            }

            if ((a == 0.0 || g == 0.0) && c == 0.0) {
                throw new IllegalArgumentException("Parameter a or g is zero," +
                        " and c is zero, the transfer function is constant");
            }

            if (c < 0.0) {
                throw new IllegalArgumentException("The transfer function must be increasing");
            }

            if (a < 0.0 || g < 0.0) {
                throw new IllegalArgumentException("The transfer function must be " +
                        "positive or increasing");
            }

            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
        }

        @SuppressWarnings("SimplifiableIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TransferParameters that = (TransferParameters) o;

            if (Double.compare(that.a, a) != 0) return false;
            if (Double.compare(that.b, b) != 0) return false;
            if (Double.compare(that.c, c) != 0) return false;
            if (Double.compare(that.d, d) != 0) return false;
            if (Double.compare(that.e, e) != 0) return false;
            if (Double.compare(that.f, f) != 0) return false;
            return Double.compare(that.g, g) == 0;
        }

        @Override
        public int hashCode() {
            int result = Double.hashCode(a);
            result = 31 * result + Double.hashCode(b);
            result = 31 * result + Double.hashCode(c);
            result = 31 * result + Double.hashCode(d);
            result = 31 * result + Double.hashCode(e);
            result = 31 * result + Double.hashCode(f);
            result = 31 * result + Double.hashCode(g);
            return result;
        }
    }


    final float @NonNull [] mPrimaries;

    final float @NonNull [] mTransform;

    final float @NonNull [] mInverseTransform;

    @NonNull
    private final DoubleUnaryOperator mOETF;
    @NonNull
    private final DoubleUnaryOperator mEOTF;
    @NonNull
    private final DoubleUnaryOperator mClampedOETF;
    @NonNull
    private final DoubleUnaryOperator mClampedEOTF;

    private final float mMin;
    private final float mMax;

    private final boolean mIsWideGamut;
    private final boolean mIsSRGB;

    @Nullable
    private final TransferParameters mTransferParameters;

    /**
     * <p>Creates a new RGB color space using a 3x3 column-major transform matrix.
     * The transform matrix must convert from the RGB space to the profile connection
     * space CIE XYZ.</p>
     *
     * <p class="note">The range of the color space is imposed to be \([0..1]\).</p>
     *
     * @param name  Name of the color space, cannot be null, its length must be >= 1
     * @param toXYZ 3x3 column-major transform matrix from RGB to the profile
     *              connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param oetf  Opto-electronic transfer function, cannot be null
     * @param eotf  Electro-optical transfer function, cannot be null
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The OETF is null or the EOTF is null.</li>
     *                                      <li>The minimum valid value is >= the maximum valid value.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(9) float @NonNull [] toXYZ,
            @NonNull DoubleUnaryOperator oetf,
            @NonNull DoubleUnaryOperator eotf) {
        this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), null,
                oetf, eotf, 0.0f, 1.0f, null, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * <p class="note">The ID, as returned by {@link #getId()}, of an object created by
     * this constructor is always {@link #MIN_ID}.</p>
     *
     * @param name       Name of the color space, cannot be null, its length must be >= 1
     * @param primaries  RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param oetf       Opto-electronic transfer function, cannot be null
     * @param eotf       Electro-optical transfer function, cannot be null
     * @param min        The minimum valid value in this color space's RGB range
     * @param max        The maximum valid value in this color space's RGB range
     * @throws IllegalArgumentException <p>If any of the following conditions is met:</p>
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The OETF is null or the EOTF is null.</li>
     *                                      <li>The minimum valid value is >= the maximum valid value.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            @NonNull DoubleUnaryOperator oetf,
            @NonNull DoubleUnaryOperator eotf,
            float min,
            float max) {
        this(name, primaries, whitePoint, null, oetf, eotf, min, max, null, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a 3x3 column-major transform matrix.
     * The transform matrix must convert from the RGB space to the profile connection
     * space CIE XYZ.</p>
     *
     * <p class="note">The range of the color space is imposed to be \([0..1]\).</p>
     *
     * @param name     Name of the color space, cannot be null, its length must be >= 1
     * @param toXYZ    3x3 column-major transform matrix from RGB to the profile
     *                 connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param function Parameters for the transfer functions
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>Gamma is negative.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(9) float @NonNull [] toXYZ,
            @NonNull TransferParameters function) {
        // Note: when isGray() returns false, this passes null for the transform for
        // consistency with other constructors, which compute the transform from the primaries
        // and white point.
        this(name, isGray(toXYZ) ? GRAY_PRIMARIES : computePrimaries(toXYZ),
                computeWhitePoint(toXYZ), isGray(toXYZ) ? toXYZ : null, function, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name       Name of the color space, cannot be null, its length must be >= 1
     * @param primaries  RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param function   Parameters for the transfer functions
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The transfer parameters are invalid.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            @NonNull TransferParameters function) {
        this(name, primaries, whitePoint, null, function, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name       Name of the color space, cannot be null, its length must be >= 1
     * @param primaries  RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param transform  Computed transform matrix that converts from RGB to XYZ, or
     *                   {@code null} to compute it from {@code primaries} and {@code whitePoint}.
     * @param function   Parameters for the transfer functions
     * @param id         ID of this color space as an integer between {@link #MIN_ID} and {@link #MAX_ID}
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The ID is not between {@link #MIN_ID} and {@link #MAX_ID}.</li>
     *                                      <li>The transfer parameters are invalid.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            @Size(9) float @Nullable [] transform,
            @NonNull TransferParameters function,
            @Range(from = MIN_ID, to = MAX_ID) int id) {
        this(name, primaries, whitePoint, transform,
                function.e == 0.0 && function.f == 0.0 ?
                        x -> absRcpResponse(x, function.a, function.b,
                                function.c, function.d, function.g) :
                        x -> absRcpResponse(x, function.a, function.b, function.c,
                                function.d, function.e, function.f, function.g),
                function.e == 0.0 && function.f == 0.0 ?
                        x -> absResponse(x, function.a, function.b,
                                function.c, function.d, function.g) :
                        x -> absResponse(x, function.a, function.b, function.c,
                                function.d, function.e, function.f, function.g),
                0.0f, 1.0f, function, id);
    }

    /**
     * <p>Creates a new RGB color space using a 3x3 column-major transform matrix.
     * The transform matrix must convert from the RGB space to the profile connection
     * space CIE XYZ.</p>
     *
     * <p class="note">The range of the color space is imposed to be \([0..1]\).</p>
     *
     * @param name  Name of the color space, cannot be null, its length must be >= 1
     * @param toXYZ 3x3 column-major transform matrix from RGB to the profile
     *              connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param gamma Gamma to use as the transfer function
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>Gamma is negative.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(9) float @NonNull [] toXYZ,
            double gamma) {
        this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), gamma, 0.0f, 1.0f, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name       Name of the color space, cannot be null, its length must be >= 1
     * @param primaries  RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param gamma      Gamma to use as the transfer function
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>Gamma is negative.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            double gamma) {
        this(name, primaries, whitePoint, gamma, 0.0f, 1.0f, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name       Name of the color space, cannot be null, its length must be >= 1
     * @param primaries  RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param gamma      Gamma to use as the transfer function
     * @param min        The minimum valid value in this color space's RGB range
     * @param max        The maximum valid value in this color space's RGB range
     * @param id         ID of this color space as an integer between {@link #MIN_ID} and {@link #MAX_ID}
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The minimum valid value is >= the maximum valid value.</li>
     *                                      <li>The ID is not between {@link #MIN_ID} and {@link #MAX_ID}.</li>
     *                                      <li>Gamma is negative.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            double gamma,
            float min,
            float max,
            @Range(from = MIN_ID, to = MAX_ID) int id) {
        this(name, primaries, whitePoint, null,
                gamma == 1.0 ? DoubleUnaryOperator.identity() :
                        x -> absRcpResponse(x, gamma),
                gamma == 1.0 ? DoubleUnaryOperator.identity() :
                        x -> absResponse(x, gamma),
                min, max, new TransferParameters(1.0, 0.0, 0.0, 0.0, gamma), id);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name               Name of the color space, cannot be null, its length must be >= 1
     * @param primaries          RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint         Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param transform          Computed transform matrix that converts from RGB to XYZ, or
     *                           {@code null} to compute it from {@code primaries} and {@code whitePoint}.
     * @param oetf               Opto-electronic transfer function, cannot be null
     * @param eotf               Electro-optical transfer function, cannot be null
     * @param min                The minimum valid value in this color space's RGB range
     * @param max                The maximum valid value in this color space's RGB range
     * @param transferParameters Parameters for the transfer functions
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The OETF is null or the EOTF is null.</li>
     *                                      <li>The minimum valid value is >= the maximum valid value.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    public ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            @Size(9) float @Nullable [] transform,
            @NonNull DoubleUnaryOperator oetf,
            @NonNull DoubleUnaryOperator eotf,
            float min,
            float max,
            @Nullable TransferParameters transferParameters) {
        this(name, primaries, whitePoint, transform,
                oetf, eotf, min, max, transferParameters, MIN_ID);
    }

    /**
     * <p>Creates a new RGB color space using a specified set of primaries
     * and a specified white point.</p>
     *
     * <p>The primaries and white point can be specified in the CIE xyY space
     * or in CIE XYZ. The length of the arrays depends on the chosen space:</p>
     *
     * <table summary="Parameters length">
     *     <tr><th>Space</th><th>Primaries length</th><th>White point length</th></tr>
     *     <tr><td>xyY</td><td>6</td><td>2</td></tr>
     *     <tr><td>XYZ</td><td>9</td><td>3</td></tr>
     * </table>
     *
     * <p>When the primaries and/or white point are specified in xyY, the Y component
     * does not need to be specified and is assumed to be 1.0. Only the xy components
     * are required.</p>
     *
     * @param name               Name of the color space, cannot be null, its length must be >= 1
     * @param primaries          RGB primaries as an array of 6 (xy) or 9 (XYZ) floats
     * @param whitePoint         Reference white as an array of 2 (xy) or 3 (XYZ) floats
     * @param transform          Computed transform matrix that converts from RGB to XYZ, or
     *                           {@code null} to compute it from {@code primaries} and {@code whitePoint}.
     * @param oetf               Opto-electronic transfer function, cannot be null
     * @param eotf               Electro-optical transfer function, cannot be null
     * @param min                The minimum valid value in this color space's RGB range
     * @param max                The maximum valid value in this color space's RGB range
     * @param transferParameters Parameters for the transfer functions
     * @param id                 ID of this color space as an integer between {@link #MIN_ID} and {@link #MAX_ID}
     * @throws IllegalArgumentException If any of the following conditions is met:
     *                                  <ul>
     *                                      <li>The name is null or has a length of 0.</li>
     *                                      <li>The primaries array is null or has a length that is neither 6 or
     *                                      9.</li>
     *                                      <li>The white point array is null or has a length that is neither 2
     *                                      or 3.</li>
     *                                      <li>The OETF is null or the EOTF is null.</li>
     *                                      <li>The minimum valid value is >= the maximum valid value.</li>
     *                                      <li>The ID is not between {@link #MIN_ID} and {@link #MAX_ID}.</li>
     *                                  </ul>
     * @see #get(Named)
     */
    ColorSpaceRGB(
            @NonNull @Size(min = 1) String name,
            @Size(min = 6, max = 9) float @NonNull [] primaries,
            @Size(min = 2, max = 3) float @NonNull [] whitePoint,
            @Size(9) float @Nullable [] transform,
            @NonNull DoubleUnaryOperator oetf,
            @NonNull DoubleUnaryOperator eotf,
            float min,
            float max,
            @Nullable TransferParameters transferParameters,
            @Range(from = MIN_ID, to = MAX_ID) int id) {

        super(name, MODEL_RGB, whitePoint, id);

        if (primaries.length != 6 && primaries.length != 9) {
            throw new IllegalArgumentException("The color space's primaries must be " +
                    "defined as an array of 6 floats in xyY or 9 floats in XYZ");
        }

        Objects.requireNonNull(oetf, "The transfer functions of a color space cannot be null");
        Objects.requireNonNull(eotf, "The transfer functions of a color space cannot be null");

        if (min >= max) {
            throw new IllegalArgumentException("Invalid range: min=" + min + ", max=" + max +
                    "; min must be strictly < max");
        }

        mPrimaries = xyPrimaries(primaries);

        if (transform == null) {
            mTransform = computeXYZMatrix(mPrimaries, mWhitePoint);
        } else {
            if (transform.length != 9) {
                throw new IllegalArgumentException("Transform must have 9 entries! Has "
                        + transform.length);
            }
            mTransform = transform;
        }
        mInverseTransform = inverse3x3(mTransform);

        mOETF = oetf;
        mEOTF = eotf;

        mMin = min;
        mMax = max;

        DoubleUnaryOperator clamp = x -> MathUtil.clamp(x, min, max);
        mClampedOETF = oetf.andThen(clamp);
        mClampedEOTF = clamp.andThen(eotf);

        mTransferParameters = transferParameters;

        // A color space is wide-gamut if its area is >90% of NTSC 1953 and
        // if it entirely contains the Color space definition in xyY
        mIsWideGamut = isWideGamut(mPrimaries, min, max);
        mIsSRGB = isSRGB(mPrimaries, mWhitePoint, oetf, eotf, id);
    }

    /**
     * Creates a copy of the specified color space with a new transform.
     *
     * @param colorSpace The color space to create a copy of
     */
    ColorSpaceRGB(@NonNull ColorSpaceRGB colorSpace,
                          @Size(9) float @NonNull [] transform,
                          @Size(min = 2, max = 3) float @NonNull [] whitePoint) {
        this(colorSpace.getName(), colorSpace.mPrimaries, whitePoint, transform,
                colorSpace.mOETF, colorSpace.mEOTF, colorSpace.mMin, colorSpace.mMax,
                colorSpace.mTransferParameters, MIN_ID);
    }

    /**
     * Compares two sets of parametric transfer functions parameters with a precision of 1e-3.
     *
     * @param a The first set of parameters to compare
     * @param b The second set of parameters to compare
     * @return True if the two sets are equal, false otherwise
     */
    private static boolean compare(
            @Nullable TransferParameters a,
            @Nullable TransferParameters b) {
        //noinspection SimplifiableIfStatement
        if (a == null && b == null) return true;
        return a != null && b != null &&
                Math.abs(a.a - b.a) < 1e-3 &&
                Math.abs(a.b - b.b) < 1e-3 &&
                Math.abs(a.c - b.c) < 1e-3 &&
                Math.abs(a.d - b.d) < 2e-3 && // Special case for variations in sRGB OETF/EOTF
                Math.abs(a.e - b.e) < 1e-3 &&
                Math.abs(a.f - b.f) < 1e-3 &&
                Math.abs(a.g - b.g) < 1e-3;
    }

    /**
     * <p>Returns a {@link Named} instance of {@link ColorSpaceRGB} that matches
     * the specified RGB to CIE XYZ transform and transfer functions. If no
     * instance can be found, this method returns null.</p>
     *
     * <p>The color transform matrix is assumed to target the CIE XYZ space
     * a {@link #ILLUMINANT_D50 D50} standard illuminant.</p>
     *
     * @param toXYZD50 3x3 column-major transform matrix from RGB to the profile
     *                 connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param function Parameters for the transfer functions
     * @return A non-null {@link ColorSpaceRGB} if a match is found, null otherwise
     */
    @Nullable
    public static ColorSpaceRGB match(
            @Size(9) float @NonNull[] toXYZD50,
            @NonNull TransferParameters function) {

        for (ColorSpace colorSpace : Named.sNamedColorSpaces) {
            if (colorSpace.getModel() == MODEL_RGB) {
                ColorSpaceRGB rgb = (ColorSpaceRGB) adapt(colorSpace, ILLUMINANT_D50_XYZ);
                if (compare(toXYZD50, rgb.mTransform) &&
                        compare(function, rgb.mTransferParameters)) {
                    return (ColorSpaceRGB) colorSpace;
                }
            }
        }

        return null;
    }

    /**
     * <p>Returns a {@link Named} instance of {@link ColorSpaceRGB} that matches
     * the specified RGB to CIE XYZ transform and transfer functions. If no
     * instance can be found, this method returns null.</p>
     *
     * @param unadaptedToXYZ      3x3 column-major transform matrix from RGB to the profile
     *                            connection space CIE XYZ as an array of 9 floats, cannot be null
     * @param unadaptedWhitePoint the unadapted white point
     * @param function            Parameters for the transfer functions
     * @return A non-null {@link ColorSpaceRGB} if a match is found, null otherwise
     */
    @Nullable
    public static ColorSpaceRGB match(
            @Size(9) float @NonNull[] unadaptedToXYZ,
            @Size(min = 2) float @NonNull[] unadaptedWhitePoint,
            @NonNull TransferParameters function) {

        float[] whitePoint = xyWhitePoint(unadaptedWhitePoint);

        for (ColorSpace colorSpace : Named.sNamedColorSpaces) {
            if (colorSpace.getModel() == MODEL_RGB) {
                ColorSpaceRGB rgb = (ColorSpaceRGB) colorSpace;
                if (compare(unadaptedToXYZ, rgb.mTransform) &&
                        compare(whitePoint, rgb.mWhitePoint) &&
                        compare(function, rgb.mTransferParameters)) {
                    return rgb;
                }
            }
        }

        return null;
    }


    /**
     * Copies the primaries of this color space in specified array. The Y
     * component is assumed to be 1 and is therefore not copied into the
     * destination. The x and y components of the first primary are written
     * in the array at positions 0 and 1 respectively.
     *
     * <p>Note: Some ColorSpaces represent gray profiles. The concept of
     * primaries for such a ColorSpace does not make sense, so we use a special
     * set of primaries that are all 1s.</p>
     *
     * @param primaries The destination array, cannot be null, its length
     *                  must be >= 6
     * @return The destination array passed as a parameter
     * @see #getPrimaries()
     */
    @Size(min = 6)
    public float @NonNull [] getPrimaries(@Size(min = 6) float @NonNull [] primaries) {
        System.arraycopy(mPrimaries, 0, primaries, 0, mPrimaries.length);
        return primaries;
    }


    /**
     * Returns the primaries of this color space as a new array of 6 floats.
     * The Y component is assumed to be 1 and is therefore not copied into
     * the destination. The x and y components of the first primary are
     * written in the array at positions 0 and 1 respectively.
     *
     * <p>Note: Some ColorSpaces represent gray profiles. The concept of
     * primaries for such a ColorSpace does not make sense, so we use a special
     * set of primaries that are all 1s.</p>
     *
     * @return A new non-null array of 6 floats
     * @see #getPrimaries(float[])
     */
    @Size(6)
    public float @NonNull [] getPrimaries() {
        return mPrimaries.clone();
    }


    /**
     * <p>Copies the transform of this color space in specified array. The
     * transform is used to convert from RGB to XYZ (with the same white
     * point as this color space). To connect color spaces, you must first
     * {@link ColorSpace#adapt(ColorSpace, float[]) adapt} them to the
     * same white point.</p>
     *
     * @param transform The destination array, cannot be null, its length
     *                  must be >= 9
     * @return The destination array passed as a parameter
     * @see #getTransform()
     */
    @Size(min = 9)
    public float @NonNull [] getTransform(@Size(min = 9) float @NonNull [] transform) {
        System.arraycopy(mTransform, 0, transform, 0, mTransform.length);
        return transform;
    }


    /**
     * <p>Returns the transform of this color space as a new array. The
     * transform is used to convert from RGB to XYZ (with the same white
     * point as this color space). To connect color spaces, you must first
     * {@link ColorSpace#adapt(ColorSpace, float[]) adapt} them to the
     * same white point.</p>
     *
     * @return A new array of 9 floats
     * @see #getTransform(float[])
     */
    @Size(9)
    public float @NonNull [] getTransform() {
        return mTransform.clone();
    }


    /**
     * <p>Copies the inverse transform of this color space in specified array.
     * The inverse transform is used to convert from XYZ to RGB (with the
     * same white point as this color space). To connect color spaces, you
     * must first {@link ColorSpace#adapt(ColorSpace, float[]) adapt} them
     * to the same white point.</p>
     *
     * @param inverseTransform The destination array, cannot be null, its length
     *                         must be >= 9
     * @return The destination array passed as a parameter
     * @see #getInverseTransform()
     */
    @Size(min = 9)
    public float @NonNull [] getInverseTransform(@Size(min = 9) float @NonNull [] inverseTransform) {
        System.arraycopy(mInverseTransform, 0, inverseTransform, 0, mInverseTransform.length);
        return inverseTransform;
    }


    /**
     * <p>Returns the inverse transform of this color space as a new array.
     * The inverse transform is used to convert from XYZ to RGB (with the
     * same white point as this color space). To connect color spaces, you
     * must first {@link ColorSpace#adapt(ColorSpace, float[]) adapt} them
     * to the same white point.</p>
     *
     * @return A new array of 9 floats
     * @see #getInverseTransform(float[])
     */
    @Size(9)
    public float @NonNull [] getInverseTransform() {
        return mInverseTransform.clone();
    }

    /**
     * <p>Returns the opto-electronic transfer function (OETF) of this color space.
     * The inverse function is the electro-optical transfer function (EOTF) returned
     * by {@link #getEOTF()}. These functions are defined to satisfy the following
     * equality for \(x \in [0..1]\):</p>
     * <p>
     * $$OETF(EOTF(x)) = EOTF(OETF(x)) = x$$
     *
     * <p>For RGB colors, this function can be used to convert from linear space
     * to "gamma space" (gamma encoded). The terms gamma space and gamma encoded
     * are frequently used because many OETFs can be closely approximated using
     * a simple power function of the form \(x^{\frac{1}{\gamma}}\) (the
     * approximation of the {@link Named#SRGB sRGB} OETF uses \(\gamma=2.2\)
     * for instance).</p>
     *
     * @return A transfer function that converts from linear space to "gamma space"
     * @see #getEOTF()
     * @see #getTransferParameters()
     */
    @NonNull
    public DoubleUnaryOperator getOETF() {
        return mClampedOETF;
    }

    /**
     * Similar to {@link #getOETF}, but not clamped to min/max per spec.
     *
     * @return A transfer function that converts from linear space to "gamma space"
     * @see #getOETF()
     */
    @NonNull
    public DoubleUnaryOperator getExtendedOETF() {
        return mOETF;
    }

    /**
     * <p>Returns the electro-optical transfer function (EOTF) of this color space.
     * The inverse function is the opto-electronic transfer function (OETF)
     * returned by {@link #getOETF()}. These functions are defined to satisfy the
     * following equality for \(x \in [0..1]\):</p>
     * <p>
     * $$OETF(EOTF(x)) = EOTF(OETF(x)) = x$$
     *
     * <p>For RGB colors, this function can be used to convert from "gamma space"
     * (gamma encoded) to linear space. The terms gamma space and gamma encoded
     * are frequently used because many EOTFs can be closely approximated using
     * a simple power function of the form \(x^\gamma\) (the approximation of the
     * {@link Named#SRGB sRGB} EOTF uses \(\gamma=2.2\) for instance).</p>
     *
     * @return A transfer function that converts from "gamma space" to linear space
     * @see #getOETF()
     * @see #getTransferParameters()
     */
    @NonNull
    public DoubleUnaryOperator getEOTF() {
        return mClampedEOTF;
    }

    /**
     * Similar to {@link #getEOTF}, but not clamped to min/max per spec.
     *
     * @return A transfer function that converts from "gamma space" to linear space
     * @see #getEOTF()
     */
    @NonNull
    public DoubleUnaryOperator getExtendedEOTF() {
        return mEOTF;
    }

    /**
     * <p>Returns the parameters used by the {@link #getEOTF() electro-optical}
     * and {@link #getOETF() opto-electronic} transfer functions. If the transfer
     * functions do not match the ICC parametric curves defined in ICC.1:2004-10
     * (section 10.15), this method returns null.</p>
     *
     * <p>See {@link TransferParameters} for a full description of the transfer
     * functions.</p>
     *
     * @return An instance of {@link TransferParameters} or null if this color
     * space's transfer functions do not match the equation defined in
     * {@link TransferParameters}
     */
    @Nullable
    public TransferParameters getTransferParameters() {
        return mTransferParameters;
    }

    @Override
    public boolean isSRGB() {
        if (mMin != 0.0f) return false;
        if (mMax != 1.0f) return false;
        return mIsSRGB;
    }

    @Override
    public boolean isExtendedSRGB() {
        return mIsSRGB;
    }

    @Override
    public boolean isWideGamut() {
        return mIsWideGamut;
    }

    @Override
    public float getMinValue(int component) {
        return mMin;
    }

    @Override
    public float getMaxValue(int component) {
        return mMax;
    }


    /**
     * <p>Decodes an RGB value to linear space. This is achieved by
     * applying this color space's electro-optical transfer function
     * to the supplied values.</p>
     *
     * <p>Refer to the documentation of {@link ColorSpaceRGB} for
     * more information about transfer functions and their use for
     * encoding and decoding RGB values.</p>
     *
     * @param r The red component to decode to linear space
     * @param g The green component to decode to linear space
     * @param b The blue component to decode to linear space
     * @return A new array of 3 floats containing linear RGB values
     * @see #toLinear(float[])
     * @see #fromLinear(float, float, float)
     */
    @Size(3)
    public float @NonNull [] toLinear(float r, float g, float b) {
        return toLinear(new float[]{r, g, b});
    }


    /**
     * <p>Decodes an RGB value to linear space. This is achieved by
     * applying this color space's electro-optical transfer function
     * to the first 3 values of the supplied array. The result is
     * stored back in the input array.</p>
     *
     * <p>Refer to the documentation of {@link ColorSpaceRGB} for
     * more information about transfer functions and their use for
     * encoding and decoding RGB values.</p>
     *
     * @param v A non-null array of non-linear RGB values, its length
     *          must be at least 3
     * @return The specified array
     * @see #toLinear(float, float, float)
     * @see #fromLinear(float[])
     */
    @Size(min = 3)
    public float @NonNull [] toLinear(@Size(min = 3) float @NonNull [] v) {
        v[0] = (float) mClampedEOTF.applyAsDouble(v[0]);
        v[1] = (float) mClampedEOTF.applyAsDouble(v[1]);
        v[2] = (float) mClampedEOTF.applyAsDouble(v[2]);
        return v;
    }

    @Size(min = 3)
    public float @NonNull [] toLinearExtended(@Size(min = 3) float @NonNull [] v) {
        v[0] = (float) mEOTF.applyAsDouble(v[0]);
        v[1] = (float) mEOTF.applyAsDouble(v[1]);
        v[2] = (float) mEOTF.applyAsDouble(v[2]);
        return v;
    }

    /**
     * <p>Encodes an RGB value from linear space to this color space's
     * "gamma space". This is achieved by applying this color space's
     * opto-electronic transfer function to the supplied values.</p>
     *
     * <p>Refer to the documentation of {@link ColorSpaceRGB} for
     * more information about transfer functions and their use for
     * encoding and decoding RGB values.</p>
     *
     * @param r The red component to encode from linear space
     * @param g The green component to encode from linear space
     * @param b The blue component to encode from linear space
     * @return A new array of 3 floats containing non-linear RGB values
     * @see #fromLinear(float[])
     * @see #toLinear(float, float, float)
     */
    @Size(3)
    public float @NonNull [] fromLinear(float r, float g, float b) {
        return fromLinear(new float[]{r, g, b});
    }


    /**
     * <p>Encodes an RGB value from linear space to this color space's
     * "gamma space". This is achieved by applying this color space's
     * opto-electronic transfer function to the first 3 values of the
     * supplied array. The result is stored back in the input array.</p>
     *
     * <p>Refer to the documentation of {@link ColorSpaceRGB} for
     * more information about transfer functions and their use for
     * encoding and decoding RGB values.</p>
     *
     * @param v A non-null array of linear RGB values, its length
     *          must be at least 3
     * @return A new array of 3 floats containing non-linear RGB values
     * @see #fromLinear(float[])
     * @see #toLinear(float, float, float)
     */
    @Size(min = 3)
    public float @NonNull [] fromLinear(@Size(min = 3) float @NonNull [] v) {
        v[0] = (float) mClampedOETF.applyAsDouble(v[0]);
        v[1] = (float) mClampedOETF.applyAsDouble(v[1]);
        v[2] = (float) mClampedOETF.applyAsDouble(v[2]);
        return v;
    }

    @Size(min = 3)
    public float @NonNull [] fromLinearExtended(@Size(min = 3) float @NonNull [] v) {
        v[0] = (float) mOETF.applyAsDouble(v[0]);
        v[1] = (float) mOETF.applyAsDouble(v[1]);
        v[2] = (float) mOETF.applyAsDouble(v[2]);
        return v;
    }

    @Override
    @Size(min = 3)
    public float @NonNull [] toXYZ(@Size(min = 3) float @NonNull [] v) {
        v[0] = (float) mClampedEOTF.applyAsDouble(v[0]);
        v[1] = (float) mClampedEOTF.applyAsDouble(v[1]);
        v[2] = (float) mClampedEOTF.applyAsDouble(v[2]);
        return mul3x3Float3(mTransform, v);
    }

    @Override
    @Size(min = 3)
    public float @NonNull [] toXYZExtended(float @NonNull [] v) {
        v[0] = (float) mEOTF.applyAsDouble(v[0]);
        v[1] = (float) mEOTF.applyAsDouble(v[1]);
        v[2] = (float) mEOTF.applyAsDouble(v[2]);
        return mul3x3Float3(mTransform, v);
    }

    @Override
    @Size(min = 3)
    public float @NonNull [] fromXYZ(@Size(min = 3) float @NonNull [] v) {
        mul3x3Float3(mInverseTransform, v);
        v[0] = (float) mClampedOETF.applyAsDouble(v[0]);
        v[1] = (float) mClampedOETF.applyAsDouble(v[1]);
        v[2] = (float) mClampedOETF.applyAsDouble(v[2]);
        return v;
    }

    @Override
    @Size(min = 3)
    public float @NonNull [] fromXYZExtended(float @NonNull [] v) {
        mul3x3Float3(mInverseTransform, v);
        v[0] = (float) mOETF.applyAsDouble(v[0]);
        v[1] = (float) mOETF.applyAsDouble(v[1]);
        v[2] = (float) mOETF.applyAsDouble(v[2]);
        return v;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(mPrimaries);
        result = 31 * result + (mMin != 0.0f ? Float.floatToIntBits(mMin) : 0);
        result = 31 * result + (mMax != 0.0f ? Float.floatToIntBits(mMax) : 0);
        result = 31 * result +
                (mTransferParameters != null ? mTransferParameters.hashCode() : 0);
        if (mTransferParameters == null) {
            result = 31 * result + mOETF.hashCode();
            result = 31 * result + mEOTF.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ColorSpaceRGB rgb = (ColorSpaceRGB) o;

        if (Float.compare(rgb.mMin, mMin) != 0) return false;
        if (Float.compare(rgb.mMax, mMax) != 0) return false;
        if (!Arrays.equals(mPrimaries, rgb.mPrimaries)) return false;
        if (!Objects.equals(mTransferParameters, rgb.mTransferParameters)) return false;
        if (!mOETF.equals(rgb.mOETF)) return false;
        return mEOTF.equals(rgb.mEOTF);
    }

    @Override
    public boolean equals(ColorSpace o, boolean extended) {
        if (!extended) {
            return equals(o);
        }
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o, true)) return false;

        ColorSpaceRGB rgb = (ColorSpaceRGB) o;

        if (!Arrays.equals(mPrimaries, rgb.mPrimaries)) return false;
        return Objects.equals(mTransferParameters, rgb.mTransferParameters);
    }

    /**
     * Computes whether a color space is the sRGB color space or at least
     * a close approximation.
     *
     * @param primaries  The set of RGB primaries in xyY as an array of 6 floats
     * @param whitePoint The white point in xyY as an array of 2 floats
     * @param oetf       The opto-electronic transfer function
     * @param eotf       The electro-optical transfer function
     * @param id         The ID of the color space
     * @return True if the color space can be considered as the sRGB color space
     * @see #isSRGB()
     */
    public static boolean isSRGB(
            @Size(6) float @NonNull [] primaries,
            @Size(2) float @NonNull [] whitePoint,
            @NonNull DoubleUnaryOperator oetf,
            @NonNull DoubleUnaryOperator eotf,
            @Range(from = MIN_ID, to = MAX_ID) int id) {
        if (id == 0) return true;
        if (!ColorSpace.compare(primaries, SRGB_PRIMARIES)) {
            return false;
        }
        if (!ColorSpace.compare(whitePoint, ILLUMINANT_D65)) {
            return false;
        }

        // We would have already returned true if this was SRGB itself, so
        // it is safe to reference it here.
        ColorSpaceRGB srgb = (ColorSpaceRGB) get(Named.SRGB);

        for (double x = 0.0; x <= 1.0; x += 1 / 255.0) {
            if (!compare(x, oetf, srgb.mOETF)) return false;
            if (!compare(x, eotf, srgb.mEOTF)) return false;
        }

        return true;
    }

    /**
     * Report whether this matrix is a special gray matrix.
     *
     * @param toXYZ A XYZ D50 matrix.
     * @return true if this is a special gray matrix.
     */
    public static boolean isGray(@Size(9) float @NonNull [] toXYZ) {
        return toXYZ.length == 9 &&
                toXYZ[1] == 0 &&
                toXYZ[2] == 0 &&
                toXYZ[3] == 0 &&
                toXYZ[5] == 0 &&
                toXYZ[6] == 0 &&
                toXYZ[7] == 0;
    }

    public static boolean compare(double point,
                                  @NonNull DoubleUnaryOperator a,
                                  @NonNull DoubleUnaryOperator b) {
        double rA = a.applyAsDouble(point);
        double rB = b.applyAsDouble(point);
        return Math.abs(rA - rB) <= 1e-3;
    }

    /**
     * Computes whether the specified CIE xyY or XYZ primaries (with Y set to 1) form
     * a wide color gamut. A color gamut is considered wide if its area is &gt; 90%
     * of the area of NTSC 1953 and if it contains the sRGB color gamut entirely.
     * If the conditions above are not met, the color space is considered as having
     * a wide color gamut if its range is larger than [0..1].
     *
     * @param primaries RGB primaries in CIE xyY as an array of 6 floats
     * @param min       The minimum value of the color space's range
     * @param max       The minimum value of the color space's range
     * @return True if the color space has a wide gamut, false otherwise
     * @see #isWideGamut()
     * @see #area(float[])
     */
    public static boolean isWideGamut(@Size(6) float @NonNull [] primaries,
                                      float min, float max) {
        return (area(primaries) / area(NTSC_1953_PRIMARIES) > 0.9f &&
                contains(primaries, SRGB_PRIMARIES)) || (min < 0.0f && max > 1.0f);
    }

    /**
     * Computes the area of the triangle represented by a set of RGB primaries
     * in the CIE xyY space.
     *
     * @param primaries The triangle's vertices, as RGB primaries in an array of 6 floats
     * @return The area of the triangle
     * @see #isWideGamut(float[], float, float)
     */
    public static float area(@Size(6) float @NonNull [] primaries) {
        float Rx = primaries[0];
        float Ry = primaries[1];
        float Gx = primaries[2];
        float Gy = primaries[3];
        float Bx = primaries[4];
        float By = primaries[5];
        float det = Rx * Gy + Ry * Bx + Gx * By - Gy * Bx - Ry * Gx - Rx * By;
        float r = 0.5f * det;
        return r < 0.0f ? -r : r;
    }

    /**
     * Computes the cross product of two 2D vectors.
     *
     * @param ax The x coordinate of the first vector
     * @param ay The y coordinate of the first vector
     * @param bx The x coordinate of the second vector
     * @param by The y coordinate of the second vector
     * @return The result of a x b
     */
    public static float cross(float ax, float ay, float bx, float by) {
        return ax * by - ay * bx;
    }

    /**
     * Decides whether a 2D triangle, identified by the 6 coordinates of its
     * 3 vertices, is contained within another 2D triangle, also identified
     * by the 6 coordinates of its 3 vertices.
     * <p>
     * In the illustration below, we want to test whether the RGB triangle
     * is contained within the triangle XYZ formed by the 3 vertices at
     * the "+" locations.
     * <pre>{@literal
     *                                  Y     .
     *                              .   +    .
     *                               .     ..
     *                                .   .
     *                                 . .
     *                                  .  G
     *                                  *
     *                                 * *
     *                               **   *
     *                              *      **
     *                             *         *
     *                           **           *
     *                          *              *
     *                         *                *
     *                       **                  *
     *                      *                     *
     *                     *                       **
     *                   **                          *   R    ...
     *                  *                             *  .....
     *                 *                         ***** ..
     *               **              ************       .   +
     *           B  *    ************                    .   X
     *        ......*****                                 .
     *  ......    .                                        .
     *          ..
     *     +   .
     *   Z    .
     * }</pre>
     * RGB is contained within XYZ if all the following conditions are true
     * (with "x" the cross product operator):
     * <pre>
     *   -->  -->
     *   GR x RX >= 0
     *   -->  -->
     *   RX x BR >= 0
     *   -->  -->
     *   RG x GY >= 0
     *   -->  -->
     *   GY x RG >= 0
     *   -->  -->
     *   RB x BZ >= 0
     *   -->  -->
     *   BZ x GB >= 0
     * </pre>
     *
     * @param p1 The enclosing triangle
     * @param p2 The enclosed triangle
     * @return True if the triangle p1 contains the triangle p2
     * @see #isWideGamut(float[], float, float)
     */
    @SuppressWarnings("RedundantIfStatement")
    public static boolean contains(@Size(6) float @NonNull [] p1, @Size(6) float @NonNull [] p2) {
        // Translate the vertices p1 in the coordinates system
        // with the vertices p2 as the origin
        float[] p0 = {
                p1[0] - p2[0], p1[1] - p2[1],
                p1[2] - p2[2], p1[3] - p2[3],
                p1[4] - p2[4], p1[5] - p2[5],
        };
        // Check the first vertex of p1
        if (cross(p0[0], p0[1], p2[0] - p2[4], p2[1] - p2[5]) < 0 ||
                cross(p2[0] - p2[2], p2[1] - p2[3], p0[0], p0[1]) < 0) {
            return false;
        }
        // Check the second vertex of p1
        if (cross(p0[2], p0[3], p2[2] - p2[0], p2[3] - p2[1]) < 0 ||
                cross(p2[2] - p2[4], p2[3] - p2[5], p0[2], p0[3]) < 0) {
            return false;
        }
        // Check the third vertex of p1
        if (cross(p0[4], p0[5], p2[4] - p2[2], p2[5] - p2[3]) < 0 ||
                cross(p2[4] - p2[0], p2[5] - p2[1], p0[4], p0[5]) < 0) {
            return false;
        }
        return true;
    }


    /**
     * Computes the primaries  of a color space identified only by
     * its RGB->XYZ transform matrix. This method assumes that the
     * range of the color space is [0..1].
     *
     * @param toXYZ The color space's 3x3 transform matrix to XYZ
     * @return A new array of 6 floats containing the color space's
     * primaries in CIE xyY
     */
    @Size(6)
    public static float @NonNull [] computePrimaries(@Size(9) float @NonNull [] toXYZ) {
        float[] r = mul3x3Float3(toXYZ, new float[]{1.0f, 0.0f, 0.0f});
        float[] g = mul3x3Float3(toXYZ, new float[]{0.0f, 1.0f, 0.0f});
        float[] b = mul3x3Float3(toXYZ, new float[]{0.0f, 0.0f, 1.0f});

        float rSum = r[0] + r[1] + r[2];
        float gSum = g[0] + g[1] + g[2];
        float bSum = b[0] + b[1] + b[2];

        return new float[]{
                r[0] / rSum, r[1] / rSum,
                g[0] / gSum, g[1] / gSum,
                b[0] / bSum, b[1] / bSum,
        };
    }


    /**
     * Computes the white point of a color space identified only by
     * its RGB->XYZ transform matrix. This method assumes that the
     * range of the color space is [0..1].
     *
     * @param toXYZ The color space's 3x3 transform matrix to XYZ
     * @return A new array of 2 floats containing the color space's
     * white point in CIE xyY
     */
    @Size(2)
    public static float @NonNull [] computeWhitePoint(@Size(9) float @NonNull [] toXYZ) {
        float[] w = mul3x3Float3(toXYZ, new float[]{1.0f, 1.0f, 1.0f});
        float sum = w[0] + w[1] + w[2];
        return new float[]{w[0] / sum, w[1] / sum};
    }


    /**
     * Converts the specified RGB primaries point to xyY if needed. The primaries
     * can be specified as an array of 6 floats (in CIE xyY) or 9 floats
     * (in CIE XYZ). If no conversion is needed, the input array is copied.
     *
     * @param primaries The primaries in xyY or XYZ
     * @return A new array of 6 floats containing the primaries in xyY
     */
    @Size(6)
    public static float @NonNull [] xyPrimaries(@Size(min = 6, max = 9) float @NonNull [] primaries) {
        float[] xyPrimaries = new float[6];

        // XYZ to xyY
        if (primaries.length == 9) {
            float sum;

            sum = primaries[0] + primaries[1] + primaries[2];
            xyPrimaries[0] = primaries[0] / sum;
            xyPrimaries[1] = primaries[1] / sum;

            sum = primaries[3] + primaries[4] + primaries[5];
            xyPrimaries[2] = primaries[3] / sum;
            xyPrimaries[3] = primaries[4] / sum;

            sum = primaries[6] + primaries[7] + primaries[8];
            xyPrimaries[4] = primaries[6] / sum;
            xyPrimaries[5] = primaries[7] / sum;
        } else {
            System.arraycopy(primaries, 0, xyPrimaries, 0, 6);
        }

        return xyPrimaries;
    }


    /**
     * Computes the matrix that converts from RGB to XYZ based on RGB
     * primaries and a white point, both specified in the CIE xyY space.
     * The Y component of the primaries and white point is implied to be 1.
     *
     * @param primaries  The RGB primaries in xyY, as an array of 6 floats
     * @param whitePoint The white point in xyY, as an array of 2 floats
     * @return A 3x3 matrix as a new array of 9 floats
     */
    @Size(9)
    public static float @NonNull [] computeXYZMatrix(
            @Size(6) float @NonNull [] primaries,
            @Size(2) float @NonNull [] whitePoint) {
        float Rx = primaries[0];
        float Ry = primaries[1];
        float Gx = primaries[2];
        float Gy = primaries[3];
        float Bx = primaries[4];
        float By = primaries[5];
        float Wx = whitePoint[0];
        float Wy = whitePoint[1];

        float oneRxRy = (1 - Rx) / Ry;
        float oneGxGy = (1 - Gx) / Gy;
        float oneBxBy = (1 - Bx) / By;
        float oneWxWy = (1 - Wx) / Wy;

        float RxRy = Rx / Ry;
        float GxGy = Gx / Gy;
        float BxBy = Bx / By;
        float WxWy = Wx / Wy;

        float BY =
                ((oneWxWy - oneRxRy) * (GxGy - RxRy) - (WxWy - RxRy) * (oneGxGy - oneRxRy)) /
                        ((oneBxBy - oneRxRy) * (GxGy - RxRy) - (BxBy - RxRy) * (oneGxGy - oneRxRy));
        float GY = (WxWy - RxRy - BY * (BxBy - RxRy)) / (GxGy - RxRy);
        float RY = 1 - GY - BY;

        float RYRy = RY / Ry;
        float GYGy = GY / Gy;
        float BYBy = BY / By;

        return new float[]{
                RYRy * Rx, RY, RYRy * (1 - Rx - Ry),
                GYGy * Gx, GY, GYGy * (1 - Gx - Gy),
                BYBy * Bx, BY, BYBy * (1 - Bx - By)
        };
    }
}

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

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleUnaryOperator;

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
public class TransferFunction {

    public static final TransferFunction SRGB =
            new TransferFunction(1 / 1.055013444855428, 0.055013444855428 / 1.055013444855428, 1 / 12.92, 0.04045, 2.4);
    /**
     * This is exactly the inverse of BT.709 OETF
     */
    public static final TransferFunction SMPTE_170M =
            new TransferFunction(1 / 1.099296826809442, 0.099296826809442 / 1.099296826809442, 1 / 4.5, 0.0812428582986315, 1 / 0.45);
    public static final TransferFunction SMPTE_240M =
            new TransferFunction(1 / 1.111466795495574, 0.111466795495574 / 1.111466795495574, 1 / 4.0, 0.0912880151220584, 1 / 0.45);
    public static final TransferFunction LINEAR =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 1.0);
    public static final TransferFunction GAMMA_2_2 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.2);
    /**
     * BT.1886 EOTF
     */
    public static final TransferFunction GAMMA_2_4 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.4);
    public static final TransferFunction GAMMA_2_6 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.6);
    public static final TransferFunction GAMMA_2_8 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.8);

    /**
     * @see #makePQ(double)
     */
    public static final double TYPE_PQ = -5.0;
    /**
     * @see #makeHLG(double, double, double)
     */
    public static final double TYPE_HLG = -6.0;

    /**
     * If the g is special, the function no longer represents the ICC parametric curve,
     * but PQ or HLG. Reference luminance is stored in {@link #a}; peak luminance
     * is stored in {@link #b}; system gamma is stored in {@link #c}; d,e,f are zeros.
     */
    public static boolean isSpecialG(double g) {
        return g == TYPE_PQ || g == TYPE_HLG;
    }

    /**
     * Create a special transfer function that represents SMPTE ST2084
     * (perceptual quantization) EOTF/OETF.
     * <p>
     * When performing EOTF, the given reference white (1.0) is mapped to 10000 cd/m².
     * When performing OETF, 10000 cd/m² (1.0) is mapped to the given reference white.
     *
     * @param referenceLuminance the reference white luminance in cd/m², also known as SDR white level
     */
    public static @NonNull TransferFunction makePQ(double referenceLuminance) {
        return new TransferFunction(referenceLuminance, 10000.0, 1.0, 0.0, TYPE_PQ);
    }

    /**
     * Create a special transfer function that represents ARIB STD-B67
     * (hybrid log gamma) EOTF/OETF.
     * <p>
     * When performing EOTF, first peforms OOTF, the given reference white (1.0) is mapped to peak luminance.
     * When performing OETF, peak luminance (1.0) is mapped to the given reference white, and performs OOTF.
     *
     * @param referenceLuminance the reference white luminance in cd/m², also known as SDR white level
     * @param peakLuminance      the peak white luminance in cd/m²
     * @param systemGamma        the system gamma for the OOTF
     */
    public static @NonNull TransferFunction makeHLG(double referenceLuminance,
                                                    double peakLuminance,
                                                    double systemGamma) {
        return new TransferFunction(referenceLuminance, peakLuminance, systemGamma, 0.0, TYPE_HLG);
    }

    // reference
    public static final TransferFunction PQ = makePQ(203.0);
    public static final TransferFunction HLG = makeHLG(203.0, 1000.0, 1.2);

    @Contract(pure = true)
    public static @Nullable TransferFunction fromCICP(int transfer, boolean useBT1886) {
        TransferFunction tf;
        switch (transfer) {
            case Color.TRANSFER_CHARACTERISTICS_BT709,
                 Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                 Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT -> {
                tf = useBT1886 ? GAMMA_2_4 : SMPTE_170M;
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470M -> {
                tf = GAMMA_2_2;
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470BG -> {
                tf = GAMMA_2_8;
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE240M -> {
                tf = SMPTE_240M;
            }
            case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                // there's no difference between non-extended and extended version
                tf = LINEAR;
            }
            case Color.TRANSFER_CHARACTERISTICS_LOG,
                 Color.TRANSFER_CHARACTERISTICS_LOG_SQRT -> {
                // no support
                return null;
            }
            case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                // there's no difference between non-extended and extended version
                tf = SMPTE_170M;
            }
            case Color.TRANSFER_CHARACTERISTICS_BT1361_ECG -> {
                // this is deprecated in favor of IEC 61966-2-4
                // we don't support it as well...
                return null;
            }
            case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1 -> {
                tf = SRGB;
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE2084 -> {
                tf = PQ;
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE428 -> {
                // there's scaling coefficient we don't care
                tf = GAMMA_2_6;
            }
            case Color.TRANSFER_CHARACTERISTICS_ARIB_STD_B67 -> {
                tf = HLG;
            }
            default -> {
                return null;
            }
        }
        return tf;
    }

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
    public TransferFunction(double a, double b, double c, double d, double g) {
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
    public TransferFunction(double a, double b, double c, double d, double e,
                            double f, double g) {

        if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c) ||
                Double.isNaN(d) || Double.isNaN(e) || Double.isNaN(f) ||
                Double.isNaN(g)) {
            throw new IllegalArgumentException("Parameters cannot be NaN");
        }

        if (isSpecialG(g)) {
            if (!(a > 0.0 && b > 0.0 && c > 0.0)) {
                throw new IllegalArgumentException("Parameter a,b,c must be positive");
            }
            if (!(d == 0.0 && e == 0.0 && f == 0.0)) {
                throw new IllegalArgumentException("Parameter d,e,f must be zero");
            }
        } else {
            // Next representable float after 1.0
            // We use doubles here but the representation inside our shader code is floats
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
        }

        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
        this.g = g;
    }

    /**
     * Returns a new OETF representing the inverse of this function and
     * capable of handling extended range colors. The returned object
     * holds a strong reference to this.
     */
    public @NonNull DoubleUnaryOperator toOETF() {
        if (g == TYPE_PQ) {
            return x -> absRcpResponsePQ(x * a / b);
        }
        if (g == TYPE_HLG) {
            return x -> absRcpResponseHLG(x * a / b);
        }
        if (e == 0.0 && f == 0.0) {
            if (a == 1.0 && b == 0.0 &&
                    c == 0.0 && d == 0.0) {
                if (g == 1.0) {
                    return DoubleUnaryOperator.identity();
                }
                return x -> absRcpResponse(x, g);
            }
            return x -> absRcpResponse(x, a, b,
                    c, d, g);
        }
        return x -> absRcpResponse(x, a, b, c,
                d, e, f, g);
    }

    /**
     * Returns a new EOTF representing this function and
     * capable of handling extended range colors. The returned object
     * holds a strong reference to this.
     */
    public @NonNull DoubleUnaryOperator toEOTF() {
        if (g == TYPE_PQ) {
            return x -> absResponsePQ(x * b / a);
        }
        if (g == TYPE_HLG) {
            return x -> absResponseHLG(x * b / a);
        }
        if (e == 0.0 && f == 0.0) {
            if (a == 1.0 && b == 0.0 &&
                    c == 0.0 && d == 0.0) {
                if (g == 1.0) {
                    return DoubleUnaryOperator.identity();
                }
                return x -> absResponse(x, g);
            }
            return x -> absResponse(x, a, b,
                    c, d, g);
        }
        return x -> absResponse(x, a, b, c,
                d, e, f, g);
    }

    /**
     * A fallback method used to compute an exponential approximation of EOTF.
     *
     * @param visual true to use visual approx, false to use linear approx
     */
    public double getGammaApprox(boolean visual) {
        // brute force results...
        if (compare(this, SRGB)) {
            return visual ? 2.208 : 2.239;
        }
        if (compare(this, SMPTE_170M)) {
            return visual ? 1.921 : 1.961;
        }
        if (compare(this, SMPTE_240M)) {
            return visual ? 1.890 : 1.933;
        }
        if (a == 1.0 && b == 0.0 &&
                c == 0.0 && d == 0.0 && e == 0.0 && f == 0.0) {
            return g;
        }
        // fallback to 2.2 exact
        return 2.2;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransferFunction that = (TransferFunction) o;

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

    @Override
    public String toString() {
        return "TransferFunction{" +
                "a=" + a +
                ", b=" + b +
                ", c=" + c +
                ", d=" + d +
                ", e=" + e +
                ", f=" + f +
                ", g=" + g +
                '}';
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

    public static final double PQ_c1 =  107 / 128.0;
    public static final double PQ_c2 = 2413 / 128.0;
    public static final double PQ_c3 = 2392 / 128.0;
    public static final double PQ_m = 2523 / 32.0;
    public static final double PQ_n = 1305 / 8192.0;

    // SMPTE ST2084
    public static double rcpResponsePQ(double x) {
        double p = Math.pow(x, PQ_n);
        return Math.pow((PQ_c1 + PQ_c2 * p) / (1.0 + PQ_c3 * p), PQ_m);
    }

    // SMPTE ST2084
    public static double responsePQ(double x) {
        double p = Math.pow(x, 1.0 / PQ_m);
        return Math.pow((p - PQ_c1) / (PQ_c2 - PQ_c3 * p), 1.0 / PQ_n);
    }

    // SMPTE ST2084
    public static double absRcpResponsePQ(double x) {
        return Math.copySign(rcpResponsePQ(x < 0.0 ? -x : x), x);
    }

    // SMPTE ST2084
    public static double absResponsePQ(double x) {
        return Math.copySign(responsePQ(x < 0.0 ? -x : x), x);
    }

    public static final double HLG_a = 0.17883277;
    public static final double HLG_b = 0.28466892;
    public static final double HLG_c = 0.55991073;

    // ARIB STD-B67
    public static double rcpResponseHLG(double x) {
        return x <= 0.5 ? x * x / 3.0 : (Math.exp((x - HLG_c) / HLG_a) + HLG_b) / 12.0;
    }

    // ARIB STD-B67
    public static double responseHLG(double x) {
        return x <= 1 / 12.0 ? Math.sqrt(3.0 * x) : HLG_a * Math.log(12.0 * x - HLG_b) + HLG_c;
    }

    // ARIB STD-B67
    public static double absRcpResponseHLG(double x) {
        return Math.copySign(rcpResponseHLG(x < 0.0 ? -x : x), x);
    }

    // ARIB STD-B67
    public static double absResponseHLG(double x) {
        return Math.copySign(responseHLG(x < 0.0 ? -x : x), x);
    }

    /**
     * Compute a system gamma for HLG system.
     */
    public static double getSystemGamma(double peakLuminance, double surroundLuminance) {
        double log2 = Math.log(2);
        return 1.2 * Math.pow(1.111, Math.log(peakLuminance / 1000) / log2)
                * Math.pow(0.98, Math.log(surroundLuminance / 5) / log2);
    }

    /**
     * Compares two sets of parametric transfer functions parameters with a precision of 5e-4.
     *
     * @param a The first set of parameters to compare
     * @param b The second set of parameters to compare
     * @return True if the two sets are equal, false otherwise
     */
    public static boolean compare(
            @Nullable TransferFunction a,
            @Nullable TransferFunction b) {
        if (a == b) return true;
        return a != null && b != null &&
                Math.abs(a.a - b.a) < 5e-4 &&
                Math.abs(a.b - b.b) < 5e-4 &&
                Math.abs(a.c - b.c) < 5e-4 &&
                Math.abs(a.d - b.d) < 2e-3 && // Special case for variations in sRGB OETF/EOTF
                Math.abs(a.e - b.e) < 5e-4 &&
                Math.abs(a.f - b.f) < 5e-4 &&
                Math.abs(a.g - b.g) < 2e-3; // ICC 'curv' has error (1/512)
    }

    public static boolean compare(double point,
                                  @NonNull DoubleUnaryOperator a,
                                  @NonNull DoubleUnaryOperator b) {
        double rA = a.applyAsDouble(point);
        double rB = b.applyAsDouble(point);
        return Math.abs(rA - rB) <= 5e-4;
    }
}

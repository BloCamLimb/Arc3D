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
    public static final TransferFunction SMPTE_170M =
            new TransferFunction(1 / 1.099296826809442, 0.099296826809442 / 1.099296826809442, 1 / 4.5, 0.0812428582986315, 1 / 0.45);
    public static final TransferFunction SMPTE_240M =
            new TransferFunction(1 / 1.111466795495574, 0.111466795495574 / 1.111466795495574, 1 / 4.0, 0.0912880151220584, 1 / 0.45);
    public static final TransferFunction LINEAR =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 1.0);
    public static final TransferFunction GAMMA_2_2 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.2);
    public static final TransferFunction GAMMA_2_4 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.4);
    public static final TransferFunction GAMMA_2_6 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.6);
    public static final TransferFunction GAMMA_2_8 =
            new TransferFunction(1.0, 0.0, 0.0, 0.0, 2.8);

    @Contract(pure = true)
    public static @Nullable TransferFunction fromCICP(int transfer) {
        TransferFunction tf;
        switch (transfer) {
            case Color.TRANSFER_FUNCTION_BT709,
                 Color.TRANSFER_FUNCTION_UNSPECIFIED,
                 Color.TRANSFER_FUNCTION_SMPTE170M,
                 Color.TRANSFER_FUNCTION_BT2020_10BIT,
                 Color.TRANSFER_FUNCTION_BT2020_12BIT -> {
                tf = SMPTE_170M;
            }
            case Color.TRANSFER_FUNCTION_BT470M -> {
                tf = GAMMA_2_2;
            }
            case Color.TRANSFER_FUNCTION_BT470BG -> {
                tf = GAMMA_2_8;
            }
            case Color.TRANSFER_FUNCTION_SMPTE240M -> {
                tf = SMPTE_240M;
            }
            case Color.TRANSFER_FUNCTION_LINEAR -> {
                // there's no difference between non-extended and extended version
                tf = LINEAR;
            }
            case Color.TRANSFER_FUNCTION_LOG,
                 Color.TRANSFER_FUNCTION_LOG_SQRT -> {
                // no support
                return null;
            }
            case Color.TRANSFER_FUNCTION_IEC61966_2_4 -> {
                // there's no difference between non-extended and extended version
                tf = SMPTE_170M;
            }
            case Color.TRANSFER_FUNCTION_BT1361_ECG -> {
                // this is deprecated in favor of IEC 61966-2-4
                // we don't support it as well...
                return null;
            }
            case Color.TRANSFER_FUNCTION_IEC61966_2_1 -> {
                tf = SRGB;
            }
            case Color.TRANSFER_FUNCTION_SMPTE2084 -> {
                //TODO PQ
                return null;
            }
            case Color.TRANSFER_FUNCTION_SMPTE428 -> {
                // there's scaling coefficient we don't care
                tf = GAMMA_2_6;
            }
            case Color.TRANSFER_FUNCTION_ARIB_STD_B67 -> {
                //TODO HLG
                return null;
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

    /**
     * Returns a new OETF representing the inverse of this function and
     * capable of handling extended range colors. The returned object
     * holds a strong reference to this.
     */
    public @NonNull DoubleUnaryOperator toOETF() {
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
        if (a == null && b == null) return true;
        return a != null && b != null &&
                Math.abs(a.a - b.a) < 5e-4 &&
                Math.abs(a.b - b.b) < 5e-4 &&
                Math.abs(a.c - b.c) < 5e-4 &&
                Math.abs(a.d - b.d) < 1e-3 && // Special case for variations in sRGB OETF/EOTF
                Math.abs(a.e - b.e) < 5e-4 &&
                Math.abs(a.f - b.f) < 5e-4 &&
                Math.abs(a.g - b.g) < 5e-4;
    }

    public static boolean compare(double point,
                                  @NonNull DoubleUnaryOperator a,
                                  @NonNull DoubleUnaryOperator b) {
        double rA = a.applyAsDouble(point);
        double rB = b.applyAsDouble(point);
        return Math.abs(rA - rB) <= 2e-4;
    }
}

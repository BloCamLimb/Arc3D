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

import org.jspecify.annotations.NonNull;

import java.util.Arrays;

/**
 * {@usesMathJax}
 *
 * <p>List of adaptation matrices that can be used for chromatic adaptation
 * using the von Kries transform. These matrices are used to convert values
 * in the CIE XYZ space to values in the LMS space (Long Medium Short).</p>
 *
 * <p>Given an adaptation matrix \(A\), the conversion from XYZ to
 * LMS is straightforward:</p>
 * <p>
 * $$\left[ \begin{array}{c} L\\ M\\ S \end{array} \right] =
 * A \left[ \begin{array}{c} X\\ Y\\ Z \end{array} \right]$$
 *
 * <p>The complete von Kries transform \(T\) uses a diagonal matrix
 * noted \(D\) to perform the adaptation in LMS space. In addition
 * to \(A\) and \(D\), the source white point \(W1\) and the destination
 * white point \(W2\) must be specified:</p>
 * <p>
 * $$\begin{align*}
 * \left[ \begin{array}{c} L_1\\ M_1\\ S_1 \end{array} \right] &=
 * A \left[ \begin{array}{c} W1_X\\ W1_Y\\ W1_Z \end{array} \right] \\\
 * \left[ \begin{array}{c} L_2\\ M_2\\ S_2 \end{array} \right] &=
 * A \left[ \begin{array}{c} W2_X\\ W2_Y\\ W2_Z \end{array} \right] \\\
 * D &= \left[ \begin{matrix} \frac{L_2}{L_1} & 0 & 0 \\\
 * 0 & \frac{M_2}{M_1} & 0 \\\
 * 0 & 0 & \frac{S_2}{S_1} \end{matrix} \right] \\\
 * T &= A^{-1}.D.A
 * \end{align*}$$
 *
 * <p>As an example, the resulting matrix \(T\) can then be used to
 * perform the chromatic adaptation of sRGB XYZ transform from D65
 * to D50:</p>
 * <p>
 * $$sRGB_{D50} = T.sRGB_{D65}$$
 *
 * @see ColorTransform
 */
public final class ChromaticAdaptation {

    /**
     * Bradford chromatic adaptation transform, as defined in the
     * CIECAM97s color appearance model.
     */
    @NonNull
    public static final ChromaticAdaptation BRADFORD = new ChromaticAdaptation(new float[]{
            0.8951f, -0.7502f, 0.0389f,
            0.2664f, 1.7135f, -0.0685f,
            -0.1614f, 0.0367f, 1.0296f
    });

    /**
     * von Kries chromatic adaptation transform.
     */
    @NonNull
    public static final ChromaticAdaptation VON_KRIES = new ChromaticAdaptation(new float[]{
            0.40024f, -0.22630f, 0.00000f,
            0.70760f, 1.16532f, 0.00000f,
            -0.08081f, 0.04570f, 0.91822f
    });

    /**
     * CIECAT02 chromatic adaption transform, as defined in the
     * CIECAM02 color appearance model.
     */
    @NonNull
    public static final ChromaticAdaptation CIECAT02 = new ChromaticAdaptation(new float[]{
            0.7328f, -0.7036f, 0.0030f,
            0.4296f, 1.6975f, 0.0136f,
            -0.1624f, 0.0061f, 0.9834f
    });

    final float[] mTransform;
    final float[] mInverseTransform;

    public ChromaticAdaptation(@Size(9) float @NonNull [] transform) {
        if (transform.length != 9) {
            throw new IllegalArgumentException("Transform must have 9 entries! Has "
                    + transform.length);
        }
        mTransform = transform;
        mInverseTransform = ColorSpace.inverse3x3(mTransform);
    }

    /**
     * <p>Computes the chromatic adaptation transform from the specified
     * source white point to the specified destination white point.</p>
     *
     * @param srcWhitePoint The white point to adapt from
     * @param dstWhitePoint The white point to adapt to
     * @return A 3x3 matrix as a non-null array of 9 floats
     */
    @Size(9)
    public float @NonNull [] computeTransform(@Size(min = 2, max = 3) float @NonNull [] srcWhitePoint,
                                              @Size(min = 2, max = 3) float @NonNull [] dstWhitePoint) {
        if ((srcWhitePoint.length != 2 && srcWhitePoint.length != 3)
                || (dstWhitePoint.length != 2 && dstWhitePoint.length != 3)) {
            throw new IllegalArgumentException("A white point array must have 2 or 3 floats");
        }
        float[] srcXYZ = srcWhitePoint.length == 3 ?
                Arrays.copyOf(srcWhitePoint, 3) : ColorSpace.xyYToXYZ(srcWhitePoint);
        float[] dstXYZ = dstWhitePoint.length == 3 ?
                Arrays.copyOf(dstWhitePoint, 3) : ColorSpace.xyYToXYZ(dstWhitePoint);

        if (ColorSpace.compare(srcXYZ, dstXYZ)) {
            return new float[]{
                    1.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f
            };
        }
        return computeTransform(mTransform, mInverseTransform, srcXYZ, dstXYZ);
    }

    /**
     * <p>Computes the chromatic adaptation transform from the specified
     * source white point to the specified destination white point.</p>
     *
     * <p>The transform is computed using the von Kries method, described
     * in more details in the documentation of {@link ChromaticAdaptation}. The
     * {@link ChromaticAdaptation} enum provides different matrices that can be
     * used to perform the adaptation.</p>
     *
     * @param matrix        The adaptation matrix
     * @param inverseMatrix The inverse of the adaptation matrix
     * @param srcWhitePoint The white point to adapt from, *will be modified*
     * @param dstWhitePoint The white point to adapt to, *will be modified*
     * @return A 3x3 matrix as a non-null array of 9 floats
     */
    @Size(9)
    static float @NonNull [] computeTransform(@Size(9) float @NonNull [] matrix,
                                              @Size(9) float @NonNull [] inverseMatrix,
                                              @Size(3) float @NonNull [] srcWhitePoint,
                                              @Size(3) float @NonNull [] dstWhitePoint) {
        float[] srcLMS = ColorSpace.mul3x3Float3(matrix, srcWhitePoint);
        float[] dstLMS = ColorSpace.mul3x3Float3(matrix, dstWhitePoint);
        // LMS is a diagonal matrix stored as a float[3]
        float[] LMS = {dstLMS[0] / srcLMS[0], dstLMS[1] / srcLMS[1], dstLMS[2] / srcLMS[2]};
        return ColorSpace.mul3x3(inverseMatrix, ColorSpace.mul3x3Diag(LMS, matrix));
    }

    @Size(min = 9)
    public float @NonNull [] getTransform(@Size(min = 9) float @NonNull [] transform) {
        System.arraycopy(mTransform, 0, transform, 0, mTransform.length);
        return transform;
    }

    @Size(9)
    public float @NonNull [] getTransform() {
        return mTransform.clone();
    }

    @Size(min = 9)
    public float @NonNull [] getInverseTransform(@Size(min = 9) float @NonNull [] inverseTransform) {
        System.arraycopy(mInverseTransform, 0, inverseTransform, 0, mInverseTransform.length);
        return inverseTransform;
    }

    @Size(9)
    public float @NonNull [] getInverseTransform() {
        return mInverseTransform.clone();
    }
}

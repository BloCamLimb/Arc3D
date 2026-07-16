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
import org.jspecify.annotations.Nullable;

/**
 * {@usesMathJax}
 *
 * <p>A connector transforms colors from a source color space to a destination
 * color space.</p>
 *
 * <p>A source color space is connected to a destination color space using the
 * color transform \(C\) computed from their respective transforms noted
 * \(T_{src}\) and \(T_{dst}\) in the following equation:</p>
 * <p>
 * $$C = T^{-1}_{dst} . T_{src}$$
 *
 * <p>The transform \(C\) shown above is only valid when the source and
 * destination color spaces have the same profile connection space (PCS).
 * We know that instances of {@link ColorSpace} always use CIE XYZ as their
 * PCS but their white points might differ. When they do, we must perform
 * a chromatic adaptation of the color spaces' transforms. To do so, we
 * use the von Kries method described in the documentation of
 * {@link ChromaticAdaptation}.</p>
 *
 * <p>Example of conversion from {@link ColorSpaces#SRGB sRGB} to
 * {@link ColorSpaces#DCI_P3 DCI-P3}:</p>
 *
 * <pre class="prettyprint">
 * ColorTransform transform = new ColorTransform(
 *         ColorSpaces.SRGB,
 *         ColorSpaces.DCI_P3);
 * float[] p3 = transform.transform(1.0f, 0.0f, 0.0f);
 * // p3 contains { 0.9473, 0.2740, 0.2076 }
 * </pre>
 *
 * <p>A render intent determines how a {@link ColorTransform connector}
 * maps colors from one color space to another. The choice of mapping is
 * important when the source color space has a larger color gamut than the
 * destination color space.</p>
 *
 * @see ChromaticAdaptation
 * @see RGBColorSpace#adapt(RGBColorSpace, float[], ChromaticAdaptation)
 * @see RGBColorSpace#adapt(RGBColorSpace, float[])
 */
public final class ColorTransform {

    // these constants match ICC Profile Rendering Intent
    /**
     * <p>Compresses the source gamut into the destination gamut.
     * This render intent affects all colors, inside and outside
     * of destination gamut. The goal of this render intent is
     * to preserve the visual relationship between colors.</p>
     *
     * <p class="note">This render intent is currently not
     * implemented and behaves like {@link #RELATIVE_COLORIMETRIC}.</p>
     */
    public static final int PERCEPTUAL = 0;
    /**
     * Similar to the {@link #ABSOLUTE_COLORIMETRIC} render intent, this render
     * intent matches the closest color in the destination gamut
     * but makes adjustments for the destination white point.
     */
    public static final int RELATIVE_COLORIMETRIC = 1;
    /**
     * <p>Attempts to maintain the relative saturation of colors
     * from the source gamut to the destination gamut, to keep
     * highly saturated colors as saturated as possible.</p>
     *
     * <p class="note">This render intent is currently not
     * implemented and behaves like {@link #RELATIVE_COLORIMETRIC}.</p>
     */
    public static final int SATURATION = 2;
    /**
     * Colors that are in the destination gamut are left unchanged.
     * Colors that fall outside the destination gamut are mapped
     * to the closest possible color within the gamut of the destination
     * color space (they are clipped).
     */
    public static final int ABSOLUTE_COLORIMETRIC = 3;

    @NonNull
    private final ColorSpace mSource;
    @NonNull
    private final ColorSpace mDestination;

    // Optimized connector for RGB/XYZ -> RGB/XYZ conversions.
    private final @Nullable RGBColorSpace mSourceRGB;
    private final @Nullable RGBColorSpace mDestinationRGB;

    private final int mIntent;

    @Size(9)
    private final float @Nullable [] mTransform;

    @Size(4)
    private final float @Nullable [] mSourceOOTF;
    @Size(4)
    private final float @Nullable [] mDestinationOOTF;

    /**
     * <p>Creates a new transform between a source and a destination color space.
     * If the source and destination color spaces do not have the same white point,
     * they are chromatically adapted from source to destination using the von Kries
     * transform and the {@link ChromaticAdaptation#BRADFORD} matrix.</p>
     *
     * <p>Colors are mapped from the source color space to the destination color
     * space using the {@link #RELATIVE_COLORIMETRIC} render intent.</p>
     *
     * <p>This constructor will NOT check if the source and destination are the same
     * and then use identity transformations. Caller should use {@link ColorSpace#equals}
     * to do so.</p>
     *
     * @param source      The source color space, cannot be null
     * @param destination The destination color space, cannot be null
     * @see #ColorTransform(ColorSpace, ColorSpace, int, ChromaticAdaptation)
     */
    public ColorTransform(@NonNull ColorSpace source,
                          @NonNull ColorSpace destination) {
        this(source, destination, RELATIVE_COLORIMETRIC);
    }

    /**
     * <p>Creates a new transform between a source and a destination color space.
     * If the source and destination color spaces do not have the same white point,
     * they are chromatically adapted from source to destination using the von Kries
     * transform and the {@link ChromaticAdaptation#BRADFORD} matrix.</p>
     *
     * <p>This constructor will NOT check if the source and destination are the same
     * and then use identity transformations. Caller should use {@link ColorSpace#equals}
     * to do so.</p>
     *
     * @param source      The source color space, cannot be null
     * @param destination The destination color space, cannot be null
     * @param intent      The render intent to use when compressing gamuts
     * @see #ColorTransform(ColorSpace, ColorSpace, int, ChromaticAdaptation)
     */
    public ColorTransform(@NonNull ColorSpace source,
                          @NonNull ColorSpace destination,
                          int intent) {
        this(source, destination, intent, ChromaticAdaptation.BRADFORD);
    }

    /**
     * <p>Creates a new transform between a source and a destination color space.
     * If the source and destination color spaces do not have the same white point,
     * they are chromatically adapted from source to destination using the von Kries
     * transform and the given adaptation matrix.</p>
     *
     * <p>This constructor will NOT check if the source and destination are the same
     * and then use identity transformations. Caller should use {@link ColorSpace#equals}
     * to do so.</p>
     *
     * @param source      The source color space, cannot be null
     * @param destination The destination color space, cannot be null
     * @param intent      The render intent to use when compressing gamuts
     * @param adaptation  The adaptation matrix
     */
    public ColorTransform(
            @NonNull ColorSpace source,
            @NonNull ColorSpace destination,
            int intent,
            @NonNull ChromaticAdaptation adaptation) {
        mSource = source;
        mDestination = destination;
        mSourceRGB = source.getModel() == ColorSpace.MODEL_RGB
                ? (RGBColorSpace) source : null;
        mDestinationRGB = destination.getModel() == ColorSpace.MODEL_RGB
                ? (RGBColorSpace) destination : null;
        mIntent = intent;
        var transform = computeTransform(source, destination, intent, adaptation);
        float scale = computeYScale(mSourceRGB, mDestinationRGB);
        if (scale != 1) {
            if (transform != null) {
                for (int i = 0; i < transform.length; i++) {
                    transform[i] *= scale;
                }
            } else {
                transform = new float[]{
                        scale, 0, 0,
                        0, scale, 0,
                        0, 0, scale
                };
            }
        }
        mTransform = transform;
        mSourceOOTF = computeOOTF(mSourceRGB, false);
        mDestinationOOTF = computeOOTF(mDestinationRGB, true);
    }

    /**
     * <p>Computes the gamut compressing and chromatic adaptation transform that
     * connects two color spaces, XYZ to RGB spaces, RGB to XYZ spaces.</p>
     *
     * <p>We can only connect color spaces if they use the same profile
     * connection space. We assume the connection space is always
     * CIE XYZ but we maybe need to perform a chromatic adaptation to
     * match the white points. The unmatched color space is adapted
     * using the von Kries transform and the given adaptation matrix.</p>
     *
     * @param source      The source color space
     * @param destination The destination color space
     * @param intent      The render intent to use when compressing gamuts
     * @param adaptation  The adaptation matrix
     * @return An array of 9 floats containing the 3x3 matrix transform, null means identity
     */
    @Size(9)
    public static float @Nullable [] computeTransform(
            @NonNull ColorSpace source,
            @NonNull ColorSpace destination,
            int intent,
            @NonNull ChromaticAdaptation adaptation) {
        var srcRGB = source.getModel() == ColorSpace.MODEL_RGB ? (RGBColorSpace) source : null;
        var dstRGB = destination.getModel() == ColorSpace.MODEL_RGB ? (RGBColorSpace) destination : null;
        if (srcRGB != null && dstRGB != null) {
            // RGB->RGB
            boolean whitePointMatch = ColorSpace.compare(source.mWhitePoint, destination.mWhitePoint);
            if (whitePointMatch || intent == ABSOLUTE_COLORIMETRIC) {
                if (whitePointMatch && ColorSpace.compare(srcRGB.mPrimaries, dstRGB.mPrimaries)) {
                    return null;
                } else {
                    return ColorSpace.mul3x3(dstRGB.mInverseTransform, srcRGB.mTransform);
                }
            } else {
                float[] adaptationTransform = ChromaticAdaptation.computeTransform(
                        adaptation.mTransform, adaptation.mInverseTransform,
                        ColorSpace.xyYToXYZ(source.mWhitePoint),
                        ColorSpace.xyYToXYZ(destination.mWhitePoint));
                return ColorSpace.mul3x3(dstRGB.mInverseTransform,
                        ColorSpace.mul3x3(adaptationTransform, srcRGB.mTransform));
            }
        } else if (srcRGB == null && dstRGB != null) {
            // XYZ->RGB
            if (intent == ABSOLUTE_COLORIMETRIC ||
                    ColorSpace.compare(destination.mWhitePoint, source.mWhitePoint)) {
                return dstRGB.getInverseTransform();
            }
            float[] adaptationTransform = ChromaticAdaptation.computeTransform(
                    adaptation.mTransform, adaptation.mInverseTransform,
                    ColorSpace.xyYToXYZ(source.mWhitePoint),
                    ColorSpace.xyYToXYZ(destination.mWhitePoint));
            return ColorSpace.mul3x3(dstRGB.mInverseTransform, adaptationTransform);
        } else if (srcRGB != null) {
            // RGB->XYZ
            if (intent == ABSOLUTE_COLORIMETRIC ||
                    ColorSpace.compare(source.mWhitePoint, destination.mWhitePoint)) {
                return srcRGB.getTransform();
            }
            float[] adaptationTransform = ChromaticAdaptation.computeTransform(
                    adaptation.mTransform, adaptation.mInverseTransform,
                    ColorSpace.xyYToXYZ(source.mWhitePoint),
                    ColorSpace.xyYToXYZ(destination.mWhitePoint));
            return ColorSpace.mul3x3(adaptationTransform, srcRGB.mTransform);
        } else {
            if (intent == ABSOLUTE_COLORIMETRIC ||
                    ColorSpace.compare(source.mWhitePoint, destination.mWhitePoint)) {
                return null;
            }
            return ChromaticAdaptation.computeTransform(
                    adaptation.mTransform, adaptation.mInverseTransform,
                    ColorSpace.xyYToXYZ(source.mWhitePoint),
                    ColorSpace.xyYToXYZ(destination.mWhitePoint));
        }
    }

    /**
     * Compute the scale factor which is the amount that values in linear space
     * will be scaled to map peak luminance and reference luminance.
     *
     * @param source      the source color space
     * @param destination the destination color space
     * @return a scale factor, since preScale and postScale are same.
     */
    public static float computeYScale(@Nullable RGBColorSpace source,
                                      @Nullable RGBColorSpace destination) {
        float scale = 1;
        TransferFunction tf;
        if (source != null && (tf = source.getTransferFunction()) != null &&
                TransferFunction.isSpecialG(tf.g)) {
            // reference luminance (a) and peak luminance (b)
            scale *= (float) (tf.b / tf.a);
        }
        if (destination != null && (tf = destination.getTransferFunction()) != null &&
                TransferFunction.isSpecialG(tf.g)) {
            scale /= (float) (tf.b / tf.a);
        }
        return scale;
    }

    /**
     * Compute the parameters of the opto-optical transfer function (OOTF) used by
     * hybrid-log gamma (HLG) system. The first three components are factors used to
     * compute Y, the last component is system gamma minus one.
     * If OOTF is not needed, returns null.
     *
     * @param space an RGB color space
     * @param dst   true if the space is destination space (OOTF-OETF),
     *              false if the space is source space (EOTF-OOTF)
     * @return the OOTF parameters if needed
     */
    @Size(4)
    public static float @Nullable [] computeOOTF(@Nullable RGBColorSpace space, boolean dst) {
        TransferFunction tf;
        if (space == null || (tf = space.mTransferFunction) == null ||
                !TransferFunction.isSpecialG(tf.g)) {
            return null;
        }
        float systemGamma = (float) tf.c;
        if (systemGamma == 1) {
            return null;
        }
        float[] ootf = new float[4];
        float[] transform = space.mTransform;
        ootf[0] = transform[1];
        ootf[1] = transform[4];
        ootf[2] = transform[7];
        ootf[3] = dst ? (1 / systemGamma) - 1 : systemGamma - 1;
        return ootf;
    }

    /**
     * Returns the source color space this connector will convert from.
     *
     * @return A non-null instance of {@link ColorSpace}
     * @see #getDestination()
     */
    @NonNull
    public ColorSpace getSource() {
        return mSource;
    }

    /**
     * Returns the destination color space this connector will convert to.
     *
     * @return A non-null instance of {@link ColorSpace}
     * @see #getSource()
     */
    @NonNull
    public ColorSpace getDestination() {
        return mDestination;
    }

    /**
     * Returns the render intent this connector will use when mapping the
     * source color space to the destination color space.
     *
     * @return A render intent
     */
    public int getRenderIntent() {
        return mIntent;
    }

    /**
     * <p>Transforms the specified color from the source color space
     * to a color in the destination color space. This convenience
     * method assumes a source color model with 3 components
     * (typically RGB). To transform from color models with more than
     * 3 components, such as {@link ColorSpace#MODEL_CMYK CMYK}, use
     * {@link #transform(float[])} instead.</p>
     *
     * @param r The red component of the color to transform
     * @param g The green component of the color to transform
     * @param b The blue component of the color to transform
     * @return A new array of 3 floats containing the specified color
     * transformed from the source space to the destination space
     * @see #transform(float[])
     */
    @Size(3)
    public float @NonNull [] transform(float r, float g, float b) {
        return transform(new float[]{r, g, b});
    }

    /**
     * <p>Transforms the specified color from the source color space
     * to a color in the destination color space.</p>
     *
     * @param v A non-null array of 3 floats containing the value to transform
     *          and that will hold the result of the transform
     * @return The v array passed as a parameter, containing the specified color
     * transformed from the source space to the destination space
     * @see #transform(float, float, float)
     */
    @Size(min = 3)
    public float @NonNull [] transform(@Size(min = 3) float @NonNull [] v) {
        if (mSourceRGB != null) {
            mSourceRGB.toLinear(v);
            if (mSourceOOTF != null) {
                applyOOTF(v, mSourceOOTF);
            }
        } else {
            mSource.toXYZ(v);
        }
        if (mTransform != null) {
            ColorSpace.mul3x3Float3(mTransform, v);
        }
        if (mDestinationRGB != null) {
            if (mDestinationOOTF != null) {
                applyOOTF(v, mDestinationOOTF);
            }
            mDestinationRGB.fromLinear(v);
        } else {
            mDestination.fromXYZ(v);
        }
        return v;
    }

    /**
     * Similar to {@link #transform}, but not clamp to source and destination's
     * min/max per spec.
     *
     * @param v A non-null array of 3 floats containing the value to transform
     *          and that will hold the result of the transform
     * @return The v array passed as a parameter, containing the specified color
     * transformed from the source space to the destination space
     */
    @Size(min = 3)
    public float @NonNull [] transformExtended(@Size(min = 3) float @NonNull [] v) {
        if (mSourceRGB != null) {
            mSourceRGB.toLinearExtended(v);
            if (mSourceOOTF != null) {
                applyOOTF(v, mSourceOOTF);
            }
        } else {
            mSource.toXYZExtended(v);
        }
        if (mTransform != null) {
            ColorSpace.mul3x3Float3(mTransform, v);
        }
        if (mDestinationRGB != null) {
            if (mDestinationOOTF != null) {
                applyOOTF(v, mDestinationOOTF);
            }
            mDestinationRGB.fromLinearExtended(v);
        } else {
            mDestination.fromXYZExtended(v);
        }
        return v;
    }

    private static void applyOOTF(@Size(min = 3) float @NonNull [] v,
                                  @Size(4) float @NonNull [] ootf) {
        float lum = v[0] * ootf[0] + v[1] * ootf[1] + v[2] * ootf[2];
        // the function must be invertible, so use magnitude
        float factor = (float) Math.pow(Math.abs(lum), ootf[3]);
        v[0] *= factor;
        v[1] *= factor;
        v[2] *= factor;
    }
}

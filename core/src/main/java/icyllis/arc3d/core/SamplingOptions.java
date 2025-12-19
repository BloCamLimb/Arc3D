/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.Immutable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Immutable struct describing image sampling options (resampling or interpolation method).
 */
@Immutable
public final class SamplingOptions {

    // keep FilterModes and MipmapModes sync with SamplerDesc::Filter and SamplerDesc::MipmapMode

    /**
     * The {@code FilterMode} specifies the sampling method on transformed texture images.
     * The default is {@link #FILTER_MODE_LINEAR}.
     */
    @MagicConstant(intValues = {FILTER_MODE_NEAREST, FILTER_MODE_LINEAR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterMode {
    }

    /**
     * Single sample point (nearest neighbor).
     */
    public static final int FILTER_MODE_NEAREST = 0;    // this must be 0

    /**
     * Interpolate between 2x2 sample points (bilinear interpolation).
     */
    public static final int FILTER_MODE_LINEAR = 1;     // this must be 1

    /**
     * The {@code MipmapMode} specifies the interpolation method for MIP image levels when
     * down-sampling texture images. The default is {@link #MIPMAP_MODE_LINEAR}.
     */
    @MagicConstant(intValues = {MIPMAP_MODE_NONE, MIPMAP_MODE_NEAREST, MIPMAP_MODE_LINEAR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MipmapMode {
    }

    /**
     * Ignore mipmap levels, sample from the "base".
     */
    public static final int MIPMAP_MODE_NONE = 0;

    /**
     * Sample from the nearest level.
     */
    public static final int MIPMAP_MODE_NEAREST = 1;

    /**
     * Interpolate between the two nearest levels.
     */
    public static final int MIPMAP_MODE_LINEAR = 2;

    /**
     * Use nearest-neighbour sampling for minification, magnification; no mipmapping.
     * Also known as point sampling.
     */
    public static final SamplingOptions NEAREST = new SamplingOptions(FILTER_MODE_NEAREST);

    /**
     * Use linear interpolation for minification, magnification; no mipmapping.
     * Also known as triangle sampling and bilinear sampling.
     */
    public static final SamplingOptions LINEAR = new SamplingOptions(FILTER_MODE_LINEAR);

    /**
     * Use nearest-neighbour sampling for minification, magnification, and mip-level sampling.
     */
    public static final SamplingOptions NEAREST_MIPMAP_NEAREST = new SamplingOptions(
            FILTER_MODE_NEAREST, MIPMAP_MODE_NEAREST
    );

    public static final SamplingOptions NEAREST_MIPMAP_LINEAR = new SamplingOptions(
            FILTER_MODE_NEAREST, MIPMAP_MODE_LINEAR
    );

    public static final SamplingOptions LINEAR_MIPMAP_NEAREST = new SamplingOptions(
            FILTER_MODE_LINEAR, MIPMAP_MODE_NEAREST
    );

    /**
     * Use linear interpolation for minification, magnification, and mip-level sampling.
     */
    public static final SamplingOptions LINEAR_MIPMAP_LINEAR = new SamplingOptions(
            FILTER_MODE_LINEAR, MIPMAP_MODE_LINEAR
    );

    // common cubic sampling options

    /**
     * Use bicubic sampling, the cubic B-spline with B=1, C=0.
     */
    public static final SamplingOptions CUBIC_BSPLINE = new SamplingOptions(
            1f, 0f, 0
    );

    /**
     * Use bicubic sampling, the Mitchell–Netravali filter with B=1/3, C=1/3.
     */
    public static final SamplingOptions MITCHELL = new SamplingOptions(
            1 / 3.0f, 1 / 3.0f, 0
    );

    /**
     * Use bicubic sampling, the Photoshop bicubic filter with B=0, C=0.75.
     */
    public static final SamplingOptions PHOTOSHOP_BICUBIC = new SamplingOptions(
            0f, 0.75f, 0
    );

    /**
     * Use bicubic sampling, the Catmull-Rom spline with B=0, C=0.5.
     */
    public static final SamplingOptions CATMULLROM = new SamplingOptions(
            0f, 0.5f, 0
    );

    public final byte mFilter;
    public final byte mMipmap;
    public final boolean mUseCubic;
    public final float mCubicB;
    public final float mCubicC;
    public final int mMaxAnisotropy;

    SamplingOptions(@FilterMode int filter) {
        this(filter, filter, MIPMAP_MODE_NONE);
    }

    SamplingOptions(@FilterMode int filter, @MipmapMode int mipmap) {
        mFilter = (byte) filter;
        mMipmap = (byte) mipmap;
        mUseCubic = false;
        mCubicB = mCubicC = 0f;
        mMaxAnisotropy = 0;
    }

    SamplingOptions(float cubicB,
                    float cubicC,
                    int maxAnisotropy) {
        mFilter = FILTER_MODE_NEAREST;
        mMipmap = MIPMAP_MODE_NONE;
        mUseCubic = maxAnisotropy == 0;
        mCubicB = cubicB;
        mCubicC = cubicC;
        mMaxAnisotropy = maxAnisotropy;
    }

    public static @NonNull SamplingOptions make(@FilterMode int filter) {
        return filter == FILTER_MODE_NEAREST ? NEAREST : LINEAR;
    }

    public static @NonNull SamplingOptions make(@FilterMode int filter, @MipmapMode int mipmap) {
        return switch (filter | (mipmap << 1)) {
            case FILTER_MODE_NEAREST | (MIPMAP_MODE_NONE << 1) -> NEAREST;
            case FILTER_MODE_LINEAR | (MIPMAP_MODE_NONE << 1) -> LINEAR;
            case FILTER_MODE_NEAREST | (MIPMAP_MODE_NEAREST << 1) -> NEAREST_MIPMAP_NEAREST;
            case FILTER_MODE_LINEAR | (MIPMAP_MODE_NEAREST << 1) -> LINEAR_MIPMAP_NEAREST;
            case FILTER_MODE_NEAREST | (MIPMAP_MODE_LINEAR << 1) -> NEAREST_MIPMAP_LINEAR;
            case FILTER_MODE_LINEAR | (MIPMAP_MODE_LINEAR << 1) -> LINEAR_MIPMAP_LINEAR;
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Specify B and C (each between 0...1) to create a shader that applies the corresponding
     * cubic reconstruction filter to the image.
     */
    @Contract("_, _ -> new")
    public static @NonNull SamplingOptions makeCubic(float B, float C) {
        return new SamplingOptions(MathUtil.pin(B, 0f, 1f),
                MathUtil.pin(C, 0f, 1f), 0);
    }

    /**
     * @param maxAnisotropy the max anisotropy filtering level
     */
    @Contract("_ -> new")
    public static @NonNull SamplingOptions makeAnisotropy(int maxAnisotropy) {
        return new SamplingOptions(0f, 0f, Math.max(maxAnisotropy, 1));
    }

    public boolean isAnisotropy() {
        return mMaxAnisotropy != 0;
    }

    @Override
    public int hashCode() {
        int result = mFilter;
        result = 31 * result + (int) mMipmap;
        result = 31 * result + (mUseCubic ? 1 : 0);
        result = 31 * result + Float.floatToIntBits(mCubicB);
        result = 31 * result + Float.floatToIntBits(mCubicC);
        result = 31 * result + mMaxAnisotropy;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof SamplingOptions that) {
            return mFilter == that.mFilter &&
                    mMipmap == that.mMipmap &&
                    mUseCubic == that.mUseCubic &&
                    mCubicB == that.mCubicB &&
                    mCubicC == that.mCubicC &&
                    mMaxAnisotropy == that.mMaxAnisotropy;
        }
        return false;
    }
}

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

/**
 * Implementation of the Oklab color space. Oklab uses a D65 white point.
 */
final class ColorSpaceOklab extends ColorSpace {

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

    ColorSpaceOklab(@NonNull String name,
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
    public float @NonNull [] toXYZ(@Size(min = 3) float @NonNull [] v) {
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
    public float @NonNull [] toXYZExtended(@Size(min = 3) float @NonNull [] v) {
        mul3x3Float3(INVERSE_M2, v);
        v[0] = v[0] * v[0] * v[0];
        v[1] = v[1] * v[1] * v[1];
        v[2] = v[2] * v[2] * v[2];
        mul3x3Float3(INVERSE_M1, v);
        return v;
    }

    @Override
    public float @NonNull [] fromXYZ(@Size(min = 3) float @NonNull [] v) {
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
    public float @NonNull [] fromXYZExtended(@Size(min = 3) float @NonNull [] v) {
        mul3x3Float3(M1, v);
        v[0] = (float) Math.cbrt(v[0]);
        v[1] = (float) Math.cbrt(v[1]);
        v[2] = (float) Math.cbrt(v[2]);
        mul3x3Float3(M2, v);
        return v;
    }
}

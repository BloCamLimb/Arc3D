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
 * Implementation of the CIE L*a*b* color space. Its PCS is CIE XYZ
 * with a white point of D50.
 */
final class ColorSpaceLab extends ColorSpace {

    private static final float A = 216.0f / 24389.0f;
    private static final float B = 841.0f / 108.0f;
    private static final float C = 4.0f / 29.0f;
    private static final float D = 6.0f / 29.0f;

    ColorSpaceLab(@NonNull String name,
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
    public float @NonNull [] toXYZ(@Size(min = 3) float @NonNull [] v) {
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
    public float @NonNull [] toXYZExtended(@Size(min = 3) float @NonNull [] v) {
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
    public float @NonNull [] fromXYZ(@Size(min = 3) float @NonNull [] v) {
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
    public float @NonNull [] fromXYZExtended(@Size(min = 3) float @NonNull [] v) {
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

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
 * Implementation of the CIE XYZ color space.
 */
public final class ColorSpaceXYZ extends ColorSpace {

    public ColorSpaceXYZ(@NonNull String name, float @NonNull [] whitePoint) {
        this(name, whitePoint, MIN_ID);
    }

    ColorSpaceXYZ(@NonNull String name, float @NonNull [] whitePoint,
                  @Range(from = MIN_ID, to = MAX_ID) int id) {
        super(name, MODEL_XYZ, whitePoint, id);
    }

    @Override
    public boolean isWideGamut() {
        return true;
    }

    @Override
    public float getMinValue(@Range(from = 0, to = 3) int component) {
        return -2.0f;
    }

    @Override
    public float getMaxValue(@Range(from = 0, to = 3) int component) {
        return 2.0f;
    }


    @Override
    public float @NonNull [] toXYZ(@Size(min = 3) float @NonNull [] v) {
        v[0] = MathUtil.clamp(v[0], -2.0f, 2.0f);
        v[1] = MathUtil.clamp(v[1], -2.0f, 2.0f);
        v[2] = MathUtil.clamp(v[2], -2.0f, 2.0f);
        return v;
    }

    @Override
    public float @NonNull [] toXYZExtended(@Size(min = 3) float @NonNull [] v) {
        return v;
    }


    @Override
    public float @NonNull [] fromXYZ(@Size(min = 3) float @NonNull [] v) {
        v[0] = MathUtil.clamp(v[0], -2.0f, 2.0f);
        v[1] = MathUtil.clamp(v[1], -2.0f, 2.0f);
        v[2] = MathUtil.clamp(v[2], -2.0f, 2.0f);
        return v;
    }

    @Override
    public float @NonNull [] fromXYZExtended(@Size(min = 3) float @NonNull [] v) {
        return v;
    }
}

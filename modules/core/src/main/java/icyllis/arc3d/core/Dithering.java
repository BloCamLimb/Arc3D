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

/**
 * Dithering is used to smooth color transitions.
 * <p>
 * Input values should be non-negative integer coordinates, return values are
 * ranged from 0.0 (inclusive) to 1.0 (exclusive).
 */
public class Dithering {

    public static float bayer2(float x, float y) {
        x = (float) Math.floor(x);
        y = (float) Math.floor(y);
        return (x * .5f + y * y * .75f) % 1.0f;
    }

    public static float bayer4(float x, float y) {
        return bayer2(.5f * x, .5f * y) * .25f + bayer2(x, y);
    }

    public static float bayer8(float x, float y) {
        return bayer4(.5f * x, .5f * y) * .25f + bayer2(x, y);
    }

    public static float bayer16(float x, float y) {
        return bayer8(.5f * x, .5f * y) * .25f + bayer2(x, y);
    }

    public static float bayer32(float x, float y) {
        return bayer16(.5f * x, .5f * y) * .25f + bayer2(x, y);
    }

    public static float bayer64(float x, float y) {
        return bayer32(.5f * x, .5f * y) * .25f + bayer2(x, y);
    }

    // unreasonable effectiveness of quasirandom sequences
    // x^3 = x + 1, find the unique positive root
    // g = 1.324717957244746, alpha = (1/g, 1/g^2)
    private static final double G = 1.324717957244746;
    private static final double A1 = 1.0/G;
    private static final double A2 = 1.0/(G*G);

    // seed=0.5 is commonly used
    public static float r2(float x, float y, float seed) {
        return (float) ((x * A1 + y * A2 + seed) % 1.0);
    }
    public static float r2(float x, float y) {
        return (float) ((x * A1 + y * A2 + 0.5) % 1.0);
    }
}

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

public class YUVMath {

    /**
     * Returns a correction factor for sampling YUV data larger than 8 bits using
     * 16-bit UNORM textures. This is because its lower-order bits are padded with 0s.
     *
     * @param bits the bits, valid values are 10, 12, 14
     * @return a scaling factor
     */
    public static float getCorrectionFactor(int bits) {
        assert bits > 8 && bits <= 16;
        return 65535.0f / (65536 - (1 << (16 - bits)));
    }
}

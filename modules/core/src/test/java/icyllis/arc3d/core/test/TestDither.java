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

package icyllis.arc3d.core.test;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.Dithering;
import icyllis.arc3d.core.PixelUtils;
import org.lwjgl.system.MemoryUtil;

public class TestDither {

    public static void main(String[] args) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                System.out.printf("%.5f ", Dithering.bayer8(j, i)-0.5);
            }
            System.out.println();
        }

        System.out.println("-".repeat(16));

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                System.out.printf("%.5f ", Dithering.r2(j, i)-0.5);
            }
            System.out.println();
        }

        for (int i = 0; i < ColorInfo.CT_COUNT; i++) {
            int bpp = ColorInfo.bytesPerPixel(i);
            if (!ColorInfo.validMemoryAddress(i, null, bpp * 7)) {
                throw new AssertionError(i);
            }
        }

        long addr = MemoryUtil.nmemAlloc(16);
        float[] col = new float[4];
        for (int i = 0; i < 100000; i++) {
            PixelUtils.load_RGBA_F32_u(null, addr, col);
        }
        MemoryUtil.nmemFree(addr);
    }
}

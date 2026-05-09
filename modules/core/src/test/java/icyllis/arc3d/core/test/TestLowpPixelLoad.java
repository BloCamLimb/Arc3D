/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.*;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class TestLowpPixelLoad {

    //-XX:+UnlockDiagnosticVMOptions
    //-XX:+UnlockExperimentalVMOptions
    //-XX:CompileCommand="print *PixelUtils::store_*"
    //-XX:-TieredCompilation
    public static void main(String[] args) {
        int width = 64, height = 64;
        var info = ImageInfo.make(width, height, ColorInfo.CT_RGBA_F32, ColorInfo.AT_UNPREMUL, null);
        var pixels = PixelRef.makeAllocate(info, 0);
        Objects.requireNonNull(pixels);
        var random = new Random();
        for (long i = 0, e = info.computeMinByteSize(); i < e; i += 4) {
            MemoryUtil.memPutFloat(pixels.getAddress() + i, random.nextFloat(1));
        }
        Pixmap originalPixmap = new Pixmap(
                info,
                pixels.getBase(),
                pixels.getAddress(),
                pixels.getRowBytes()
        );
        int[] colorTypes = {ColorInfo.CT_R_8, ColorInfo.CT_RG_88, ColorInfo.CT_RGB_888, ColorInfo.CT_RGBX_8888,
                ColorInfo.CT_RGBA_8888, ColorInfo.CT_BGRA_8888, ColorInfo.CT_GRAY_16, ColorInfo.CT_GRAY_ALPHA_1616,
                ColorInfo.CT_GRAY_8, ColorInfo.CT_GRAY_ALPHA_88, ColorInfo.CT_ALPHA_8,
                ColorInfo.CT_R_16, ColorInfo.CT_RG_1616, ColorInfo.CT_RGBA_16161616, ColorInfo.CT_ALPHA_16,
                ColorInfo.CT_R_F16, ColorInfo.CT_RG_F16, ColorInfo.CT_RGBA_F16, ColorInfo.CT_ALPHA_F16,
                ColorInfo.CT_RGBA_F32, ColorInfo.CT_BGR_565, ColorInfo.CT_RGBA_1010102, ColorInfo.CT_BGRA_1010102,
                ColorInfo.CT_BGRA_5551, ColorInfo.CT_R_F32, ColorInfo.CT_RG_F32, ColorInfo.CT_RGB_161616};
        for (int ct : colorTypes) {
            testForColorType(ct, originalPixmap);
        }
        for (int ct : colorTypes) {
            Pixmap pixmap = new Pixmap(info.makeColorType(ct), originalPixmap);
            pixmap.clear(new float[]{0, 0, 0, 0}, null);
            pixmap.clear(new float[]{1, 1, 1, 0.49f}, new Rect2i(1, 6, 50, 30));
            pixmap.clear(new float[]{0.2f, 0.4f, 0.6f, 0.7f}, null);
        }
        pixels.unref();

        for (int i = 0; i < 64; i++) {
            int a = (int) (i * (63/255.0f) + .5f);
            int b = (i * 21 + 42) / 85;
            if (a != b) {
                throw new IllegalStateException();
            }
        }

        float[] col = new float[4];
        byte[] rgColors = {(byte) 252, 2, (byte) 152, 6};
        for (int i = 0; i < 100000; i++) {
            PixelUtils.load_RG_88_hb(rgColors, 2, col);
        }
        System.out.println(Arrays.toString(col));
    }

    public static void testForColorType(int ct, Pixmap originalPixmap) {
        var newInfo = originalPixmap.getInfo().makeColorType(ct);
        var newPixels = PixelRef.makeAllocate(newInfo, 0);
        Objects.requireNonNull(newPixels);

        Pixmap convertedPixmap = new Pixmap(
                newInfo, newPixels.getBase(), newPixels.getAddress(), newPixels.getRowBytes()
        );
        boolean res = PixelUtils.convertPixels(originalPixmap, convertedPixmap);
        assert res;

        PixelUtils.PixelOp load = PixelUtils.loadOp(ct, convertedPixmap.getBase() == null);
        float[] color4f = new float[4];
        for (int y = newInfo.height() - 1; y >= 0; y--) {
            for (int x = newInfo.width() - 1; x >= 0; x--) {
                int colA = convertedPixmap.getColor(x, y);
                load.op(convertedPixmap.getBase(), convertedPixmap.getAddress(x, y), color4f);
                int colB = Color.argb(color4f[3], color4f[0], color4f[1], color4f[2]);
                if (colA != colB) {
                    throw new IllegalStateException(
                            String.format("ct: %s, got: %X, expected: %X", ColorInfo.colorTypeToString(ct), colA, colB)
                    );
                }
            }
        }

        newPixels.unref();
    }
}

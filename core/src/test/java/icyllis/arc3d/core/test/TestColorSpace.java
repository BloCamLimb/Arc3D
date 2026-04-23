/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.Color;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class TestColorSpace {

    public static final Logger LOGGER = LoggerFactory.getLogger(TestColorSpace.class);

    public static void main(String[] args) {
        var cs = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.SRGB);
        float[] v = {0.4f, 0.8f, 0.7f};
        {
            float[] linear = v.clone();
            Color.GammaToLinear(linear);
            float lum = Color.luminance(linear);
            System.out.println(lum);
            System.out.println(Color.LinearToGamma(lum));
        }
        {
            float lum = cs.toXyz(v)[1];
            System.out.println(lum);
            System.out.println(cs.fromLinear(lum, lum, lum)[0]);
        }
        {
            float lum = 0.299f * v[0] + 0.587f * v[1] + 0.114f * v[2];
            // ????
            System.out.println(lum);
        }

        var sRGB = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);
        var displayP3 = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.DISPLAY_P3);


        float[] col = new float[]{1, 0, 1, 1};
        testColor(ColorSpace.get(ColorSpace.Named.DCI_P3), sRGB, col);
        testColor(displayP3, sRGB, col);
        testColor(ColorSpace.get(ColorSpace.Named.BT2020), sRGB, col);
        testColor(ColorSpace.get(ColorSpace.Named.SRGB), ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), col);

        testRgbTransform(ColorSpace.get(ColorSpace.Named.DCI_P3), sRGB);
        testRgbTransform(ColorSpace.get(ColorSpace.Named.ADOBE_RGB), sRGB);
        testRgbTransform(displayP3, sRGB);
        testRgbTransform(ColorSpace.get(ColorSpace.Named.ACESCG), sRGB);

        LOGGER.info("{}", ((ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.DCI_P3)).getTransform());
        LOGGER.info("{}", ((ColorSpace.Rgb) sRGB).getTransform());
        LOGGER.info("{}", ColorSpace.chromaticAdaptation(ColorSpace.Adaptation.BRADFORD,
                new float[]{0.314f, 0.351f}, ColorSpace.ILLUMINANT_D65));
    }

    public static void testColor(ColorSpace src, ColorSpace dst,
                                 float[] color) {
        ColorSpace.Connector connector = ColorSpace.connect(
                src, dst,
                ColorSpace.RenderIntent.RELATIVE
        );

        float[] col = connector.transformUnclamped(color.clone());

        LOGGER.info("{} to {}", src, dst);
        LOGGER.info("{} to {}, packed 0x{}L", color, col,
                Long.toHexString(pack(col[0], col[1], col[2], col[3])).toUpperCase(Locale.ROOT));
    }

    public static void testRgbTransform(ColorSpace src, ColorSpace dst) {
        ColorSpace.Rgb
                srcRGB = (ColorSpace.Rgb) src,
                dstRGB = (ColorSpace.Rgb) dst;

        LOGGER.info("{} to {}", srcRGB, dstRGB);

        LOGGER.info("Relative intent");

        LOGGER.info("New matrix {}",
                ColorSpace.Connector.Rgb.computeTransform(srcRGB, dstRGB, ColorSpace.RenderIntent.RELATIVE));

        LOGGER.info("Absolute intent");

        LOGGER.info("New matrix {}",
                ColorSpace.Connector.Rgb.computeTransform(srcRGB, dstRGB, ColorSpace.RenderIntent.ABSOLUTE));
    }

    public static long pack(float red, float green, float blue, float alpha) {
        short r = MathUtil.floatToHalf(red);
        short g = MathUtil.floatToHalf(green);
        short b = MathUtil.floatToHalf(blue);
        short a = MathUtil.floatToHalf(alpha);
        return (a & 0xffffL) << 48 |
                (b & 0xffffL) << 32 |
                (g & 0xffffL) << 16 |
                (r & 0xffffL);
    }
}

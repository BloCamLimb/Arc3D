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

import icyllis.arc3d.core.ChromaticAdaptation;
import icyllis.arc3d.core.Color;
import icyllis.arc3d.core.ColorProfile;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.RGBColorSpace;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ColorTransform;
import icyllis.arc3d.core.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class TestColorSpace {

    public static final Logger LOGGER = LoggerFactory.getLogger(TestColorSpace.class);

    public static void main(String[] args) {

        for (int i = 0; i < 1000; i++) {
            int finalI = i;
            new Thread(() -> {
                    new RGBColorSpace("A", new float[]{
                            1, 0, 0, 0, 1, 0, 0, 0, 1
                    }, 1);

            }).start();
            var cs = ColorSpaces.EXTENDED_SRGB;
        }

        var cs = ColorSpaces.SRGB;
        float[] v = {0.4f, 0.8f, 0.7f};
        {
            float[] linear = v.clone();
            Color.GammaToLinear(linear);
            float lum = Color.luminance(linear);
            System.out.println(lum);
            System.out.println(Color.LinearToGamma(lum));
        }
        {
            float lum = cs.toXYZ(v)[1];
            System.out.println(lum);
            System.out.println(cs.fromLinear(lum, lum, lum)[0]);
        }
        {
            float lum = 0.299f * v[0] + 0.587f * v[1] + 0.114f * v[2];
            // ????
            System.out.println(lum);
        }

        var sRGB = ColorSpaces.EXTENDED_SRGB;
        var displayP3 = ColorSpaces.DISPLAY_P3;


        float[] xyzMat = RGBColorSpace.computeXYZMatrix(new float[]{1, 1, 1, 1, 1, 1},
                ColorSpace.ILLUMINANT_D65);
        LOGGER.info("Gray XYZMat {}", xyzMat);
        generateICCProfile();

        float[] col = new float[]{1, 0, 1, 1};
        testColor(ColorSpaces.DCI_P3, sRGB, col);
        testColor(displayP3, sRGB, col);
        testColor(ColorSpaces.BT2020, sRGB, col);
        testColor(ColorSpaces.SRGB, ColorSpaces.LINEAR_EXTENDED_SRGB, col);
        testColor(ColorSpaces.CIE_LAB, displayP3, new float[]{55, 90, 70, 1});
        testColor(ColorSpaces.OK_LAB, displayP3, new float[]{0.733f, -0.265f, 0.088f, 1});
        testColor(ColorSpaces.OK_LAB, ColorSpaces.ACESCG, new float[]{0.733f, -0.265f, 0.088f, 1});

        testRgbTransform(ColorSpaces.DCI_P3, sRGB);
        testRgbTransform(ColorSpaces.ADOBE_RGB, sRGB);
        testRgbTransform(displayP3, sRGB);
        testRgbTransform(ColorSpaces.ACESCG, sRGB);

        LOGGER.info("{}", ColorSpaces.DCI_P3.getTransform());
        LOGGER.info("{}", sRGB.getTransform());
        LOGGER.info("{}", ChromaticAdaptation.BRADFORD.computeTransform(
                new float[]{0.314f, 0.351f}, ColorSpace.ILLUMINANT_D65));

        RGBColorSpace adaptedP3 = RGBColorSpace.adapt(ColorSpaces.ADOBE_RGB,
                ColorSpace.ILLUMINANT_D50, ChromaticAdaptation.BRADFORD);
        LOGGER.info("adapted P3 {}", adaptedP3.getTransform());

        assert ColorSpaces.SRGB.isSRGB();
        assert ColorSpaces.SRGB.isExtendedSRGB();
        assert !ColorSpaces.EXTENDED_SRGB.isSRGB();
        assert ColorSpaces.EXTENDED_SRGB.isExtendedSRGB();
        assert !ColorSpaces.LINEAR_EXTENDED_SRGB.isSRGB();
        assert !ColorSpaces.LINEAR_EXTENDED_SRGB.isExtendedSRGB();
        assert !ColorSpaces.BT709.isSRGB();
        assert !ColorSpaces.SRGB.equals(ColorSpaces.EXTENDED_SRGB);
        assert ColorSpaces.SRGB.equals(ColorSpaces.EXTENDED_SRGB, true);

        for (var that : ColorSpaces.getNamedColorSpaces()) {
            LOGGER.info("{} isWideGamut {}", that, that.isWideGamut());
        }
    }

    public static void generateICCProfile() {
        ColorProfile cp = new ColorProfile();

        RGBColorSpace p3 = ColorSpaces.DISPLAY_P3;

        cp.dataColorSpace = ICC_Profile.icSigRgbData;
        cp.rTRC_para = p3.getTransferFunction();
        cp.gTRC_para = p3.getTransferFunction();
        cp.bTRC_para = p3.getTransferFunction();

        cp.primaries = p3.getPrimaries();
        cp.whitePoint = p3.getWhitePoint();

        cp.description = p3.getName();

        cp.cicp = true;
        cp.cicp_colorPrimaries = Color.COLOR_PRIMARIES_SMPTE432;
        cp.cicp_transferCharacteristics = Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1;
        cp.cicp_videoFullRangeFlag = 1;

        writeICCProfile(cp, "my_display_p3");

        RGBColorSpace srgb = ColorSpaces.SRGB;

        cp.rTRC_para = srgb.getTransferFunction();
        cp.gTRC_para = srgb.getTransferFunction();
        cp.bTRC_para = srgb.getTransferFunction();

        cp.primaries = srgb.getPrimaries();
        cp.whitePoint = srgb.getWhitePoint();

        cp.description = srgb.getName();

        cp.cicp = true;
        cp.cicp_colorPrimaries = Color.COLOR_PRIMARIES_BT709;
        cp.cicp_transferCharacteristics = Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1;
        cp.cicp_videoFullRangeFlag = 1;

        writeICCProfile(cp, "my_srgb");
    }

    public static void writeICCProfile(ColorProfile cp, String filename) {
        byte[] data = cp.getData();

        try {
            Files.write(Path.of("run/" + filename + ".icc"), data,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.write(Path.of("run/" + filename + "_lcms_cross.icc"), ICC_Profile.getInstance(data).getData(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void testColor(ColorSpace src, ColorSpace dst,
                                 float[] color) {
        ColorTransform transform = new ColorTransform(
                src, dst,
                ColorTransform.RELATIVE_COLORIMETRIC
        );

        float[] col = transform.transformExtended(color.clone());

        LOGGER.info("{} to {}", src, dst);
        LOGGER.info("{} to {}, packed 0x{}L", color, col,
                Long.toHexString(pack(col[0], col[1], col[2], col[3])).toUpperCase(Locale.ROOT));
    }

    public static void testRgbTransform(ColorSpace src, ColorSpace dst) {
        RGBColorSpace
                srcRGB = (RGBColorSpace) src,
                dstRGB = (RGBColorSpace) dst;

        LOGGER.info("{} to {}", srcRGB, dstRGB);

        LOGGER.info("Relative intent");

        LOGGER.info("New matrix {}",
                ColorTransform.computeTransform(srcRGB, dstRGB,
                        ColorTransform.RELATIVE_COLORIMETRIC, ChromaticAdaptation.BRADFORD));

        LOGGER.info("Absolute intent");

        LOGGER.info("New matrix {}",
                ColorTransform.computeTransform(srcRGB, dstRGB,
                        ColorTransform.ABSOLUTE_COLORIMETRIC, ChromaticAdaptation.BRADFORD));
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

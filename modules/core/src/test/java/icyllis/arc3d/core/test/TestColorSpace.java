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

import icyllis.arc3d.core.*;
import org.jspecify.annotations.NonNull;
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

        /*for (int i = 0; i < 1000; i++) {
            int finalI = i;
            new Thread(() -> {
                    new RGBColorSpace("A", new float[]{
                            1, 0, 0, 0, 1, 0, 0, 0, 1
                    }, 1);

            }).start();
            var cs = ColorSpaces.EXTENDED_SRGB;
        }*/

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

        /*LOGGER.info("Gamma approx (linear) of sRGB {}",
                approx(TransferFunction.SRGB, true));
        LOGGER.info("Gamma approx (linear) of BT709 {}",
                approx(TransferFunction.SMPTE_170M, true));
        LOGGER.info("Gamma approx (linear) of SMPTE240M {}",
                approx(TransferFunction.SMPTE_240M, true));
        LOGGER.info("Gamma approx (visual) of sRGB {}",
                approx(TransferFunction.SRGB, false));
        LOGGER.info("Gamma approx (visual) of BT709 {}",
                approx(TransferFunction.SMPTE_170M, false));
        LOGGER.info("Gamma approx (visual) of SMPTE240M {}",
                approx(TransferFunction.SMPTE_240M, false));*/

        float[] xyzMat = RGBColorSpace.computeXYZMatrix(new float[]{1, 0, 0, 1, 0, 0},
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

        LOGGER.info("{}", ColorSpaces.BT2020.getTransform());
        LOGGER.info("{}", ColorSpaces.DCI_P3.getTransform());
        LOGGER.info("{}", sRGB.getTransform());
        LOGGER.info("{}", ChromaticAdaptation.BRADFORD.computeTransform(
                new float[]{0.314f, 0.351f}, ColorSpace.ILLUMINANT_D65));

        RGBColorSpace adaptedP3 = RGBColorSpace.adapt(ColorSpaces.ADOBE_RGB,
                ColorSpace.ILLUMINANT_D50, ChromaticAdaptation.BRADFORD);
        LOGGER.info("adapted P3 {}", adaptedP3.getTransform());

        float[] someTransform = //{0.60974f, 0.20528f, 0.14919f, 0.31111f, 0.62567f, 0.06322f, 0.01947f, 0.06087f, 0.74457f};
                {0.436065674f, 0.385147095f, 0.143066406f,
                         0.222488403f, 0.716873169f, 0.060607910f,
                         0.013916016f, 0.097076416f, 0.714096069f};
        transpose3x3(someTransform);
        float[] computedPri = RGBColorSpace.computePrimaries(someTransform);
        float[] computedWhite = RGBColorSpace.computeWhitePoint(someTransform);
        LOGGER.info("Computed pri {} and white {}", computedPri, ColorSpace.xyYToXYZ(computedWhite));
        LOGGER.info("Match what {}", ColorSpaces.match(someTransform,
                new TransferFunction((float)(1/1.055), (float)(0.055/1.055), (float)(1/12.92), 0.04045f, 0.0f, 0.0f, 2.4f)));
        someTransform = new float[]{0.60974f, 0.31111f, 0.01947f, 0.20528f, 0.62567f, 0.06087f, 0.14919f, 0.06322f, 0.74457f};
        LOGGER.info("Match what {}", ColorSpaces.match(someTransform,
                new TransferFunction(1, 0, 0, 0, 2.19921875)));

        {
            var someTF = new TransferFunction(0.947998046875, 0.052001953125, 0.076995849609375, 0.03900146484375, 0.0, 0.0, 2.399993896484375);
            /*var some = new RGBColorSpace("A", ,
                    , TransferFunction.GAMMA_2_4);
                    var adp = RGBColorSpace.adapt(some, ColorSpace.ILLUMINANT_D65);
                    var adpTx = adp.getTransform();*/
            LOGGER.info("Match what {}",
                    ColorSpaces.match(new float[]{0.6800135f, 0.32001078f, 0.26499933f, 0.6900024f, 0.14999372f, 0.059991274f},
                            new float[]{0.31270096f, 0.32900274f}, someTF ));

            someTransform = new float[]{0.4543f, 0.24191f, 0.01489f, 0.35335f, 0.67363f, 0.09064f, 0.15665f, 0.08446f, 0.71957f};
            someTransform = ColorSpace.mul3x3(ChromaticAdaptation.BRADFORD.computeTransform(
                    ColorSpace.ILLUMINANT_D50, ColorSpace.ILLUMINANT_D65
            ), someTransform);
            LOGGER.info("Pri {}", RGBColorSpace.computePrimaries(someTransform));
        }

        testOOTF(ColorSpaces.BT2020, new float[]{-0.36f, -0.56f, 0.13f});

        testMult(new float[]{1, 0, 0}, new float[]{0, 0, 1}, displayP3,
                ColorSpaces.ACESCG);

        assert ColorSpaces.SRGB.isSRGB();
        assert ColorSpaces.SRGB.isExtendedSRGB();
        assert !ColorSpaces.EXTENDED_SRGB.isSRGB();
        assert ColorSpaces.EXTENDED_SRGB.isExtendedSRGB();
        assert !ColorSpaces.LINEAR_EXTENDED_SRGB.isSRGB();
        assert !ColorSpaces.LINEAR_EXTENDED_SRGB.isExtendedSRGB();
        assert !ColorSpaces.BT709.isSRGB();
        assert !ColorSpaces.SRGB.equals(ColorSpaces.EXTENDED_SRGB);
        assert ColorSpaces.SRGB.equals(ColorSpaces.EXTENDED_SRGB, true);

        /*for (var that : ColorSpaces.getNamedColorSpaces()) {
            LOGGER.info("{} isWideGamut {}", that, that.isWideGamut());
        }*/
    }

    private static void testMult(float[] a, float[] b, ColorSpace srcColorSpace,
                                 ColorSpace blendColorSpace) {
        ColorTransform aToB = new ColorTransform(srcColorSpace, blendColorSpace);

        float[] aa = aToB.transformExtended(a.clone());
        float[] bb = aToB.transformExtended(b.clone());

        for (int i = 0; i < aa.length; i++) {
            aa[i] *= bb[i];
        }

        new ColorTransform(blendColorSpace, srcColorSpace).transformExtended(aa);
        LOGGER.info("Result after multiply {} {}", aa, srcColorSpace);
    }

    private static void testOOTF(RGBColorSpace space, float[] color) {
        float[] srcOOTF = new float[4];
        float systemGamma = 1.2f;
        float[] transform = space.getTransform();
        srcOOTF[0] = transform[1];
        srcOOTF[1] = transform[4];
        srcOOTF[2] = transform[7];
        srcOOTF[3] = systemGamma - 1;
        float[] dstOOTF = new float[4];
        dstOOTF[0] = transform[1];
        dstOOTF[1] = transform[4];
        dstOOTF[2] = transform[7];
        dstOOTF[3] = (1 / systemGamma) - 1;

        float[] a = color.clone();
        applyOOTF(a, srcOOTF);
        LOGGER.info("OOTF Before {}", color);
        LOGGER.info("OOTF After {}", a);
        applyOOTF(a, dstOOTF);
        LOGGER.info("OOTF Inv {}", a);
    }

    private static void applyOOTF(@Size(min = 3) float @NonNull [] v,
                                  @Size(4) float @NonNull [] ootf) {
        float lum = v[0] * ootf[0] + v[1] * ootf[1] + v[2] * ootf[2];
        float factor = (float) Math.pow(Math.abs(lum), ootf[3]);
        v[0] *= factor;
        v[1] *= factor;
        v[2] *= factor;
    }

    public static TransferFunction approx(TransferFunction tf, boolean linear) {
        double bestError = Double.POSITIVE_INFINITY;
        TransferFunction best = null;
        for (int i = 0; i < 10000; i++) {
            double gamma = 1.8 + i / 1000.0;
            TransferFunction can = new TransferFunction(
                    1, 0, 0, 0, gamma
            );
            double err = error(tf, can, linear);
            if (err < bestError) {
                bestError = err;
                best = can;
            }
        }
        return best;
    }

    public static double error(TransferFunction origTF, TransferFunction gammaTF,
                               boolean linear) {
        var t1 = origTF.toEOTF();
        var t2 = gammaTF.toEOTF();
        var oetf = linear ? null : origTF.toOETF();
        double acc = 0;
        for (int i = 0; i < 1024; i++) {
            double x = i / 1023.0;
            if (linear) {
                acc += Math.abs(t1.applyAsDouble(x) - t2.applyAsDouble(x));
            } else {
                acc += Math.abs(oetf.applyAsDouble(t1.applyAsDouble(x)) - oetf.applyAsDouble(t2.applyAsDouble(x)));
            }
        }
        return acc;
    }

    public static void transpose3x3(float[] m) {
        float t = m[1];
        m[1] = m[3];
        m[3] = t;

        t = m[2];
        m[2] = m[6];
        m[6] = t;

        t = m[5];
        m[5] = m[7];
        m[7] = t;
    }

    public static void generateICCProfile() {
        ColorProfile cp = new ColorProfile();

        RGBColorSpace p3 = ColorSpaces.DISPLAY_P3;

        cp.dataColorSpace = ICC_Profile.icSigRgbData;
        cp.transferFunction = p3.getTransferFunction();

        cp.primaries = p3.getPrimaries();
        cp.whitePoint = p3.getWhitePoint();

        cp.description = p3.getName();

        cp.cicp = true;
        cp.cicp_colorPrimaries = Color.COLOR_PRIMARIES_SMPTE432;
        cp.cicp_transferCharacteristics = Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1;
        cp.cicp_videoFullRangeFlag = 1;

        writeICCProfile(cp, "my_display_p3");

        RGBColorSpace srgb = ColorSpaces.SRGB;

        cp.transferFunction = srgb.getTransferFunction();

        cp.primaries = srgb.getPrimaries();
        cp.whitePoint = srgb.getWhitePoint();

        cp.description = srgb.getName();

        cp.cicp = true;
        cp.cicp_colorPrimaries = Color.COLOR_PRIMARIES_BT709;
        cp.cicp_transferCharacteristics = Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1;
        cp.cicp_videoFullRangeFlag = 1;

        writeICCProfile(cp, "my_srgb");

        try {
            Files.write(Path.of("run/builtin_srgb.icc"), ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

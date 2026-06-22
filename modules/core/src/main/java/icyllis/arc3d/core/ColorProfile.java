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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Represents a partial ICC Profile used by Arc3D.
 */
//TODO
public class ColorProfile {

    // Gray or RGB
    public int dataColorSpace = ICC_Profile.icSigRgbData;

    public String description;

    public TransferFunction rTRC_para;
    public char[] rTRC_table;

    public TransferFunction gTRC_para;
    public char[] gTRC_table;

    public TransferFunction bTRC_para;
    public char[] bTRC_table;

    public TransferFunction kTRC_para;
    public char[] kTRC_table;

    public @Size(6) float[] primaries;
    public @Size(2) float[] whitePoint;

    public int renderingIntent = ICC_Profile.icMediaRelativeColorimetric;

    public boolean cicp;
    public int cicp_colorPrimaries;
    public int cicp_transferCharacteristics;
    public int cicp_matrixCoefficients = Color.MATRIX_COEFFICIENTS_IDENTITY;
    public int cicp_videoFullRangeFlag = 1;

    public int originalTagCount;
    public byte @Nullable [] originalData;
    public @Nullable ICC_Profile originalProfile;

    public String getDefaultDescription() {
        if (!cicp) {
            return "Generic RGB";
        }
        String pn;
        switch (cicp_colorPrimaries) {
            case Color.COLOR_PRIMARIES_BT709,
                 Color.COLOR_PRIMARIES_UNSPECIFIED -> {
                pn = "BT709";
            }
            case Color.COLOR_PRIMARIES_BT470M -> {
                pn = "BT470M";
            }
            case Color.COLOR_PRIMARIES_BT470BG -> {
                pn = "BT470BG";
            }
            case Color.COLOR_PRIMARIES_SMPTE170M,
                 Color.COLOR_PRIMARIES_SMPTE240M -> {
                pn = "SMPTE170M";
            }
            case Color.COLOR_PRIMARIES_GENERIC_FILM -> {
                pn = "FILM";
            }
            case Color.COLOR_PRIMARIES_BT2020 -> {
                pn = "BT2020";
            }
            case Color.COLOR_PRIMARIES_SMPTE428 -> {
                pn = "XYZ";
            }
            case Color.COLOR_PRIMARIES_SMPTE431 -> {
                pn = "P3-DCI";
            }
            case Color.COLOR_PRIMARIES_SMPTE432 -> {
                pn = "P3-D65";
            }
            case Color.COLOR_PRIMARIES_EBU3213 -> {
                pn = "EBU3213";
            }
            default -> {
                return null;
            }
        }

        String tn;
        switch (cicp_transferCharacteristics) {
            case Color.TRANSFER_CHARACTERISTICS_BT709,
                 Color.TRANSFER_CHARACTERISTICS_UNSPECIFIED,
                 Color.TRANSFER_CHARACTERISTICS_SMPTE170M,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_10BIT,
                 Color.TRANSFER_CHARACTERISTICS_BT2020_12BIT,
                 Color.TRANSFER_CHARACTERISTICS_IEC61966_2_4 -> {
                tn = "SMPTE170M";
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470M -> {
                tn = "Gamma 2.2";
            }
            case Color.TRANSFER_CHARACTERISTICS_BT470BG -> {
                tn = "Gamma 2.8";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE240M -> {
                tn = "SMPTE240M";
            }
            case Color.TRANSFER_CHARACTERISTICS_LINEAR -> {
                tn = "Linear";
            }
            case Color.TRANSFER_CHARACTERISTICS_IEC61966_2_1 -> {
                tn = "sRGB";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE2084 -> {
                tn = "PQ";
            }
            case Color.TRANSFER_CHARACTERISTICS_SMPTE428 -> {
                tn = "Gamma 2.6";
            }
            case Color.TRANSFER_CHARACTERISTICS_ARIB_STD_B67 -> {
                tn = "HLG";
            }
            default -> {
                return null;
            }
        }

        return pn + " primaries with " + tn + " transfer";
    }

    public static final int HEADER_SIZE = 128;
    // Header plus the size of the tag count (4)
    public static final int TOC_OFFSET = HEADER_SIZE + 4;
    // Contains a signature (4), offset (4), and size (4).
    public static final int TOC_RECORD_SIZE = 12;

    public static final int PROFILE_FILE_SIGNATURE = 0x61637370; // 'acsp'

    /**
     * ICC Profile Tag Signature: 'cicp'.
     * <p>
     * Added in ICC v4.4
     */
    public static final int icSigcicpTag = 0x63696370;

    public static final int icSigMultiLocalizedUnicodeType = 0x6D6C7563; // 'mluc'
    public static final int icSigS15Fixed16ArrayType = 0x73663332; // 'sf32'
    public static final int icSigCurveType = 0x63757276;  // 'curv'
    public static final int icSigParametricCurveType = 0x70617261;  // 'para'

    /**
     * Save the profile and return the binary ICC profile data.
     */
    public byte[] getData() {
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<>();
        int version = 0x04300000; // v4.3

        String desc = description;
        if (desc == null || desc.isEmpty()) {
            desc = getDefaultDescription();
        }
        tags.put(ICC_Profile.icSigProfileDescriptionTag, writeText(desc));

        tags.put(ICC_Profile.icSigCopyrightTag, writeText(
                "Copyright Icyllis Milica 2026"
        ));

        // ICCv4 requires media white point to be always D50
        tags.put(ICC_Profile.icSigMediaWhitePointTag, writeXYZ(
                ColorSpace.ICC_ILLUMINANT_D50_XYZ[0],
                ColorSpace.ICC_ILLUMINANT_D50_XYZ[1],
                ColorSpace.ICC_ILLUMINANT_D50_XYZ[2]
        ));

        float[] adaptation = null;
        if (whitePoint != null) {
            adaptation = ChromaticAdaptation.BRADFORD.computeTransform(
                    whitePoint, ColorSpace.ICC_ILLUMINANT_D50_XYZ
            );

            tags.put(ICC_Profile.icSigChromaticAdaptationTag, writeChad(adaptation));
        }

        if (primaries != null && whitePoint != null) {
            float[] xyzMatrix = RGBColorSpace.computeXYZMatrix(primaries, whitePoint);
            xyzMatrix = ColorSpace.mul3x3(adaptation, xyzMatrix);

            tags.put(ICC_Profile.icSigRedMatrixColumnTag, writeXYZ(
                    xyzMatrix[0],
                    xyzMatrix[1],
                    xyzMatrix[2]
            ));
            tags.put(ICC_Profile.icSigGreenMatrixColumnTag, writeXYZ(
                    xyzMatrix[3],
                    xyzMatrix[4],
                    xyzMatrix[5]
            ));
            tags.put(ICC_Profile.icSigBlueMatrixColumnTag, writeXYZ(
                    xyzMatrix[6],
                    xyzMatrix[7],
                    xyzMatrix[8]
            ));
        }

        if (rTRC_para != null || rTRC_table != null) {
            tags.put(ICC_Profile.icSigRedTRCTag, writeTRC(
                    rTRC_para, rTRC_table
            ));
            if (Objects.equals(gTRC_para, rTRC_para) && Arrays.equals(gTRC_table, rTRC_table)) {
                // null means duplicate previous tag data
                tags.put(ICC_Profile.icSigGreenTRCTag, null);
            } else {
                tags.put(ICC_Profile.icSigGreenTRCTag, writeTRC(
                        gTRC_para, gTRC_table
                ));
            }
            if (Objects.equals(bTRC_para, gTRC_para) && Arrays.equals(bTRC_table, gTRC_table)) {
                // null means duplicate previous tag data
                tags.put(ICC_Profile.icSigBlueTRCTag, null);
            } else {
                tags.put(ICC_Profile.icSigBlueTRCTag, writeTRC(
                        bTRC_para, bTRC_table
                ));
            }
        }
        if (kTRC_para != null || kTRC_table != null) {
            tags.put(ICC_Profile.icSigGrayTRCTag, writeTRC(
                    kTRC_para, kTRC_table
            ));
        }

        if (cicp) {
            tags.put(icSigcicpTag, writeCICP(
                    cicp_colorPrimaries,
                    cicp_transferCharacteristics,
                    cicp_matrixCoefficients,
                    cicp_videoFullRangeFlag
            ));
            // CICP is added in ICC v4.4
            version = 0x04400000;
        }


        int tagDataSize = 0;
        for (var data : tags.values()) {
            if (data != null) {
                tagDataSize += data.length;
            }
        }
        int tagTableSize = TOC_RECORD_SIZE * tags.size();
        int profileSize = TOC_OFFSET + tagTableSize + tagDataSize;


        ByteBuffer buffer = ByteBuffer.allocate(profileSize);

        buffer
                .putInt(profileSize)
                .putInt(0) // CMM
                .putInt(version)
                .putInt(ICC_Profile.icSigDisplayClass)
                .putInt(dataColorSpace)
                .putInt(ICC_Profile.icSigXYZData) // PCS
                .putShort((short) 2026)
                .putShort((short) 6)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 0)
                .putInt(PROFILE_FILE_SIGNATURE)
                .putInt(0) // Platform
                .putInt(0) // Flags (set later)
                .putInt(0) // Device Manufacturer
                .putInt(0) // Device Model
                .putInt(0b0000) // Device attributes (Reflective, Glossy, Positive, Color)
                .putInt(0) // Device attributes (vendor specific)
                .putInt(0) // Rendering intent (set later)
                .putInt(Math.round(ColorSpace.ICC_ILLUMINANT_D50_XYZ[0] * 65536f))
                .putInt(Math.round(ColorSpace.ICC_ILLUMINANT_D50_XYZ[1] * 65536f))
                .putInt(Math.round(ColorSpace.ICC_ILLUMINANT_D50_XYZ[2] * 65536f))
                .putInt(0); // Profile creator

        // table of contents
        buffer
                .position(HEADER_SIZE)
                .putInt(tags.size());
        int lastTagOffset = TOC_OFFSET + tagTableSize;
        int lastTagSize = 0;
        for (var e : tags.entrySet()) {
            // null means duplicate previous tag data
            if (e.getValue() != null) {
                lastTagOffset += lastTagSize;
                lastTagSize = e.getValue().length;
            }
            buffer
                    .putInt(e.getKey())
                    .putInt(lastTagOffset)
                    .putInt(lastTagSize);
        }

        for (var data : tags.values()) {
            if (data != null) {
                buffer.put(data);
            }
        }

        assert !buffer.hasRemaining();

        byte[] md5 = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            md5 = digest.digest(buffer.array());
        } catch (NoSuchAlgorithmException ignored) {
        }

        // these 3 fields must set to zeros during MD5 calculation, so we set them here
        buffer
                .putInt(ICC_Profile.icHdrFlags, 0b00) // not embedded, independent
                .putInt(ICC_Profile.icHdrRenderingIntent, renderingIntent);
        if (md5 != null) {
            buffer.put(ICC_Profile.icHdrProfileID, md5);
        }

        return buffer.array();
    }

    public static byte @NonNull [] writeText(@NonNull CharSequence text) {
        int textLength = text.length();

        ByteBuffer buffer = ByteBuffer.allocate(
                16 + 12 + MathUtil.align4(textLength * 2)
        );

        buffer
                .putInt(icSigMultiLocalizedUnicodeType) // Type: 'mluc'
                .putInt(0) // Reserved
                .putInt(1) // Number of records: 1
                .putInt(12); // Name record size: 12
        buffer
                .put((byte) 'e').put((byte) 'n') // Language code: 'en'
                .put((byte) 'U').put((byte) 'S') // Country code: 'US'
                .putInt(textLength * 2) // String length in bytes
                .putInt(16 + 12); // String offset in bytes (from tag start)

        for (int i = 0; i < textLength; i++) {
            buffer.putChar(text.charAt(i));
        }

        return buffer.array();
    }

    public static byte @NonNull [] writeXYZ(float x, float y, float z) {
        ByteBuffer buffer = ByteBuffer.allocate(
                20
        );

        buffer
                .putInt(ICC_Profile.icSigXYZData)
                .putInt(0) // Reserved
                .putInt(Math.round(x * 65536f))
                .putInt(Math.round(y * 65536f))
                .putInt(Math.round(z * 65536f));

        return buffer.array();
    }

    public static byte @NonNull [] writeChad(float @NonNull [] m) {
        ByteBuffer buffer = ByteBuffer.allocate(
                44
        );

        buffer
                .putInt(icSigS15Fixed16ArrayType)
                .putInt(0) // Reserved
                .putInt(Math.round(m[0] * 65536f))
                .putInt(Math.round(m[3] * 65536f))
                .putInt(Math.round(m[6] * 65536f))
                .putInt(Math.round(m[1] * 65536f))
                .putInt(Math.round(m[4] * 65536f))
                .putInt(Math.round(m[7] * 65536f))
                .putInt(Math.round(m[2] * 65536f))
                .putInt(Math.round(m[5] * 65536f))
                .putInt(Math.round(m[8] * 65536f));

        return buffer.array();
    }

    public static byte @NonNull [] writeTRC(TransferFunction tf, char[] table) {
        ByteBuffer buffer;
        if (table != null) {
            assert table.length > 1;
            buffer = ByteBuffer.allocate(
                    12 + MathUtil.align4(table.length * 2)
            );
            buffer
                    .putInt(icSigCurveType)
                    .putInt(0) // Reserved
                    .putInt(table.length);
            for (char v : table) {
                buffer.putChar(v);
            }
        } else {
            int functionType;
            if (tf.e == 0.0 && tf.f == 0.0) {
                if (tf.a == 1.0 && tf.b == 0.0 &&
                        tf.c == 0.0 && tf.d == 0.0) {
                    if (tf.g == 1.0) {
                        functionType = -1;
                    } else {
                        functionType = 0;
                    }
                } else {
                    functionType = 3;
                }
            } else {
                functionType = 4;
            }
            buffer = ByteBuffer.allocate(
                    12 + switch (functionType) {
                        case 0 -> 4;
                        case 3 -> 20;
                        case 4 -> 28;
                        default -> 0;
                    }
            );
            if (functionType >= 0) {
                buffer
                        .putInt(icSigParametricCurveType)
                        .putInt(0) // Reserved
                        .putShort((short) functionType)
                        .putShort((short) 0); // Reserved
                buffer.putInt((int) Math.round(tf.g * 65536));
                if (functionType >= 3) {
                    buffer.putInt((int) Math.round(tf.a * 65536));
                    buffer.putInt((int) Math.round(tf.b * 65536));
                    buffer.putInt((int) Math.round(tf.c * 65536));
                    buffer.putInt((int) Math.round(tf.d * 65536));
                }
                if (functionType == 4) {
                    buffer.putInt((int) Math.round(tf.e * 65536));
                    buffer.putInt((int) Math.round(tf.f * 65536));
                }
            } else {
                buffer
                        .putInt(icSigCurveType)
                        .putInt(0) // Reserved
                        .putInt(0); // n=0 means identity
            }
        }
        return buffer.array();
    }

    public static byte @NonNull [] writeChromaticity(@Size(6) float[] primaries) {
        ByteBuffer buffer = ByteBuffer.allocate(
                36
        );

        int encodedValue;
        if (Arrays.equals(primaries, ColorSpace.SRGB_PRIMARIES)) {
            // ITU-R BT.709-2
            encodedValue = 1;
        } else if (Arrays.equals(primaries, ColorSpace.SMPTE_C_PRIMARIES)) {
            // SMPTE RP145
            encodedValue = 2;
        } else if (Arrays.equals(primaries, ColorSpace.BT470_BG_PRIMARIES)) {
            // EBU Tech. 3213-E
            encodedValue = 3;
        } else if (Arrays.equals(primaries, ColorSpace.DCI_P3_PRIMARIES)) {
            // P3
            encodedValue = 5;
        } else if (Arrays.equals(primaries, ColorSpace.BT2020_PRIMARIES)) {
            // ITU-R BT.2020
            encodedValue = 6;
        } else {
            encodedValue = 0;
        }

        buffer
                .putInt(ICC_Profile.icSigChromaticityTag)
                .putInt(0) // Reserved
                .putShort((short) 3) // Number of channels
                .putShort((short) encodedValue);
        for (int i = 0; i < 6; i++) {
            // this is u16Fixed16Number, so we can't write negative coordinates
            buffer.putInt(Math.round(primaries[i] * 65536f));
        }

        return buffer.array();
    }

    public static byte @NonNull [] writeCICP(int colorPrimaries, int transferCharacteristics,
                                             int matrixCoefficients, int videoFullRangeFlag) {
        ByteBuffer buffer = ByteBuffer.allocate(
                12
        );

        buffer
                .putInt(icSigcicpTag)
                .putInt(0) // Reserved
                .put((byte) colorPrimaries)
                .put((byte) transferCharacteristics)
                .put((byte) matrixCoefficients)
                .put((byte) videoFullRangeFlag);

        return buffer.array();
    }
}

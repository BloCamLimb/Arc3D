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
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.function.DoubleUnaryOperator;

/**
 * Represents a partial ICC Profile used by Arc3D Color Management System.
 */
public class ColorProfile {

    // Gray or RGB
    public int dataColorSpace = ICC_Profile.icSigRgbData;
    public int renderingIntent = ICC_Profile.icMediaRelativeColorimetric;

    public String description;

    public @Size(6) float[] primaries;
    public @Size(2) float[] whitePoint;

    public TransferFunction transferFunction;

    public boolean cicp;
    public int cicp_colorPrimaries;
    public int cicp_transferCharacteristics;
    public int cicp_matrixCoefficients = YUVInfo.MATRIX_COEFFICIENTS_IDENTITY;
    public int cicp_videoFullRangeFlag = 1;

    public int originalTagCount;
    public byte @Nullable [] originalData;
    public @Nullable ICC_Profile originalProfile;

    public ColorProfile() {
    }

    public void setColorSpace(@NonNull RGBColorSpace colorSpace) {
        dataColorSpace = ICC_Profile.icSigRgbData;

        description = colorSpace.getName();
        primaries = colorSpace.getPrimaries();
        whitePoint = colorSpace.getWhitePoint();
        transferFunction = colorSpace.getTransferFunction();

        cicp = false;

        originalTagCount = 0;
        originalData = null;
        originalProfile = null;
    }

    public @Nullable ColorSpace toColorSpace(boolean useBT1886) {
        if (dataColorSpace == ICC_Profile.icSigRgbData) {

            if (cicp &&
                    cicp_matrixCoefficients == YUVInfo.MATRIX_COEFFICIENTS_IDENTITY &&
                    cicp_videoFullRangeFlag != 0) {
                return ColorSpaces.fromCICP(cicp_colorPrimaries, cicp_transferCharacteristics, useBT1886);
            }

            if (primaries != null && whitePoint != null && transferFunction != null) {
                RGBColorSpace matched = ColorSpaces.match(primaries, whitePoint, transferFunction);
                if (matched != null) {
                    return matched;
                }

                return new RGBColorSpace(getDefaultDescription(), primaries, whitePoint, transferFunction);
            }

        } else if (dataColorSpace == ICC_Profile.icSigGrayData) {
            if (whitePoint != null && transferFunction != null) {
                return new RGBColorSpace(getDefaultDescription(), ColorSpace.XYZ_PRIMARIES, whitePoint, transferFunction);
            }
        }
        return null;
    }

    public static boolean compare(@NonNull ShortBuffer table, @NonNull TransferFunction tf) {
        int N = Math.max(table.remaining(), 256);
        double scale = 1.0 / (N - 1);
        DoubleUnaryOperator A = x -> evalCurve(table, x);
        DoubleUnaryOperator B = tf.toEOTF();
        for (int i = 0; i < N; i++) {
            double x = i * scale;
            if (!TransferFunction.compare(x, A, B)) {
                return false;
            }
        }
        return true;
    }

    public static double evalCurve(@NonNull ShortBuffer table, double x) {
        int n = table.remaining() - 1;
        double ix = MathUtil.clamp(x, 0, 1) * n;
        int lo = (int) ix;
        int hi = Math.min(lo + 1, n);
        double l = (table.get(lo) & 0xFFFF) * (1 / 65535.0);
        double h = (table.get(hi) & 0xFFFF) * (1 / 65535.0);
        return MathUtil.lerp(l, h, ix - lo);
    }

    public @NonNull String getDefaultDescription() {
        if (cicp) {
            String name = ColorSpaces.nameCICP(cicp_colorPrimaries, cicp_transferCharacteristics,
                    false);
            if (name != null) {
                return name;
            }
        }
        if (dataColorSpace == ICC_Profile.icSigRgbData) {
            if (primaries != null && whitePoint != null && transferFunction != null) {
                String pn = ColorSpaces.namePrimaries(primaries, whitePoint);
                String tn = ColorSpaces.nameTransfer(transferFunction);
                if (pn != null || tn != null) {
                    return (pn != null ? pn : "Unknown") + " primaries with " +
                            (tn != null ? tn : "Unknown") + " transfer";
                }
            }
            return "Some RGB";
        } else if (dataColorSpace == ICC_Profile.icSigGrayData) {
            if (transferFunction != null) {
                String tn = ColorSpaces.nameTransfer(transferFunction);
                return "Gray with " + (tn != null ? tn : "Unknown") + " transfer";
            }
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return "ColorProfile{" +
                "dataColorSpace=0x" + Integer.toHexString(dataColorSpace) +
                ", renderingIntent=" + renderingIntent +
                ", description='" + description + '\'' +
                ", primaries=" + Arrays.toString(primaries) +
                ", whitePoint=" + Arrays.toString(whitePoint) +
                ", transferFunction=" + transferFunction +
                ", cicp=" + cicp +
                ", cicp_colorPrimaries=" + cicp_colorPrimaries +
                ", cicp_transferCharacteristics=" + cicp_transferCharacteristics +
                ", cicp_matrixCoefficients=" + cicp_matrixCoefficients +
                ", cicp_videoFullRangeFlag=" + cicp_videoFullRangeFlag +
                ", originalTagCount=" + originalTagCount +
                ", originalData=" + (originalData == null ? "null" : originalData.length + " bytes") +
                ", originalProfile=" + originalProfile +
                '}';
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
     * Attempts to parse the given ICC profile data and returns a {@code ColorProfile}.
     * Throws {@link IllegalArgumentException} if the data does not represent a valid ICC profile.
     * <p>
     * Regardless of whether the profile can be resolved, {@link #originalData} and
     * {@link #originalTagCount} are always initialized.
     *
     * @throws IllegalArgumentException the data does not represent a valid ICC profile
     */
    public static @NonNull ColorProfile parseICC(byte @NonNull [] data)
            throws IllegalArgumentException {
        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        if (buffer.remaining() < TOC_OFFSET) {
            throw new IllegalArgumentException("ICC data size is too small");
        }
        if (buffer.getInt(ICC_Profile.icHdrMagic) != PROFILE_FILE_SIGNATURE) {
            throw new IllegalArgumentException("Bad ICC magic");
        }
        if (buffer.remaining() < buffer.getInt(ICC_Profile.icHdrSize)) {
            throw new IllegalArgumentException("ICC data size is smaller than the declared size");
        }

        boolean resolvable = true;

        int deviceClass = buffer.getInt(ICC_Profile.icHdrDeviceClass);
        switch (deviceClass) {
            case ICC_Profile.icSigInputClass:
            case ICC_Profile.icSigDisplayClass:
                break;
            case ICC_Profile.icSigOutputClass:
            case ICC_Profile.icSigLinkClass:
            case ICC_Profile.icSigColorSpaceClass:
            case ICC_Profile.icSigAbstractClass:
            case ICC_Profile.icSigNamedColorClass:
                resolvable = false;
                break;
            default:
                throw new IllegalArgumentException("Bad ICC device class: 0x" + Integer.toHexString(deviceClass));
        }

        int dataColorSpace = buffer.getInt(ICC_Profile.icHdrColorSpace);
        switch (dataColorSpace) {
            case ICC_Profile.icSigRgbData:
            case ICC_Profile.icSigGrayData:
                break;
            default:
                resolvable = false;
                break;
        }

        if (deviceClass != ICC_Profile.icSigLinkClass) {
            int pcs = buffer.getInt(ICC_Profile.icHdrPcs);
            switch (pcs) {
                case ICC_Profile.icSigXYZData:
                    break;
                case ICC_Profile.icSigLabData:
                    resolvable = false;
                    break;
                default:
                    throw new IllegalArgumentException("Bad ICC PCS: 0x" + Integer.toHexString(pcs));
            }
        }

        int renderingIntent = buffer.getInt(ICC_Profile.icHdrRenderingIntent);
        if (renderingIntent < 0 || renderingIntent > 3) {
            throw new IllegalArgumentException("Bad ICC rendering intent: " + renderingIntent);
        }

        int tagCount = buffer.getInt(HEADER_SIZE);
        int dataBegin = TOC_OFFSET + tagCount * TOC_RECORD_SIZE;
        if (tagCount < 0 || tagCount > 16777216 || buffer.remaining() < dataBegin) {
            throw new IllegalArgumentException("ICC data size is too small to hold tag table");
        }

        ColorProfile result = new ColorProfile();

        result.dataColorSpace = dataColorSpace;
        result.renderingIntent = renderingIntent;

        boolean hasDescription = false;
        boolean hasCopyright = false;
        float[] whitePoint = null;
        float[] chromaticAdaptation = null;
        float[] xyzMatrix = null;
        Object rTRC = null;
        Object gTRC = null;
        Object bTRC = null;
        Object kTRC = null;

        for (int i = 0, offset = TOC_OFFSET; i < tagCount; i++, offset += TOC_RECORD_SIZE) {
            int tagType = buffer.getInt(offset);
            int tagOffset = buffer.getInt(offset + 4);
            int tagSize = buffer.getInt(offset + 8);
            if (tagOffset < dataBegin ||
                    tagOffset + tagSize > buffer.remaining()) {
                throw new IllegalArgumentException("ICC tag data is out of range");
            }
            switch (tagType) {
                case ICC_Profile.icSigProfileDescriptionTag -> {
                    if (hasDescription) {
                        throw new IllegalArgumentException("Duplicate profile description");
                    }
                    hasDescription = true;
                    result.description = readText(buffer, tagOffset, tagSize);
                }
                case ICC_Profile.icSigCopyrightTag -> {
                    if (hasCopyright) {
                        throw new IllegalArgumentException("Duplicate copyright");
                    }
                    hasCopyright = true;
                }
                case ICC_Profile.icSigMediaWhitePointTag -> {
                    if (whitePoint != null) {
                        throw new IllegalArgumentException("Duplicate media white point");
                    }
                    whitePoint = new float[3];
                    if (!readXYZ(buffer, tagOffset, tagSize, whitePoint, 0)) {
                        throw new IllegalArgumentException("Invalid media white point");
                    }
                }
                case ICC_Profile.icSigChromaticAdaptationTag -> {
                    if (chromaticAdaptation != null) {
                        throw new IllegalArgumentException("Duplicate chromatic adaptation");
                    }
                    chromaticAdaptation = new float[9];
                    if (!readChad(buffer, tagOffset, tagSize, chromaticAdaptation)) {
                        throw new IllegalArgumentException("Invalid chromatic adaptation");
                    }
                }
                case ICC_Profile.icSigRedMatrixColumnTag -> {
                    if (xyzMatrix == null) {
                        xyzMatrix = new float[9];
                    }
                    if (!readXYZ(buffer, tagOffset, tagSize, xyzMatrix, 0)) {
                        throw new IllegalArgumentException("Invalid red matrix column");
                    }
                }
                case ICC_Profile.icSigGreenMatrixColumnTag -> {
                    if (xyzMatrix == null) {
                        xyzMatrix = new float[9];
                    }
                    if (!readXYZ(buffer, tagOffset, tagSize, xyzMatrix, 3)) {
                        throw new IllegalArgumentException("Invalid green matrix column");
                    }
                }
                case ICC_Profile.icSigBlueMatrixColumnTag -> {
                    if (xyzMatrix == null) {
                        xyzMatrix = new float[9];
                    }
                    if (!readXYZ(buffer, tagOffset, tagSize, xyzMatrix, 6)) {
                        throw new IllegalArgumentException("Invalid blue matrix column");
                    }
                }
                case ICC_Profile.icSigRedTRCTag -> {
                    if (rTRC != null) {
                        throw new IllegalArgumentException("Duplicate red TRC");
                    }
                    rTRC = readTRC(buffer, tagOffset, tagSize);
                    if (rTRC == null) {
                        throw new IllegalArgumentException("Invalid red TRC");
                    }
                }
                case ICC_Profile.icSigGreenTRCTag -> {
                    if (gTRC != null) {
                        throw new IllegalArgumentException("Duplicate green TRC");
                    }
                    gTRC = readTRC(buffer, tagOffset, tagSize);
                    if (gTRC == null) {
                        throw new IllegalArgumentException("Invalid green TRC");
                    }
                }
                case ICC_Profile.icSigBlueTRCTag -> {
                    if (bTRC != null) {
                        throw new IllegalArgumentException("Duplicate blue TRC");
                    }
                    bTRC = readTRC(buffer, tagOffset, tagSize);
                    if (bTRC == null) {
                        throw new IllegalArgumentException("Invalid blue TRC");
                    }
                }
                case ICC_Profile.icSigGrayTRCTag -> {
                    if (kTRC != null) {
                        throw new IllegalArgumentException("Duplicate gray TRC");
                    }
                    kTRC = readTRC(buffer, tagOffset, tagSize);
                    if (kTRC == null) {
                        throw new IllegalArgumentException("Invalid gray TRC");
                    }
                }
                case icSigcicpTag -> {
                    if (result.cicp) {
                        throw new IllegalArgumentException("Duplicate CICP");
                    }
                    if (tagSize < 12) {
                        throw new IllegalArgumentException("Invalid CICP");
                    }
                    result.cicp = true;
                    result.cicp_colorPrimaries = buffer.get(tagOffset + 8) & 0xFF;
                    result.cicp_transferCharacteristics = buffer.get(tagOffset + 9) & 0xFF;
                    result.cicp_matrixCoefficients = buffer.get(tagOffset + 10) & 0xFF;
                    result.cicp_videoFullRangeFlag = buffer.get(tagOffset + 11) & 0xFF;
                }
            }
        }

        if (!hasDescription || !hasCopyright) {
            // ICC always requires these
            throw new IllegalArgumentException("Missing profile description or copyright");
        }

        resolve:
        if (resolvable) {
            // ICC requires mediaWhitePointTag, except for DeviceLink
            if (whitePoint == null) {
                throw new IllegalArgumentException("Missing media white point");
            }

            if (dataColorSpace == ICC_Profile.icSigRgbData) {
                if (rTRC == null || gTRC == null || bTRC == null) {
                    break resolve;
                }
                // simplify trc
                if (!rTRC.equals(gTRC) || !rTRC.equals(bTRC)) {
                    break resolve;
                }
                if (rTRC instanceof ShortBuffer) {
                    // make parametric fitting
                    if (compare((ShortBuffer) rTRC, TransferFunction.SRGB)) {
                        rTRC = TransferFunction.SRGB;
                    }
                }
                if (rTRC instanceof TransferFunction) {
                    result.transferFunction = (TransferFunction) rTRC;
                } else {
                    break resolve;
                }
            } else {
                assert dataColorSpace == ICC_Profile.icSigGrayData;
                if (kTRC == null) {
                    break resolve;
                }
                if (kTRC instanceof TransferFunction) {
                    result.transferFunction = (TransferFunction) kTRC;
                } else {
                    break resolve;
                }
            }

            if (chromaticAdaptation != null) {
                // OK now we want to compute the actual adopted white point and primaries
                float[] invChad = ColorSpace.inverse3x3(chromaticAdaptation);

                // The legacy Display P3 profile generated by Apple (Copyright Apple Inc., 2015/2017)
                // has the original white point, but ICCv4 requires adapted white point.
                // Thus we ignore it and always compute from ICC D50 illuminant
                float[] unadaptedWhitePoint = ColorSpace.xyWhitePoint(
                        ColorSpace.mul3x3Float3(invChad, ColorSpace.ICC_ILLUMINANT_D50_XYZ.clone())
                );

                if (dataColorSpace == ICC_Profile.icSigRgbData) {
                    float[] unadaptedXYZMatrix = ColorSpace.mul3x3(
                            invChad, xyzMatrix
                    );

                    float[] actualWhitePoint = RGBColorSpace.computeWhitePoint(
                            unadaptedXYZMatrix
                    );
                    float[] actualPrimaries = RGBColorSpace.computePrimaries(
                            unadaptedXYZMatrix
                    );

                    if (!ColorSpace.compare(unadaptedWhitePoint, actualWhitePoint) &&
                            !ColorSpace.compare(ColorSpace.xyWhitePoint(whitePoint), actualWhitePoint)) {
                        throw new IllegalArgumentException("Unadapted media white point does not match colorant matrix");
                    }

                    result.whitePoint = actualWhitePoint;
                    result.primaries = actualPrimaries;
                } else {
                    result.whitePoint = unadaptedWhitePoint;
                }
            } else {
                // No chad, this generally means the white point is PCS illuminant.
                // However, this could also be due to some software (like Google Skia)
                // not following the ICCv4 specification and losing the chad matrix;
                // we will first attempt to derive it.
                if (dataColorSpace == ICC_Profile.icSigRgbData) {

                    RGBColorSpace matched = ColorSpaces.match(xyzMatrix, result.transferFunction);

                    float[] actualWhitePoint;
                    float[] actualPrimaries;
                    if (matched != null) {
                        actualWhitePoint = matched.getWhitePoint();
                        actualPrimaries = matched.getPrimaries();
                    } else {
                        //TODO unadapt using media white point?
                        actualWhitePoint = RGBColorSpace.computeWhitePoint(
                                xyzMatrix
                        );
                        actualPrimaries = RGBColorSpace.computePrimaries(
                                xyzMatrix
                        );
                    }
                    result.whitePoint = actualWhitePoint;
                    result.primaries = actualPrimaries;
                } else {
                    result.whitePoint = ColorSpace.xyWhitePoint(whitePoint);
                }
            }
        }

        result.originalTagCount = tagCount;
        result.originalData = data;

        return result;
    }

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

        if (transferFunction != null) {
            if (dataColorSpace == ICC_Profile.icSigRgbData) {
                tags.put(ICC_Profile.icSigRedTRCTag, writeTRC(
                        transferFunction, null
                ));
                // null means duplicate previous tag data
                tags.put(ICC_Profile.icSigGreenTRCTag, null);
                // null means duplicate previous tag data
                tags.put(ICC_Profile.icSigBlueTRCTag, null);
            } else if (dataColorSpace == ICC_Profile.icSigGrayData) {
                tags.put(ICC_Profile.icSigGrayTRCTag, writeTRC(
                        transferFunction, null
                ));
            }
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

    public static @Nullable String readText(ByteBuffer buffer, int offset, int size) {
        if (size < 8) {
            return null;
        }
        int type = buffer.getInt(offset);
        switch (type) {
            case icSigMultiLocalizedUnicodeType -> {
                if (size < 28) {
                    return null;
                }
                int stringLength = buffer.getInt(offset + 20);
                int stringOffset = buffer.getInt(offset + 24);
                if (stringLength < 0 || stringLength % 2 != 0) {
                    return null;
                }
                if (stringOffset < 28 || stringOffset > size - stringLength) {
                    return null;
                }
                String str = buffer
                        .slice(offset + stringOffset, stringLength)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asCharBuffer()
                        .toString();
                int tail = str.length();
                while (tail > 0 && str.charAt(tail - 1) == '\0')
                    tail--;
                return str.substring(0, tail);
            }
            case 0x74657874 -> {
                // 'text'
                String str = new String(buffer.array(),
                        buffer.arrayOffset() + offset + 8, size - 8,
                        StandardCharsets.US_ASCII);
                int tail = str.length();
                while (tail > 0 && str.charAt(tail - 1) == '\0')
                    tail--;
                return str.substring(0, tail);
            }
            case 0x64657363 -> {
                // 'desc'
                if (size < 12) {
                    return null;
                }
                int stringLength = buffer.getInt(offset + 8);
                if (stringLength < 0 || 12 > size - stringLength) {
                    return null;
                }
                String str = new String(buffer.array(),
                        buffer.arrayOffset() + offset + 12, stringLength,
                        StandardCharsets.US_ASCII);
                int tail = str.length();
                while (tail > 0 && str.charAt(tail - 1) == '\0')
                    tail--;
                return str.substring(0, tail);
            }
            default -> {
                return null;
            }
        }
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

    public static boolean readXYZ(ByteBuffer buffer, int offset, int size,
                                  float[] dst, int dstBegin) {
        if (size < 20 || buffer.getInt(offset) != ICC_Profile.icSigXYZData) {
            return false;
        }

        dst[dstBegin] = buffer.getInt(offset + 8) * (1.0f / 65536.0f);
        dst[dstBegin + 1] = buffer.getInt(offset + 12) * (1.0f / 65536.0f);
        dst[dstBegin + 2] = buffer.getInt(offset + 16) * (1.0f / 65536.0f);
        return true;
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

    public static boolean readChad(ByteBuffer buffer, int offset, int size,
                                   float[] dst) {
        if (size < 44 || buffer.getInt(offset) != icSigS15Fixed16ArrayType) {
            return false;
        }

        dst[0] = buffer.getInt(offset + 8) * (1.0f / 65536.0f);
        dst[3] = buffer.getInt(offset + 12) * (1.0f / 65536.0f);
        dst[6] = buffer.getInt(offset + 16) * (1.0f / 65536.0f);
        dst[1] = buffer.getInt(offset + 20) * (1.0f / 65536.0f);
        dst[4] = buffer.getInt(offset + 24) * (1.0f / 65536.0f);
        dst[7] = buffer.getInt(offset + 28) * (1.0f / 65536.0f);
        dst[2] = buffer.getInt(offset + 32) * (1.0f / 65536.0f);
        dst[5] = buffer.getInt(offset + 36) * (1.0f / 65536.0f);
        dst[8] = buffer.getInt(offset + 40) * (1.0f / 65536.0f);
        return true;
    }

    public static byte @NonNull [] writeTRC(TransferFunction para, ShortBuffer table) {
        ByteBuffer buffer;
        if (table != null) {
            assert table.remaining() > 1;
            buffer = ByteBuffer.allocate(
                    12 + MathUtil.align4(table.remaining() * 2)
            );
            buffer
                    .putInt(icSigCurveType)
                    .putInt(0) // Reserved
                    .putInt(table.remaining());
            buffer.asShortBuffer().put(table);
        } else {
            int functionType;
            if (para.e == 0.0 && para.f == 0.0) {
                if (para.a == 1.0 && para.b == 0.0 &&
                        para.c == 0.0 && para.d == 0.0) {
                    if (para.g == 1.0) {
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
                buffer.putInt((int) Math.round(para.g * 65536));
                if (functionType >= 3) {
                    buffer.putInt((int) Math.round(para.a * 65536));
                    buffer.putInt((int) Math.round(para.b * 65536));
                    buffer.putInt((int) Math.round(para.c * 65536));
                    buffer.putInt((int) Math.round(para.d * 65536));
                }
                if (functionType == 4) {
                    buffer.putInt((int) Math.round(para.e * 65536));
                    buffer.putInt((int) Math.round(para.f * 65536));
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

    // Returns TransferFunction para, ShortBuffer table, or null
    public static @Nullable Object readTRC(ByteBuffer buffer, int offset, int size) {
        if (size < 12) {
            return null;
        }
        switch (buffer.getInt(offset)) {
            case icSigCurveType -> {
                int count = buffer.getInt(offset + 8);
                if (count < 0 || count > 16777216 || size < 12 + count * 2) {
                    return null;
                }
                if (count < 2) {
                    float gamma = count == 0 ? 1.0f : buffer.getChar(offset + 12) * (1.0f / 256.0f);
                    if (gamma == 1.0f) {
                        return TransferFunction.LINEAR;
                    }
                    return new TransferFunction(1.0, 0.0, 0.0, 0.0, gamma);
                }
                return buffer
                        .slice(offset + 12, count * 2)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asShortBuffer();
            }
            case icSigParametricCurveType -> {
                if (size < 16) {
                    return null;
                }
                int functionType = buffer.getShort(offset + 8) & 0xFFFF;
                float gamma = buffer.getInt(offset + 12) * (1.0f / 65536.0f);
                if (functionType == 0) {
                    if (gamma == 1.0f) {
                        return TransferFunction.LINEAR;
                    }
                    return new TransferFunction(1.0, 0.0, 0.0, 0.0, gamma);
                }
                float a, b, c = 0, d = 0, e = 0, f = 0;
                switch (functionType) {
                    case 4:
                        if (size < 40) {
                            return null;
                        }
                        f = buffer.getInt(offset + 36) * (1.0f / 65536.0f);
                        e = buffer.getInt(offset + 32) * (1.0f / 65536.0f);
                        // fallthrough
                    case 3:
                        if (size < 32) {
                            return null;
                        }
                        d = buffer.getInt(offset + 28) * (1.0f / 65536.0f);
                        // fallthrough
                    case 2:
                        if (size < 28) {
                            return null;
                        }
                        c = buffer.getInt(offset + 24) * (1.0f / 65536.0f);
                        // fallthrough
                    case 1:
                        if (size < 24) {
                            return null;
                        }
                        b = buffer.getInt(offset + 20) * (1.0f / 65536.0f);
                        a = buffer.getInt(offset + 16) * (1.0f / 65536.0f);
                        break;
                    default:
                        return null;
                }
                return new TransferFunction(a, b, c, d, e, f, gamma);
            }
            default -> {
                return null;
            }
        }
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

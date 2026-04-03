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

package icyllis.arc3d.core.image;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.Pixmap;
import org.jspecify.annotations.NonNull;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * PNM image reader supporting P1-P6.
 *
 * <pre>
 * P1  Portable Bitmap     ASCII (Plain)   1-bit  (0/1 per pixel)
 * P2  Portable Graymap    ASCII (Plain)   8/16-bit gray
 * P3  Portable Pixmap     ASCII (Plain)   8/16-bit RGB
 * P4  Portable Bitmap     Binary (Raw)    1-bit  (packed, 8 pixels/byte)
 * P5  Portable Graymap    Binary (Raw)    8/16-bit gray
 * P6  Portable Pixmap     Binary (Raw)    8/16-bit RGB
 * </pre>
 */
public class PNMImageReader extends CoreImageReader {

    public static final int
            PORTABLE_BITMAP_ASCII = 1,
            PORTABLE_GRAYMAP_ASCII = 2,
            PORTABLE_PIXMAP_ASCII = 3,
            PORTABLE_BITMAP_BINARY = 4,
            PORTABLE_GRAYMAP_BINARY = 5,
            PORTABLE_PIXMAP_BINARY = 6;

    public int format; // 1-6  (the digit after 'P')
    public int width;
    public int height;
    public int maxVal; // always 1 for P1/P4

    @Override
    public void setInput(InputStream in) {
        super.setInput(in);
        format = 0;
    }

    @Override
    public void setInput(ReadableByteChannel ch) {
        super.setInput(ch);
        format = 0;
    }

    public void readHeader() throws IOException {
        if (buffer == null) setBuffer(new byte[BUFFER_SIZE], 0, 0);

        if (formatIsAscii(format)) {
            // When reading multiple images in the same file, if the last format is plain,
            // skip remaining whitespaces
            while (true) {
                byte b = nextRawByte();
                if (!isWS(b)) {
                    unget();
                    break;
                }
            }
        }

        // Magic must be exactly 'P' followed by '1'..'6', followed by whitespace (or comment)
        byte b0 = nextRawByte();
        byte b1 = nextRawByte();
        byte b2 = nextRawByte();
        if (b0 != 'P' || b1 < '1' || b1 > '6' || (!isWS(b2) && b2 != '#'))
            throw new IOException("Not a PNM image; got 0x"
                    + hex(b0) + " 0x" + hex(b1) + " 0x" + hex(b2));
        format = b1 - '0';
        unget(); // let readHeaderInt skip whitespaces and comments

        if (format == PORTABLE_BITMAP_ASCII || format == PORTABLE_BITMAP_BINARY) {
            maxVal = 1;
            width = readHeaderInt();
            height = readHeaderInt();
        } else {
            width = readHeaderInt();
            height = readHeaderInt();
            maxVal = readHeaderInt();
            if (maxVal < 1 || maxVal > 65535)
                throw new IOException("MAXVAL out of [1,65535]: " + maxVal);
        }

        // Binary formats: exactly one whitespace byte separates header from raster.
        // ASCII formats: the token reader already stopped on whitespace; nothing extra.
        if (formatIsBinary(format)) {
            byte ws = nextRawByte();
            if (!isWS(ws))
                throw new IOException(
                        "Expected whitespace after header, got 0x" + hex(ws));
        }
    }

    public void readPixels(@NonNull Pixmap dst) throws IOException {
        if (dst.getWidth() != width) {
            throw new IllegalArgumentException("width mismatch");
        }
        switch (format) {
            case PORTABLE_BITMAP_ASCII,
                 PORTABLE_BITMAP_BINARY -> {
                if (dst.getColorType() != ColorInfo.CT_GRAY_8) {
                    throw new IOException("Cannot convert bitmap into color type " +
                            ColorInfo.colorTypeToString(dst.getColorType()));
                }

                Object base = dst.getBase();
                long addr = dst.getAddress();

                for (int j = 0; j < dst.getHeight(); j++) {
                    ByteBuffer dstBuffer;
                    if (base != null) {
                        dstBuffer = ByteBuffer.wrap(
                                (byte[]) base, (int) addr, width
                        );
                    } else {
                        dstBuffer = MemoryUtil.memByteBuffer(
                                addr, width
                        );
                    }

                    switch (format) {
                        case PORTABLE_BITMAP_BINARY -> {
                            int x = width;
                            while (x > 0) {
                                int mask = nextRawByte() & 0xFF;
                                for (int shift = 7; shift >= 0 && x != 0; --shift, --x) {
                                    // in PBM, '1' is black, '0' is white, we revert it to match our convention
                                    dstBuffer.put((mask & (1 << shift)) != 0 ? 0 : (byte) ~0);
                                }
                            }
                        }
                        case PORTABLE_BITMAP_ASCII -> {
                            for (int i = 0; i < width; i++) {
                                dstBuffer.put(readLiteralBit() != 0 ? (byte) ~0 : 0);
                            }
                        }
                        default -> throw new IOException();
                    }

                    addr += dst.getRowBytes();
                }
            }
            case PORTABLE_GRAYMAP_ASCII,
                 PORTABLE_GRAYMAP_BINARY -> {

                int maxVal = this.maxVal;
                int dstMax;
                switch (dst.getColorType()) {
                    case ColorInfo.CT_GRAY_8:
                        dstMax = 255;
                        break;
                    case ColorInfo.CT_GRAY_16:
                        dstMax = 65535;
                        break;
                    default:
                        throw new IOException("Cannot convert graymap into color type " +
                                ColorInfo.colorTypeToString(dst.getColorType()));
                }

                if (maxVal > dstMax) {
                    throw new IOException("Dst gray channel width is too small");
                }

                Object base = dst.getBase();
                long addr = dst.getAddress();

                if (dstMax > 255) {
                    for (int j = 0; j < dst.getHeight(); j++) {
                        ShortBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = ShortBuffer.wrap(
                                    (short[]) base, (int) (addr >> 1), width
                            );
                        } else {
                            dstBuffer = MemoryUtil.memShortBuffer(
                                    addr, width
                            );
                        }
                        readRowGray16(dstBuffer);

                        addr += dst.getRowBytes();
                    }
                } else {
                    for (int j = 0; j < dst.getHeight(); j++) {
                        ByteBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = ByteBuffer.wrap(
                                    (byte[]) base, (int) (addr >> 1), width
                            );
                        } else {
                            dstBuffer = MemoryUtil.memByteBuffer(
                                    addr, width
                            );
                        }
                        readRowGray8(dstBuffer);

                        addr += dst.getRowBytes();
                    }
                }
            }

            case PORTABLE_PIXMAP_ASCII,
                 PORTABLE_PIXMAP_BINARY -> {
                int maxVal = this.maxVal;
                int dstMax;
                boolean is16;
                boolean hasAlpha;
                switch (dst.getColorType()) {
                    case ColorInfo.CT_BGRA_5551:
                        dstMax = 31; is16 = false; hasAlpha = true;
                        break;
                    case ColorInfo.CT_RGB_888:
                        dstMax = 255; is16 = false; hasAlpha = false;
                        break;
                    case ColorInfo.CT_RGBA_8888:
                        dstMax = 255; is16 = false; hasAlpha = true;
                        break;
                    case ColorInfo.CT_RGBA_1010102:
                        dstMax = 1023; is16 = false; hasAlpha = true;
                        break;
                    case ColorInfo.CT_RGB_161616:
                        dstMax = 65535; is16 = true; hasAlpha = false;
                        break;
                    case ColorInfo.CT_RGBA_16161616:
                        dstMax = 65535; is16 = true; hasAlpha = true;
                        break;
                    default:
                        throw new IOException("Cannot convert pixmap into color type " +
                                ColorInfo.colorTypeToString(dst.getColorType()));
                }

                if (maxVal > dstMax) {
                    throw new IOException("Dst color channel width is too small");
                }

                Object base = dst.getBase();
                long addr = dst.getAddress();

                if (dst.getColorType() == ColorInfo.CT_RGBA_1010102) {
                    if (maxVal <= 255) {
                        throw new IOException("Cannot convert 8-bit pixmap into color type " +
                                ColorInfo.colorTypeToString(dst.getColorType()));
                    }
                    // Pack R | G<<10 | B<<20 | A<<30 into one int per pixel
                    for (int j = 0; j < dst.getHeight(); j++) {
                        IntBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = IntBuffer.wrap(
                                    (int[]) base, (int) (addr >> 2), width
                            );
                        } else {
                            dstBuffer = MemoryUtil.memIntBuffer(
                                    addr, width
                            );
                        }
                        readRow1010102(dstBuffer);

                        addr += dst.getRowBytes();
                    }
                } else if (dst.getColorType() == ColorInfo.CT_BGRA_5551) {
                    // Pack B | G<<5 | R<<10 | A<<15 into one short per pixel
                    for (int j = 0; j < dst.getHeight(); j++) {
                        ShortBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = ShortBuffer.wrap(
                                    (short[]) base, (int) (addr >> 1), width
                            );
                        } else {
                            dstBuffer = MemoryUtil.memShortBuffer(
                                    addr, width
                            );
                        }
                        readRow5551(dstBuffer);

                        addr += dst.getRowBytes();
                    }
                } else if (is16) {
                    if (maxVal <= 255) {
                        throw new IOException("Cannot convert 8-bit pixmap into color type " +
                                ColorInfo.colorTypeToString(dst.getColorType()));
                    }
                    for (int j = 0; j < dst.getHeight(); j++) {
                        ShortBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = ShortBuffer.wrap(
                                    (short[]) base, (int) (addr >> 1), width * (hasAlpha ? 4 : 3)
                            );
                        } else {
                            dstBuffer = MemoryUtil.memShortBuffer(
                                    addr, width * (hasAlpha ? 4 : 3)
                            );
                        }
                        readRowRGB16(dstBuffer, hasAlpha);

                        addr += dst.getRowBytes();
                    }
                } else {
                    // 8-bit: CT_RGB_888 or CT_RGBA_8888
                    for (int j = 0; j < dst.getHeight(); j++) {
                        ByteBuffer dstBuffer;
                        if (base != null) {
                            dstBuffer = ByteBuffer.wrap(
                                    (byte[]) base, (int) addr, width * (hasAlpha ? 4 : 3)
                            );
                        } else {
                            dstBuffer = MemoryUtil.memByteBuffer(
                                    addr, width * (hasAlpha ? 4 : 3)
                            );
                        }
                        readRowRGB8(dstBuffer, hasAlpha);

                        addr += dst.getRowBytes();
                    }
                }
            }
        }
    }

    private void readRowGray16(ShortBuffer dstBuffer) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal > 255;

        switch (format) {
            case PORTABLE_GRAYMAP_BINARY:
                if (maxVal == 65535) {
                    readRowData(dstBuffer);
                } else {
                    for (int i = 0; i < width; i++) {
                        byte b1 = nextRawByte();
                        byte b2 = nextRawByte();
                        long v = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                        v = (v * 65535 + (maxVal >> 1)) / maxVal;
                        dstBuffer.put((short) v);
                    }
                }
                break;

            case PORTABLE_GRAYMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    long v = readLiteralInt();
                    if (maxVal != 65535) {
                        v = (v * 65535 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put((short) v);
                }
                break;

            default:
                throw new IOException();
        }
    }

    private void readRowGray8(ByteBuffer dstBuffer) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal <= 255;

        switch (format) {
            case PORTABLE_GRAYMAP_BINARY:
                if (maxVal == 255) {
                    readRowData(dstBuffer);
                } else {
                    for (int i = 0; i < width; i++) {
                        int v = nextRawByte() & 0xFF;
                        v = (v * 255 + (maxVal >> 1)) / maxVal;
                        dstBuffer.put((byte) v);
                    }
                }
                break;

            case PORTABLE_GRAYMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    int v = readLiteralInt();
                    if (maxVal != 255) {
                        v = (v * 255 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put((byte) v);
                }
                break;

            default:
                throw new IOException();
        }
    }

    private void readRow1010102(IntBuffer dstBuffer) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal > 255 && maxVal <= 1031;

        switch (format) {
            case PORTABLE_PIXMAP_BINARY:
                for (int i = 0; i < width; i++) {
                    byte b1 = nextRawByte();
                    byte b2 = nextRawByte();
                    int r = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                    b1 = nextRawByte(); b2 = nextRawByte();
                    int g = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                    b1 = nextRawByte(); b2 = nextRawByte();
                    int b = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                    if (maxVal != 1023) {
                        r = (r * 1023 + (maxVal >> 1)) / maxVal;
                        g = (g * 1023 + (maxVal >> 1)) / maxVal;
                        b = (b * 1023 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put(r | (g << 10) | (b << 20) | (3 << 30));
                }
                break;

            case PORTABLE_PIXMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    int r = readLiteralInt();
                    int g = readLiteralInt();
                    int b = readLiteralInt();
                    if (r > maxVal || g > maxVal || b > maxVal)
                        throw new IOException("Sample value exceeds maxVal " + maxVal);
                    if (maxVal != 1023) {
                        r = (r * 1023 + (maxVal >> 1)) / maxVal;
                        g = (g * 1023 + (maxVal >> 1)) / maxVal;
                        b = (b * 1023 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put(r | (g << 10) | (b << 20) | (3 << 30));
                }
                break;

            default:
                throw new IOException();
        }
    }

    private void readRow5551(ShortBuffer dstBuffer) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal <= 31;

        switch (format) {
            case PORTABLE_PIXMAP_BINARY:
                for (int i = 0; i < width; i++) {
                    int r = nextRawByte() & 0xFF;
                    int g = nextRawByte() & 0xFF;
                    int b = nextRawByte() & 0xFF;
                    if (maxVal != 31) {
                        r = (r * 31 + (maxVal >> 1)) / maxVal;
                        g = (g * 31 + (maxVal >> 1)) / maxVal;
                        b = (b * 31 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put((short) (b | (g << 5) | (r << 10) | (1 << 15)));
                }
                break;

            case PORTABLE_PIXMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    int r = readLiteralInt();
                    int g = readLiteralInt();
                    int b = readLiteralInt();
                    if (r > maxVal || g > maxVal || b > maxVal)
                        throw new IOException("Sample value exceeds maxVal " + maxVal);
                    if (maxVal != 31) {
                        r = (r * 31 + (maxVal >> 1)) / maxVal;
                        g = (g * 31 + (maxVal >> 1)) / maxVal;
                        b = (b * 31 + (maxVal >> 1)) / maxVal;
                    }
                    dstBuffer.put((short) (b | (g << 5) | (r << 10) | (1 << 15)));
                }
                break;

            default:
                throw new IOException();
        }
    }

    private void readRowRGB16(ShortBuffer dstBuffer, boolean hasAlpha) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal > 255;

        switch (format) {
            case PORTABLE_PIXMAP_BINARY:
                if (maxVal == 65535 && !hasAlpha) {
                    readRowData(dstBuffer);
                } else {
                    for (int i = 0; i < width; i++) {
                        for (int c = 0; c < 3; c++) {
                            byte b1 = nextRawByte();
                            byte b2 = nextRawByte();
                            long v = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                            if (maxVal != 65535) {
                                v = (v * 65535 + (maxVal >> 1)) / maxVal;
                            }
                            dstBuffer.put((short) v);
                        }
                        if (hasAlpha) {
                            dstBuffer.put((short) ~0);
                        }
                    }
                }
                break;

            case PORTABLE_PIXMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    for (int c = 0; c < 3; c++) {
                        long v = readLiteralInt();
                        if (v > maxVal)
                            throw new IOException("Sample value " + v + " is greater than maxVal " + maxVal);
                        if (maxVal != 65535) {
                            v = (v * 65535 + (maxVal >> 1)) / maxVal;
                        }
                        dstBuffer.put((short) v);
                    }
                    if (hasAlpha) {
                        dstBuffer.put((short) ~0);
                    }
                }
                break;

            default:
                throw new IOException();
        }
    }

    // 8-bit: CT_RGB_888 or CT_RGBA_8888
    private void readRowRGB8(ByteBuffer dstBuffer, boolean hasAlpha) throws IOException {

        int width = this.width;
        int maxVal = this.maxVal;

        assert maxVal <= 255;

        switch (format) {
            case PORTABLE_PIXMAP_BINARY:
                if (maxVal == 255 && !hasAlpha) {
                    readRowData(dstBuffer);
                } else {
                    for (int i = 0; i < width; i++) {
                        for (int c = 0; c < 3; c++) {
                            int v = nextRawByte() & 0xFF;
                            if (maxVal != 255) {
                                v = (v * 255 + (maxVal >> 1)) / maxVal;
                            }
                            dstBuffer.put((byte) v);
                        }
                        if (hasAlpha) {
                            dstBuffer.put((byte) ~0);
                        }
                    }
                }
                break;

            case PORTABLE_PIXMAP_ASCII:
                for (int i = 0; i < width; i++) {
                    for (int c = 0; c < 3; c++) {
                        int v = readLiteralInt();
                        if (v > maxVal)
                            throw new IOException("Sample value " + v + " is greater than maxVal " + maxVal);
                        if (maxVal != 255) {
                            v = (v * 255 + (maxVal >> 1)) / maxVal;
                        }
                        dstBuffer.put((byte) v);
                    }
                    if (hasAlpha) {
                        dstBuffer.put((byte) ~0);
                    }
                }
                break;

            default:
                throw new IOException();
        }
    }

    /**
     * Reads the raster data as-is.
     * <p>
     * Calling this method will update dst's position and limit.
     * <p>
     * Note for binary formats, byte order should be big_endian, but dst's order is ignored,
     * caller should reinterpret it as big_endian, or use {@link #readRowData(ShortBuffer)}
     * to convert endianness.
     * For ASCII formats, dst's byte order is used.
     * <p>
     * Row bytes is the minimum row bytes, for custom row bytes, call {@link #readRowData(ByteBuffer)} instead.
     */
    public void readData(ByteBuffer dst) throws IOException {
        for (int i = 0; i < height; i++) {
            readRowData(dst);
        }
    }

    /**
     * Reads the raster data of the next row as-is.
     * <p>
     * Calling this method will update dst's position and limit.
     * <p>
     * Note for binary formats, byte order should be big_endian, but dst's order is ignored,
     * caller should reinterpret it as big_endian, or use {@link #readRowData(ShortBuffer)}
     * to convert endianness.
     * For ASCII formats, dst's byte order is used.
     */
    public void readRowData(ByteBuffer dst) throws IOException {
        switch (format) {
            case PORTABLE_BITMAP_BINARY -> {
                int want = (width + 7) >> 3;
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " bytes");
                }
                dst.limit((int) newLim);
                readFully(dst);
            }
            case PORTABLE_GRAYMAP_BINARY,
                 PORTABLE_PIXMAP_BINARY -> {
                long want = minRowBytes();
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " bytes");
                }
                dst.limit((int) newLim);
                readFully(dst);
            }

            case PORTABLE_BITMAP_ASCII -> {
                int want = (width + 7) >> 3;
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " bytes");
                }
                dst.limit((int) newLim);

                int octets = width >> 3;
                int leftover = width & 7;

                for (int i = 0; i < octets; ++i) {
                    int bits = 0;
                    for (int j = 0; j < 8; ++j) {
                        bits <<= 1;
                        int v = readLiteralBit();
                        bits |= v;
                    }
                    dst.put((byte) bits);
                }
                if (leftover > 0) {
                    int bits = 0;
                    int shift = 7;
                    for (int j = 0; j < leftover; ++j, --shift) {
                        bits |= readLiteralBit() << shift;
                    }
                    dst.put((byte) bits);
                }
            }

            case PORTABLE_GRAYMAP_ASCII,
                 PORTABLE_PIXMAP_ASCII -> {
                long want = minRowBytes();
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " bytes");
                }
                dst.limit((int) newLim);
                int maxVal = this.maxVal;

                int samples = samplesPerPixel() * width;
                for (int i = 0; i < samples; i++) {
                    int v = readLiteralInt();
                    if (v > maxVal)
                        throw new IOException("Sample value " + v + " is greater than maxVal " + maxVal);
                    if (maxVal > 255) {
                        dst.putShort((short) v);
                    } else {
                        dst.put((byte) v);
                    }
                }
            }

            default -> throw new IOException("Unknown format " + format);
        }
    }

    /**
     * Reads the raster data as-is.
     * <p>
     * Calling this method will update dst's position and limit.
     * <p>
     * This method only works for 16-bit graymap and pixmap.
     * Dst's byte order is used.
     * <p>
     * Row bytes is the minimum row bytes, for custom row bytes, call {@link #readRowData(ShortBuffer)} instead.
     */
    public void readData(ShortBuffer dst) throws IOException {
        for (int i = 0; i < height; i++) {
            readRowData(dst);
        }
    }

    /**
     * Reads the raster data of the next row as-is.
     * <p>
     * Calling this method will update dst's position and limit.
     * <p>
     * This method only works for 16-bit graymap and pixmap.
     * Dst's byte order is used.
     */
    public void readRowData(ShortBuffer dst) throws IOException {
        int maxVal = this.maxVal;
        if (maxVal <= 255) {
            throw new IOException("Not 16-bit PNM; maxVal is " + maxVal);
        }
        switch (format) {
            case PORTABLE_GRAYMAP_BINARY,
                 PORTABLE_PIXMAP_BINARY -> {
                long want = width * (long) samplesPerPixel();
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " samples");
                }
                dst.limit((int) newLim);
                readFully(dst);
            }
            case PORTABLE_GRAYMAP_ASCII,
                 PORTABLE_PIXMAP_ASCII -> {
                long want = width * (long) samplesPerPixel();
                long newLim = dst.position() + want;
                if (newLim > dst.capacity()) {
                    throw new IOException("Dst buffer is too small to receive " + want + " samples");
                }
                dst.limit((int) newLim);

                for (long i = 0; i < want; i++) {
                    int v = readLiteralInt();
                    if (v > maxVal)
                        throw new IOException("Sample value " + v + " is greater than maxVal " + maxVal);
                    dst.put((short) v);
                }
            }

            default -> throw new IOException("Unknown format " + format);
        }
    }

    /**
     * Samples per pixel: 3 for P3/P6, 1 otherwise.
     */
    public int samplesPerPixel() {
        return (format == PORTABLE_PIXMAP_ASCII || format == PORTABLE_PIXMAP_BINARY) ? 3 : 1;
    }

    /**
     * Bytes per sample: 2 when maxVal > 255, else 1.
     */
    public int bytesPerSample() {
        return maxVal > 255 ? 2 : 1;
    }

    public int bytesPerPixel() {
        return samplesPerPixel() * bytesPerSample();
    }

    public long minRowBytes() {
        return width * (long) bytesPerPixel();
    }

    public static boolean formatIsAscii(int format) {
        return switch (format) {
            case PORTABLE_BITMAP_ASCII, PORTABLE_GRAYMAP_ASCII, PORTABLE_PIXMAP_ASCII -> true;
            default -> false;
        };
    }

    public static boolean formatIsBinary(int format) {
        return switch (format) {
            case PORTABLE_BITMAP_BINARY, PORTABLE_GRAYMAP_BINARY, PORTABLE_PIXMAP_BINARY -> true;
            default -> false;
        };
    }

    @ColorInfo.ColorType
    public int getBestColorType(boolean allowCompact, boolean allow3Channel) {
        int maxVal = this.maxVal;
        switch (format) {
            case PORTABLE_BITMAP_ASCII,
                 PORTABLE_BITMAP_BINARY:
                return ColorInfo.CT_GRAY_8;
            case PORTABLE_GRAYMAP_ASCII,
                 PORTABLE_GRAYMAP_BINARY:
                return maxVal <= 255 ? ColorInfo.CT_GRAY_8
                        : ColorInfo.CT_GRAY_16;
            case PORTABLE_PIXMAP_ASCII,
                 PORTABLE_PIXMAP_BINARY:
                if (allowCompact && maxVal <= 31) {
                    return ColorInfo.CT_BGRA_5551;
                } else if (maxVal <= 255) {
                    return allow3Channel ? ColorInfo.CT_RGB_888
                            : ColorInfo.CT_RGBA_8888;
                } else if (allowCompact && maxVal <= 1023) {
                    return ColorInfo.CT_RGBA_1010102;
                } else {
                    return allow3Channel ? ColorInfo.CT_RGB_161616
                            : ColorInfo.CT_RGBA_16161616;
                }
        }
        return ColorInfo.CT_UNKNOWN;
    }

    private void readFully(ByteBuffer dst) throws IOException {
        int avail = bufEnd - bufPos;
        if (avail > 0) {
            int copy = Math.min(avail, dst.remaining());
            dst.put(buffer, bufPos, copy);
            bufPos += copy;
        }
        if (stream != null) {
            while (dst.hasRemaining()) {
                int n;
                if (dst.hasArray()) {
                    // directly read into the array
                    int request = Math.min(dst.remaining(), 8192);
                    n = stream.read(dst.array(), dst.arrayOffset() + dst.position(), request);
                    if (n < 0)
                        break;
                    dst.position(dst.position() + n);
                } else {
                    int request = Math.min(dst.remaining(), buffer.length);
                    n = stream.read(buffer, 0, request);
                    if (n < 0)
                        break;
                    dst.put(buffer, 0, n);
                }
            }
        } else if (channel != null) {
            while (dst.hasRemaining()) {
                int n = channel.read(dst);
                if (n < 0)
                    break;
            }
        }
        if (dst.hasRemaining())
            throw new IOException("Insufficient bytes provided: " + dst.remaining() + " bytes more are needed");
    }

    private void readFully(ShortBuffer dst) throws IOException {
        int avail = bufEnd - bufPos;
        if (avail > 0) {
            int copy = (int) Math.min(avail, (long) dst.remaining() << 1L);
            // copySwapMemory if needed
            dst.put(ByteBuffer.wrap(buffer, bufPos, copy)
                    .order(ByteOrder.BIG_ENDIAN)
                    .asShortBuffer());
            bufPos += copy;
        }
        if (stream != null) {
            while (dst.hasRemaining()) {
                int request = (int) Math.min((long) dst.remaining() << 1L, buffer.length);
                int n = stream.read(buffer, 0, request);
                if (n < 0)
                    break;
                if (n % 2 != 0) {
                    // Read odd number of bytes, needs one more
                    int n2 = stream.read(buffer, n, 1);
                    if (n2 != 1)
                        break;
                    n += 1;
                }
                // copySwapMemory if needed
                dst.put(ByteBuffer.wrap(buffer, bufPos, n)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asShortBuffer());
            }
        } else if (channel != null) {
            ByteBuffer tmp = ByteBuffer.wrap(buffer, 0, buffer.length)
                    .order(ByteOrder.BIG_ENDIAN);
            while (dst.hasRemaining()) {
                int request = (int) Math.min((long) dst.remaining() << 1L, buffer.length);
                tmp.position(0).limit(request);
                int n = channel.read(tmp);
                if (n < 0)
                    break;
                if (n % 2 != 0) {
                    // Read odd number of bytes, needs one more
                    int n2 = channel.read(tmp);
                    if (n2 != 1)
                        break;
                }
                // copySwapMemory if needed
                dst.put(tmp.flip().asShortBuffer());
            }
        }
        if (dst.hasRemaining())
            throw new IOException("Insufficient bytes provided: " + dst.remaining() + " bytes more are needed");
    }

    private int readHeaderInt() throws IOException {
        // consume all comments and whitespaces
        byte b;
        while (true) {
            b = nextRawByte();
            if (b == '#') {
                // skip to end of line
                while ((b = nextRawByte()) != '\n' && b != '\r')
                    ;
            } else if (!isWS(b)) {
                break;
            }
        }
        if (b < '0' || b > '9')
            throw new IOException("Expected digit, got 0x" + hex(b));
        int v = b - '0';
        while (true) {
            b = nextRawByte();
            if (b >= '0' && b <= '9') {
                v = v * 10 + (b - '0');
                if (v < 0) {
                    throw new IOException("Integer is too big");
                }
            } else if (isWS(b) || b == '#') {
                unget();
                return v;
            } else {
                throw new IOException("Unexpected byte in integer: 0x" + hex(b));
            }
        }
    }

    private int readLiteralInt() throws IOException {
        // consume all whitespaces, but no comments are allowed
        byte b;
        do {
            b = nextRawByte();
        } while (isWS(b));
        if (b < '0' || b > '9')
            throw new IOException("Expected digit in raster, got 0x" + hex(b));
        int v = b - '0';
        while (true) {
            b = nextRawByte();
            if (b >= '0' && b <= '9') {
                v = v * 10 + (b - '0');
                if (v < 0) {
                    throw new IOException("Integer sample is too big in raster");
                }
            } else if (isWS(b)) {
                unget();
                return v;
            } else {
                throw new IOException("Unexpected byte in raster integer: 0x" + hex(b));
            }
        }
    }

    private int readLiteralBit() throws IOException {
        // consume all whitespaces, but no comments are allowed
        byte b;
        do {
            b = nextRawByte();
        } while (isWS(b));
        if (b < '0' || b > '1')
            throw new IOException("Expected ASCII '0' or '1' in raster, got 0x" + hex(b));
        // in PBM, '1' is black, '0' is white, we revert it to match our convention
        return '1' - b;
    }
}

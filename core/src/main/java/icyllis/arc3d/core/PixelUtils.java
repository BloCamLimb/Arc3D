/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Utilities to access and convert pixels, heap and native.
 */
public class PixelUtils {

    // We know that memory access methods in Unsafe are deprecated for removal since Java 23,
    // but we observed severe performance regressions with MemorySegment in many scenarios.
    // We'll continue using Unsafe for now until it's completely removed, and then
    // switch to MemorySegment, or nio.Buffer (slower than Unsafe, but does not degrade excessively).
    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static final sun.misc.Unsafe UNSAFE = getUnsafe();

    // we assume little-endian and do conversion if we're on big-endian machines
    public static final boolean NATIVE_BIG_ENDIAN =
            (false);

    static {
        // our engine assumes little host endianness everywhere,
        // in addition, lwjgl does not support big endian machines at all
        if ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)) {
            throw new UnsupportedOperationException("Not built for BIG_ENDIAN");
        }
    }

    private static sun.misc.Unsafe getUnsafe() {
        java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            if (!field.getType().equals(sun.misc.Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers))) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (sun.misc.Unsafe) field.get(null);
            } catch (Exception e) {
                throw new UnsupportedOperationException("No sun.misc.Unsafe", e);
            }
        }

        throw new UnsupportedOperationException("No sun.misc.Unsafe");
    }

    public static final VarHandle BYTE_ARRAY_AS_SHORT =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.nativeOrder());
    public static final VarHandle BYTE_ARRAY_AS_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());

    // limit of MemorySegment.asByteBuffer()
    public static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8; // see jdk.internal.util.ArraysSupport.SOFT_MAX_ARRAY_LENGTH

    /**
     * Copy memory row by row.
     */
    public static void copyImage(long srcAddr, long srcRowBytes,
                                 long dstAddr, long dstRowBytes,
                                 long trimRowBytes, int rowCount) {
        copyImage(srcAddr, srcRowBytes,
                dstAddr, dstRowBytes,
                trimRowBytes, rowCount, false);
    }

    /**
     * Copy memory row by row, allowing vertical flip.
     */
    public static void copyImage(long srcAddr, long srcRowBytes,
                                 long dstAddr, long dstRowBytes,
                                 long trimRowBytes, int rowCount, boolean flipY) {
        if (srcRowBytes < trimRowBytes || dstRowBytes < trimRowBytes || trimRowBytes < 0 || trimRowBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        // benchmark shows that memcpy is faster than Unsafe.copyMemory at bigger size, on OpenJDK 21
        if (srcRowBytes == trimRowBytes && dstRowBytes == trimRowBytes && !flipY) {
            LibCString.nmemcpy(dstAddr, srcAddr, trimRowBytes * rowCount);
        } else {
            if (flipY) {
                dstAddr += dstRowBytes * (rowCount - 1);
                dstRowBytes = -dstRowBytes;
            }
            for (int i = 0; i < rowCount; ++i) {
                LibCString.nmemcpy(dstAddr, srcAddr, trimRowBytes);
                srcAddr += srcRowBytes;
                dstAddr += dstRowBytes;
            }
        }
    }

    /**
     * Copy memory row by row, allowing heap to off-heap copy.
     */
    public static void copyImage(Object srcBase, long srcAddr, long srcRowBytes,
                                 Object dstBase, long dstAddr, long dstRowBytes,
                                 long trimRowBytes, int rowCount) {
        copyImage(srcBase, srcAddr, srcRowBytes,
                dstBase, dstAddr, dstRowBytes,
                trimRowBytes, rowCount, false);
    }

    /**
     * Copy memory row by row, allowing heap to off-heap copy and vertical flip.
     */
    public static void copyImage(Object srcBase, long srcAddr, long srcRowBytes,
                                 Object dstBase, long dstAddr, long dstRowBytes,
                                 long trimRowBytes, int rowCount, boolean flipY) {
        if (srcBase == null && dstBase == null) {
            // off-heap only
            copyImage(srcAddr, srcRowBytes, dstAddr, dstRowBytes, trimRowBytes, rowCount, flipY);
            return;
        }
        if (srcRowBytes < trimRowBytes || dstRowBytes < trimRowBytes || trimRowBytes < 0 || trimRowBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        // mixed heap/off-heap or heap to heap
        if (srcRowBytes == trimRowBytes && dstRowBytes == trimRowBytes && !flipY) {
            mixedMemCopy(srcBase, srcAddr, dstBase, dstAddr, trimRowBytes * rowCount);
        } else {
            if (flipY) {
                dstAddr += dstRowBytes * (rowCount - 1);
                dstRowBytes = -dstRowBytes;
            }
            for (int i = 0; i < rowCount; ++i) {
                mixedMemCopy(srcBase, srcAddr, dstBase, dstAddr, trimRowBytes);
                srcAddr += srcRowBytes;
                dstAddr += dstRowBytes;
            }
        }
    }

    public static void mixedMemCopy(Object srcBase, long srcAddr,
                                    Object dstBase, long dstAddr,
                                    long bytes) {
        //TODO lwjgl 3.4.1 provides faster copy methods for mixed offheap and heap, and it can
        // switch to FFM backend on Java 25+, the FFM backend is faster than Unsafe.
        if (srcBase != null && dstBase != null) {
            // heap to heap, arraycopy is faster than Unsafe at small sizes, unless
            // we do type conversion (byte[] <-> int[])
            Buffer dst = bufferFromArray(dstBase, dstAddr, bytes);
            if (dst instanceof ByteBuffer) {
                if (srcBase instanceof byte[]) {
                    // arraycopy
                    ((ByteBuffer) dst).put((byte[]) srcBase, (int) srcAddr, (int) bytes);
                } else {
                    // unsafe
                    ((ByteBuffer) dst).asIntBuffer().put((int[]) srcBase, (int) (srcAddr>>2), (int) (bytes>>2));
                }
            } else if (dst instanceof ShortBuffer) {
                // arraycopy
                ((ShortBuffer) dst).put((short[]) srcBase, (int) (srcAddr>>1), (int) (bytes>>1));
            } else if (dst instanceof IntBuffer) {
                if (srcBase instanceof byte[]) {
                    // unsafe
                    ByteBuffer src = ByteBuffer.wrap((byte[]) srcBase, (int) srcAddr, (int) bytes)
                            .order(ByteOrder.nativeOrder());
                    ((IntBuffer) dst).put(src.asIntBuffer());
                } else {
                    // arraycopy
                    ((IntBuffer) dst).put((int[]) srcBase, (int) (srcAddr>>2), (int) (bytes>>2));
                }
            } else if (dst instanceof FloatBuffer) {
                // arraycopy
                ((FloatBuffer) dst).put((float[]) srcBase, (int) (srcAddr>>2), (int) (bytes>>2));
            } else {
                throw new IllegalStateException();
            }
        } else if (srcBase != null) {
            // heap to off-heap
            Buffer src = bufferFromArray(srcBase, srcAddr, bytes);
            ByteBuffer dst = MemoryUtil.memByteBuffer(dstAddr, (int) bytes);
            if (src instanceof ByteBuffer) {
                dst.put((ByteBuffer) src);
            } else if (src instanceof ShortBuffer) {
                dst.asShortBuffer().put((ShortBuffer) src);
            } else if (src instanceof IntBuffer) {
                dst.asIntBuffer().put((IntBuffer) src);
            } else if (src instanceof FloatBuffer) {
                dst.asFloatBuffer().put((FloatBuffer) src);
            } else {
                throw new IllegalStateException();
            }
        } else if (dstBase != null) {
            // off-heap to heap
            ByteBuffer src = MemoryUtil.memByteBuffer(srcAddr, (int) bytes);
            Buffer dst = bufferFromArray(dstBase, dstAddr, bytes);
            if (dst instanceof ByteBuffer) {
                ((ByteBuffer) dst).put(src);
            } else if (dst instanceof ShortBuffer) {
                ((ShortBuffer) dst).put(src.asShortBuffer());
            } else if (dst instanceof IntBuffer) {
                ((IntBuffer) dst).put(src.asIntBuffer());
            } else if (dst instanceof FloatBuffer) {
                ((FloatBuffer) dst).put(src.asFloatBuffer());
            } else {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    // params same as MemorySegment
    public static Buffer bufferFromArray(Object base, long addr, long bytes) {
        if (base instanceof byte[]) {
            return ByteBuffer.wrap((byte[]) base, (int) addr, (int) bytes)
                    .order(ByteOrder.nativeOrder());
        } else if (base instanceof short[]) {
            return ShortBuffer.wrap((short[]) base, (int) (addr>>1), (int) (bytes>>1));
        } else if (base instanceof int[]) {
            return IntBuffer.wrap((int[]) base, (int) (addr>>2), (int) (bytes>>2));
        } else if (base instanceof float[]) {
            return FloatBuffer.wrap((float[]) base, (int) (addr>>2), (int) (bytes>>2));
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Pack Alpha8 format to B/W format.
     */
    public static void packA8ToBW(Object srcBase, long srcAddr, int srcRowBytes,
                                  Object dstBase, long dstAddr, int dstRowBytes,
                                  int width, int height) {
        int octets = width >> 3;
        int leftover = width & 7;

        assert (srcRowBytes >= width);
        assert (dstRowBytes >= ((width + 7) >> 3));

        ByteBuffer src = toByteBufferUncapped(srcBase, srcAddr);
        ByteBuffer dst = toByteBufferUncapped(dstBase, dstAddr);

        int srcOffset = 0;
        int dstOffset = 0;

        for (int y = 0; y < height; ++y) {
            int nextSrcOffset = srcOffset + srcRowBytes;
            int nextDstOffset = dstOffset + dstRowBytes;
            for (int i = 0; i < octets; ++i) {
                int bits = 0;
                for (int j = 0; j < 8; ++j) {
                    bits <<= 1;
                    int v = (src.get(srcOffset + j) & 0xFF) >> 7;
                    bits |= v;
                }
                dst.put(dstOffset, (byte) bits);
                srcOffset += 8;
                dstOffset += 1;
            }
            if (leftover > 0) {
                int bits = 0;
                int shift = 7;
                for (int j = 0; j < leftover; ++j, --shift) {
                    bits |= (src.get(srcOffset + j) & 0xFF) >> 7 << shift;
                }
                dst.put(dstOffset, (byte) bits);
            }
            srcOffset = nextSrcOffset;
            dstOffset = nextDstOffset;
        }
    }

    /**
     * Unpack B/W format to Alpha8 format.
     */
    public static void unpackBWToA8(Object srcBase, long srcAddr, int srcRowBytes,
                                    Object dstBase, long dstAddr, int dstRowBytes,
                                    int width, int height) {
        assert (srcRowBytes >= ((width + 7) >> 3));
        assert (dstRowBytes >= width);

        ByteBuffer src = toByteBufferUncapped(srcBase, srcAddr);
        ByteBuffer dst = toByteBufferUncapped(dstBase, dstAddr);

        int srcOffset = 0;
        int dstOffset = 0;

        for (int y = 0; y < height; ++y) {
            int nextSrcOffset = srcOffset + srcRowBytes;
            int nextDstOffset = dstOffset + dstRowBytes;
            int x = width;
            while (x > 0) {
                int mask = src.get(srcOffset) & 0xFF;
                for (int shift = 7; shift >= 0 && x != 0; --shift, --x) {
                    dst.put(dstOffset, (mask & (1 << shift)) != 0 ? (byte) ~0 : 0);
                    dstOffset += 1;
                }
                srcOffset += 1;
            }
            srcOffset = nextSrcOffset;
            dstOffset = nextDstOffset;
        }
    }

    public static ByteBuffer toByteBufferUncapped(Object base, long addr) {
        if (base == null) {
            return MemoryUtil.memByteBuffer(addr, MAX_BUFFER_SIZE);
        } else {
            byte[] hb = (byte[]) base;
            int index = (int) addr;
            return ByteBuffer.wrap(hb, index, hb.length - index)
                    .order(ByteOrder.nativeOrder());
        }
    }

    public static void setPixel16(Object base, long addr,
                                  short value, int count) {
        assert count > 0;
        long wideValue = (long) value << 16 | value;
        wideValue |= wideValue << 32;
        assert MathUtil.isAlign2(addr);
        long pad = (-addr) & 7;
        while (pad > 0 && count != 0) {
            UNSAFE.putShort(base, addr, value);
            addr += 2;
            count--;
            pad -= 2;
        }
        assert count == 0 || pad == 0;
        assert count == 0 || MathUtil.isAlign8(addr);
        while (count >= 4) {
            UNSAFE.putLong(base, addr, wideValue);
            addr += 8;
            count -= 4;
        }
        while (count-- != 0) {
            UNSAFE.putShort(base, addr, value);
            addr += 2;
        }
    }

    public static void setPixel32(Object base, long addr,
                                  int value, int count) {
        assert count > 0;
        long wideValue = (long) value << 32 | value;
        assert MathUtil.isAlign4(addr);
        if (!MathUtil.isAlign8(addr)) {
            UNSAFE.putInt(base, addr, value);
            addr += 4;
            count--;
        }
        assert MathUtil.isAlign8(addr);
        while (count >= 2) {
            UNSAFE.putLong(base, addr, wideValue);
            addr += 8;
            count -= 2;
        }
        if (count != 0) {
            assert count == 1;
            UNSAFE.putInt(base, addr, value);
        }
    }

    public static void setPixel64(Object base, long addr,
                                  long value, int count) {
        assert MathUtil.isAlign8(addr);
        for (int i = 0; i < count; i++) {
            UNSAFE.putLong(base, addr, value);
            addr += 8;
        }
    }

    /**
     * Load pixel value in low precision.
     */
    @FunctionalInterface
    public interface PixelLoad {
        @ColorInt
        int load(Object base, long addr);
    }

    //@formatter:off
    public static int load_BGR_565_u(Object base, long addr) {
        int val = MemoryUtil.memGetShort(addr);
        int b = (((val       ) & 31) * 527 + 23) >> 6;
        int g = (((val >>>  5) & 63) * 259 + 33) >> 6;
        int r = (((val >>> 11) & 31) * 527 + 23) >> 6;
        return b | g << 8 | r << 16 | 0xff000000;
    }
    public static int load_BGRA_5551_u(Object base, long addr) {
        int val = MemoryUtil.memGetShort(addr);
        int b = (((val       ) & 31) * 527 + 23) >> 6;
        int g = (((val >>>  5) & 31) * 527 + 23) >> 6;
        int r = (((val >>> 10) & 31) * 527 + 23) >> 6;
        int a = (val & (1 << 15)) != 0 ? 255 : 0;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_RGBA_1010102_u(Object base, long addr) {
        int val = MemoryUtil.memGetInt(addr);
        int r = (((val       ) & 0x3ff) * 255 + 511) / 1023;
        int g = (((val >>> 10) & 0x3ff) * 255 + 511) / 1023;
        int b = (((val >>> 20) & 0x3ff) * 255 + 511) / 1023;
        int a = (val >>> 30) * 85;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_BGRA_1010102_u(Object base, long addr) {
        int val = MemoryUtil.memGetInt(addr);
        int r = (((val >>> 20) & 0x3ff) * 255 + 511) / 1023;
        int g = (((val >>> 10) & 0x3ff) * 255 + 511) / 1023;
        int b = (((val       ) & 0x3ff) * 255 + 511) / 1023;
        int a = (val >>> 30) * 85;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_R_8_u(Object base, long addr) {
        int val = MemoryUtil.memGetByte(addr);
        return val << 16 | 0xff000000;
    }
    public static int load_RG_88_u(Object base, long addr) {
        int val = MemoryUtil.memGetShort(addr);
        if (NATIVE_BIG_ENDIAN) {
            return val << 8 | 0xff000000;
        } else {
            return (val & 0xff00) | (val & 0xff) << 16 | 0xff000000;
        }
    }
    @SuppressWarnings("PointlessArithmeticExpression")
    public static int load_RGB_888_u(Object base, long addr) {
        int r = MemoryUtil.memGetByte(addr+0) & 0xff;
        int g = MemoryUtil.memGetByte(addr+1) & 0xff;
        int b = MemoryUtil.memGetByte(addr+2) & 0xff;
        return b | g << 8 | r << 16 | 0xff000000;
    }
    public static int load_RGBX_8888_u(Object base, long addr) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            return val >>> 8 | 0xff000000;
        } else {
            return (val & 0xff00) | (val & 0xff) << 16 | ((val >>> 16) & 0xff) | 0xff000000;
        }
    }
    public static int load_RGBA_8888_u(Object base, long addr) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            return val >>> 8 | val << 24;
        } else {
            return (val & 0xff00ff00) | (val & 0xff) << 16 | ((val >>> 16) & 0xff);
        }
    }
    public static int load_BGRA_8888_u(Object base, long addr) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            return Integer.reverseBytes(val);
        } else {
            return val;
        }
    }
    public static int load_GRAY_8_u(Object base, long addr) {
        int val = MemoryUtil.memGetByte(addr) & 0xff;
        return val | val << 8 | val << 16 | 0xff000000;
    }
    public static int load_GRAY_ALPHA_88_u(Object base, long addr) {
        int val = MemoryUtil.memGetShort(addr);
        if (NATIVE_BIG_ENDIAN) {
            int lum = val & 0xff00;
            return lum << 8 | lum | lum >>> 8 | (val & 0xff) << 24;
        } else {
            int lum = val & 0xff;
            return (val << 16) | (lum << 8) | lum;
        }
    }
    public static int load_ALPHA_8_u(Object base, long addr) {
        int val = MemoryUtil.memGetByte(addr);
        return val << 24;
    }

    //----heap----

    public static int load_BGR_565_hb(Object base, long addr) {
        int val = ((short[]) base)[(int) (addr>>1)];
        int b = (((val       ) & 31) * 527 + 23) >> 6;
        int g = (((val >>>  5) & 63) * 259 + 33) >> 6;
        int r = (((val >>> 11) & 31) * 527 + 23) >> 6;
        return b | g << 8 | r << 16 | 0xff000000;
    }
    public static int load_BGRA_5551_hb(Object base, long addr) {
        int val = ((short[]) base)[(int) (addr>>1)];
        int b = (((val       ) & 31) * 527 + 23) >> 6;
        int g = (((val >>>  5) & 31) * 527 + 23) >> 6;
        int r = (((val >>> 10) & 31) * 527 + 23) >> 6;
        int a = (val & (1 << 15)) != 0 ? 255 : 0;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_RGBA_1010102_hb(Object base, long addr) {
        int val = ((int[]) base)[(int) (addr>>2)];
        int r = (((val       ) & 0x3ff) * 255 + 511) / 1023;
        int g = (((val >>> 10) & 0x3ff) * 255 + 511) / 1023;
        int b = (((val >>> 20) & 0x3ff) * 255 + 511) / 1023;
        int a = (val>>> 30) * 85;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_BGRA_1010102_hb(Object base, long addr) {
        int val = ((int[]) base)[(int) (addr>>2)];
        int r = (((val >>> 20) & 0x3ff) * 255 + 511) / 1023;
        int g = (((val >>> 10) & 0x3ff) * 255 + 511) / 1023;
        int b = (((val       ) & 0x3ff) * 255 + 511) / 1023;
        int a = (val>>> 30) * 85;
        return b | g << 8 | r << 16 | a << 24;
    }
    public static int load_R_8_hb(Object base, long addr) {
        int val = ((byte[]) base)[(int) addr];
        return val << 16 | 0xff000000;
    }
    public static int load_RG_88_hb(Object base, long addr) {
        int val = (short) BYTE_ARRAY_AS_SHORT.get((byte[]) base, (int) addr);
        if (NATIVE_BIG_ENDIAN) {
            return val << 8 | 0xff000000;
        } else {
            return (val & 0xff00) | (val & 0xff) << 16 | 0xff000000;
        }
    }
    public static int load_RGB_888_hb(Object base, long addr) {
        byte[] hb = (byte[]) base;
        int index = (int) (addr);
        int r = hb[index] & 0xff;
        int g = hb[index+1] & 0xff;
        int b = hb[index+2] & 0xff;
        return b | g << 8 | r << 16 | 0xff000000;
    }
    public static int load_RGBX_8888_hb(Object base, long addr) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            return val >>> 8 | 0xff000000;
        } else {
            return (val & 0xff00) | (val & 0xff) << 16 | ((val >>> 16) & 0xff) | 0xff000000;
        }
    }
    public static int load_RGBA_8888_hb(Object base, long addr) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            return val >>> 8 | val << 24;
        } else {
            return (val & 0xff00ff00) | (val & 0xff) << 16 | ((val >>> 16) & 0xff);
        }
    }
    public static int load_BGRA_8888_hb(Object base, long addr) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            return Integer.reverseBytes(val);
        } else {
            return val;
        }
    }
    public static int load_GRAY_8_hb(Object base, long addr) {
        int val = ((byte[]) base)[(int) addr] & 0xff;
        return val | val << 8 | val << 16 | 0xff000000;
    }
    public static int load_GRAY_ALPHA_88_hb(Object base, long addr) {
        int val = (short) BYTE_ARRAY_AS_SHORT.get((byte[]) base, (int) addr);
        if (NATIVE_BIG_ENDIAN) {
            int lum = val & 0xff00;
            return lum << 8 | lum | lum >>> 8 | (val & 0xff) << 24;
        } else {
            int lum = val & 0xff;
            return (val << 16) | (lum << 8) | lum;
        }
    }
    public static int load_ALPHA_8_hb(Object base, long addr) {
        int val = ((byte[]) base)[(int) addr];
        return val << 24;
    }

    /**
     * Load a pixel value in low precision.
     */
    @NonNull
    @Contract(pure = true)
    public static PixelLoad load(@ColorInfo.ColorType int ct, boolean isU) {
        if (isU) {
            // off heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::load_BGR_565_u;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::load_BGRA_5551_u;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::load_RGBA_1010102_u;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::load_BGRA_1010102_u;
                case ColorInfo.CT_R_8             -> PixelUtils::load_R_8_u;
                case ColorInfo.CT_RG_88           -> PixelUtils::load_RG_88_u;
                case ColorInfo.CT_RGB_888         -> PixelUtils::load_RGB_888_u;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::load_RGBX_8888_u;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::load_RGBA_8888_u;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::load_BGRA_8888_u;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::load_GRAY_8_u;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::load_GRAY_ALPHA_88_u;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::load_ALPHA_8_u;
                case ColorInfo.CT_R_16,
                     ColorInfo.CT_RG_1616,
                     ColorInfo.CT_RGB_161616,
                     ColorInfo.CT_RGBA_16161616,
                     ColorInfo.CT_GRAY_16,
                     ColorInfo.CT_GRAY_ALPHA_1616,
                     ColorInfo.CT_ALPHA_16,
                     ColorInfo.CT_R_F16,
                     ColorInfo.CT_RG_F16,
                     ColorInfo.CT_RGBA_F16,
                     ColorInfo.CT_ALPHA_F16,
                     ColorInfo.CT_R_F32,
                     ColorInfo.CT_RG_F32,
                     ColorInfo.CT_RGBA_F32 -> throw new UnsupportedOperationException();
                default -> throw new AssertionError(ct);
            };
        } else {
            // heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::load_BGR_565_hb;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::load_BGRA_5551_hb;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::load_RGBA_1010102_hb;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::load_BGRA_1010102_hb;
                case ColorInfo.CT_R_8             -> PixelUtils::load_R_8_hb;
                case ColorInfo.CT_RG_88           -> PixelUtils::load_RG_88_hb;
                case ColorInfo.CT_RGB_888         -> PixelUtils::load_RGB_888_hb;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::load_RGBX_8888_hb;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::load_RGBA_8888_hb;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::load_BGRA_8888_hb;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::load_GRAY_8_hb;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::load_GRAY_ALPHA_88_hb;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::load_ALPHA_8_hb;
                case ColorInfo.CT_R_16,
                     ColorInfo.CT_RG_1616,
                     ColorInfo.CT_RGB_161616,
                     ColorInfo.CT_RGBA_16161616,
                     ColorInfo.CT_GRAY_16,
                     ColorInfo.CT_GRAY_ALPHA_1616,
                     ColorInfo.CT_ALPHA_16,
                     ColorInfo.CT_R_F16,
                     ColorInfo.CT_RG_F16,
                     ColorInfo.CT_RGBA_F16,
                     ColorInfo.CT_ALPHA_F16,
                     ColorInfo.CT_R_F32,
                     ColorInfo.CT_RG_F32,
                     ColorInfo.CT_RGBA_F32 -> throw new UnsupportedOperationException();
                default -> throw new AssertionError(ct);
            };
        }
    }
    //@formatter:on

    /**
     * Store pixel value in low precision.
     */
    @FunctionalInterface
    public interface PixelStore {
        void store(Object base, long addr, @ColorInt int src);
    }

    //@formatter:off
    public static void store_BGR_565_u(Object base, long addr, int src) {
        int r = (src >>> 16) & 0xff;
        int g = (src >>>  8) & 0xff;
        int b = (src       ) & 0xff;
        // Round from [0,255] to [0,31] or [0,63], as if x * (31/255.0f) + 0.5f.
        // (Don't feel like you need to find some fundamental truth in these...
        // they were brute-force searched.)
        r = (r *  9 + 36) / 74; //  9/74 ≈ 31/255, plus 36/74, about half.
        g = (g * 21 + 42) / 85; // 21/85 = 63/255 exactly.
        b = (b *  9 + 36) / 74;
        MemoryUtil.memPutShort(addr, (short) (b | g << 5 | r << 11));
    }
    public static void store_BGRA_5551_u(Object base, long addr, int src) {
        int r = (src >>> 16) & 0xff;
        int g = (src >>>  8) & 0xff;
        int b = (src       ) & 0xff;
        r = (r * 9 + 36) / 74;
        g = (g * 9 + 36) / 74;
        b = (b * 9 + 36) / 74;
        MemoryUtil.memPutShort(addr, (short) (b | g << 5 | r << 10 | ((src >>> 16) & (1 << 15))));
    }
    public static void store_RGBA_1010102_u(Object base, long addr, int src) {
        int r = (((src >> 16) & 0xff) * 1023 + 127) / 255;
        int g = (((src >> 8) & 0xff) * 1023 + 127) / 255;
        int b = ((src & 0xff) * 1023 + 127) / 255;
        int a = ((src >>> 24) * 3 + 511) / 1023;
        MemoryUtil.memPutInt(addr, r | g << 10 | b << 20 | a << 30);
    }
    public static void store_BGRA_1010102_u(Object base, long addr, int src) {
        int r = (((src >> 16) & 0xff) * 1023 + 127) / 255;
        int g = (((src >> 8) & 0xff) * 1023 + 127) / 255;
        int b = ((src & 0xff) * 1023 + 127) / 255;
        int a = ((src >>> 24) * 3 + 511) / 1023;
        MemoryUtil.memPutInt(addr, b | g << 10 | r << 20 | a << 30);
    }
    public static void store_R_8_u(Object base, long addr, int src) {
        MemoryUtil.memPutByte(addr, (byte) (src >>> 16));
    }
    public static void store_RG_88_u(Object base, long addr, int src) {
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RG
            MemoryUtil.memPutShort(addr, (short) (src >>> 8));
        } else {
            // ARGB -> GR
            MemoryUtil.memPutShort(addr, (short) (((src >>> 16) & 0xff) | (src & 0xff00)));
        }
    }
    @SuppressWarnings("PointlessArithmeticExpression")
    public static void store_RGB_888_u(Object base, long addr, int src) {
        MemoryUtil.memPutByte(addr+0, (byte) (src >>> 16));
        MemoryUtil.memPutByte(addr+1, (byte) (src >>>  8));
        MemoryUtil.memPutByte(addr+2, (byte) (src       ));
    }
    public static void store_RGBX_8888_u(Object base, long addr, int src) {
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RGBX
            MemoryUtil.memPutInt(addr, src << 8 | 0xff);
        } else {
            // ARGB -> XBGR
            MemoryUtil.memPutInt(addr, (src & 0xff00) | (src & 0xff) << 16 | ((src >>> 16) & 0xff) | 0xff000000);
        }
    }
    public static void store_RGBA_8888_u(Object base, long addr, int src) {
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RGBA
            MemoryUtil.memPutInt(addr, src << 8 | src >>> 24);
        } else {
            // ARGB -> ABGR
            MemoryUtil.memPutInt(addr, (src & 0xff00ff00) | (src & 0xff) << 16 | ((src >>> 16) & 0xff));
        }
    }
    public static void store_BGRA_8888_u(Object base, long addr, int src) {
        if (NATIVE_BIG_ENDIAN) {
            MemoryUtil.memPutInt(addr, Integer.reverseBytes(src));
        } else {
            MemoryUtil.memPutInt(addr, src);
        }
    }
    public static void store_GRAY_8_u(Object base, long addr, int src) {
        float y = ((src >>> 16) & 0xff) * 0.2126f +
                  ((src >>>  8) & 0xff) * 0.7152f +
                  ((src       ) & 0xff) * 0.0722f;
        MemoryUtil.memPutByte(addr, (byte) (y + .5f));
    }
    public static void store_GRAY_ALPHA_88_u(Object base, long addr, int src) {
        float y = ((src >>> 16) & 0xff) * 0.2126f +
                  ((src >>>  8) & 0xff) * 0.7152f +
                  ((src       ) & 0xff) * 0.0722f;
        if (NATIVE_BIG_ENDIAN) {
            MemoryUtil.memPutShort(addr, (short) ((int) (y + .5f) << 8 | src >>> 24));
        } else {
            MemoryUtil.memPutShort(addr, (short) ((int) (y + .5f) | ((src >>> 16) & 0xff00)));
        }
    }
    public static void store_ALPHA_8_u(Object base, long addr, int src) {
        MemoryUtil.memPutByte(addr, (byte) (src >>> 24));
    }

    //----heap----

    public static void store_BGR_565_hb(Object base, long addr, int src) {
        int r = (src >>> 16) & 0xff;
        int g = (src >>>  8) & 0xff;
        int b = (src       ) & 0xff;
        r = (r *  9 + 36) / 74;
        g = (g * 21 + 42) / 85;
        b = (b *  9 + 36) / 74;
        ((short[]) base)[(int) (addr>>1)] = (short) (b | g << 5 | r << 11);
    }
    public static void store_BGRA_5551_hb(Object base, long addr, int src) {
        int r = (src >>> 16) & 0xff;
        int g = (src >>>  8) & 0xff;
        int b = (src       ) & 0xff;
        r = (r * 9 + 36) / 74;
        g = (g * 9 + 36) / 74;
        b = (b * 9 + 36) / 74;
        ((short[]) base)[(int) (addr>>1)] = (short) (b | g << 5 | r << 10 | ((src >>> 16) & (1 << 15)));
    }
    public static void store_RGBA_1010102_hb(Object base, long addr, int src) {
        int r = (((src >> 16) & 0xff) * 1023 + 127) / 255;
        int g = (((src >> 8) & 0xff) * 1023 + 127) / 255;
        int b = ((src & 0xff) * 1023 + 127) / 255;
        int a = ((src >>> 24) * 3 + 511) / 1023;
        ((int[]) base)[(int) (addr>>2)] = r | g << 10 | b << 20 | a << 30;
    }
    public static void store_BGRA_1010102_hb(Object base, long addr, int src) {
        int r = (((src >> 16) & 0xff) * 1023 + 127) / 255;
        int g = (((src >> 8) & 0xff) * 1023 + 127) / 255;
        int b = ((src & 0xff) * 1023 + 127) / 255;
        int a = ((src >>> 24) * 3 + 511) / 1023;
        ((int[]) base)[(int) (addr>>2)] = b | g << 10 | r << 20 | a << 30;
    }
    public static void store_R_8_hb(Object base, long addr, int src) {
        ((byte[]) base)[(int) addr] = (byte) (src >>> 16);
    }
    public static void store_RG_88_hb(Object base, long addr, int src) {
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RG
            BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) (src >>> 8));
        } else {
            // ARGB -> GR
            BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) (((src >>> 16) & 0xff) | (src & 0xff00)));
        }
    }
    public static void store_RGB_888_hb(Object base, long addr, int src) {
        byte[] hb = (byte[]) base;
        int index = (int) (addr);
        hb[index] = (byte) (src >>> 16);
        hb[index+1] = (byte) (src >>>  8);
        hb[index+2] = (byte) (src       );
    }
    public static void store_RGBX_8888_hb(Object base, long addr, int src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RGBX
            val = src << 8 | 0xff;
        } else {
            // ARGB -> XBGR
            val = (src & 0xff00) | (src & 0xff) << 16 | ((src >>> 16) & 0xff) | 0xff000000;
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }
    public static void store_RGBA_8888_hb(Object base, long addr, int src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            // ARGB -> RGBA
            val = src << 8 | src >>> 24;
        } else {
            // ARGB -> ABGR
            val = (src & 0xff00ff00) | (src & 0xff) << 16 | ((src >>> 16) & 0xff);
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }
    public static void store_BGRA_8888_hb(Object base, long addr, int src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = Integer.reverseBytes(src);
        } else {
            val = src;
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }
    public static void store_GRAY_8_hb(Object base, long addr, int src) {
        float y = ((src >>> 16) & 0xff) * 0.2126f +
                  ((src >>>  8) & 0xff) * 0.7152f +
                  ((src       ) & 0xff) * 0.0722f;
        ((byte[]) base)[(int) addr] = (byte) (y + .5f);
    }
    public static void store_GRAY_ALPHA_88_hb(Object base, long addr, int src) {
        float y = ((src >>> 16) & 0xff) * 0.2126f +
                  ((src >>>  8) & 0xff) * 0.7152f +
                  ((src       ) & 0xff) * 0.0722f;
        if (NATIVE_BIG_ENDIAN) {
            BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) ((int) (y + .5f) << 8 | src >>> 24));
        } else {
            BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) ((int) (y + .5f) | ((src >>> 16) & 0xff00)));
        }
    }
    public static void store_ALPHA_8_hb(Object base, long addr, int src) {
        ((byte[]) base)[(int) addr] = (byte) (src >>> 24);
    }

    /**
     * Store a pixel value in low precision.
     */
    @NonNull
    @Contract(pure = true)
    public static PixelStore store(@ColorInfo.ColorType int ct, boolean isU) {
        if (isU) {
            return switch (ct) {
                case ColorInfo.CT_BGR_565       -> PixelUtils::store_BGR_565_u;
                case ColorInfo.CT_BGRA_5551     -> PixelUtils::store_BGRA_5551_u;
                case ColorInfo.CT_RGBA_1010102  -> PixelUtils::store_RGBA_1010102_u;
                case ColorInfo.CT_BGRA_1010102  -> PixelUtils::store_BGRA_1010102_u;
                case ColorInfo.CT_R_8           -> PixelUtils::store_R_8_u;
                case ColorInfo.CT_RG_88         -> PixelUtils::store_RG_88_u;
                case ColorInfo.CT_RGB_888       -> PixelUtils::store_RGB_888_u;
                case ColorInfo.CT_RGBX_8888     -> PixelUtils::store_RGBX_8888_u;
                case ColorInfo.CT_RGBA_8888     -> PixelUtils::store_RGBA_8888_u;
                case ColorInfo.CT_BGRA_8888     -> PixelUtils::store_BGRA_8888_u;
                case ColorInfo.CT_GRAY_8        -> PixelUtils::store_GRAY_8_u;
                case ColorInfo.CT_GRAY_ALPHA_88 -> PixelUtils::store_GRAY_ALPHA_88_u;
                case ColorInfo.CT_ALPHA_8       -> PixelUtils::store_ALPHA_8_u;
                case ColorInfo.CT_R_16,
                     ColorInfo.CT_RG_1616,
                     ColorInfo.CT_RGB_161616,
                     ColorInfo.CT_RGBA_16161616,
                     ColorInfo.CT_GRAY_16,
                     ColorInfo.CT_GRAY_ALPHA_1616,
                     ColorInfo.CT_ALPHA_16,
                     ColorInfo.CT_R_F16,
                     ColorInfo.CT_RG_F16,
                     ColorInfo.CT_RGBA_F16,
                     ColorInfo.CT_ALPHA_F16,
                     ColorInfo.CT_R_F32,
                     ColorInfo.CT_RG_F32,
                     ColorInfo.CT_RGBA_F32 -> throw new UnsupportedOperationException();
                default -> throw new AssertionError(ct);
            };
        } else {
            return switch (ct) {
                case ColorInfo.CT_BGR_565       -> PixelUtils::store_BGR_565_hb;
                case ColorInfo.CT_BGRA_5551     -> PixelUtils::store_BGRA_5551_hb;
                case ColorInfo.CT_RGBA_1010102  -> PixelUtils::store_RGBA_1010102_hb;
                case ColorInfo.CT_BGRA_1010102  -> PixelUtils::store_BGRA_1010102_hb;
                case ColorInfo.CT_R_8           -> PixelUtils::store_R_8_hb;
                case ColorInfo.CT_RG_88         -> PixelUtils::store_RG_88_hb;
                case ColorInfo.CT_RGB_888       -> PixelUtils::store_RGB_888_hb;
                case ColorInfo.CT_RGBX_8888     -> PixelUtils::store_RGBX_8888_hb;
                case ColorInfo.CT_RGBA_8888     -> PixelUtils::store_RGBA_8888_hb;
                case ColorInfo.CT_BGRA_8888     -> PixelUtils::store_BGRA_8888_hb;
                case ColorInfo.CT_GRAY_8        -> PixelUtils::store_GRAY_8_hb;
                case ColorInfo.CT_GRAY_ALPHA_88 -> PixelUtils::store_GRAY_ALPHA_88_hb;
                case ColorInfo.CT_ALPHA_8       -> PixelUtils::store_ALPHA_8_hb;
                case ColorInfo.CT_R_16,
                     ColorInfo.CT_RG_1616,
                     ColorInfo.CT_RGB_161616,
                     ColorInfo.CT_RGBA_16161616,
                     ColorInfo.CT_GRAY_16,
                     ColorInfo.CT_GRAY_ALPHA_1616,
                     ColorInfo.CT_ALPHA_16,
                     ColorInfo.CT_R_F16,
                     ColorInfo.CT_RG_F16,
                     ColorInfo.CT_RGBA_F16,
                     ColorInfo.CT_ALPHA_F16,
                     ColorInfo.CT_R_F32,
                     ColorInfo.CT_RG_F32,
                     ColorInfo.CT_RGBA_F32 -> throw new UnsupportedOperationException();
                default -> throw new AssertionError(ct);
            };
        }
    }
    //@formatter:on

    /**
     * Load or store pixel value in high precision.
     */
    @FunctionalInterface
    public interface PixelOp {
        void op(Object base, long addr, /*Color4f*/ float[/*4*/] col);
    }

    //---- load ops ----

    //@formatter:off

    //---- off heap ----

    public static void load_BGR_565_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetShort(addr);
        dst[0] = (val & (31<<11)) * (1.0f / (31<<11));
        dst[1] = (val & (63<< 5)) * (1.0f / (63<< 5));
        dst[2] = (val & (31    )) * (1.0f / (31    ));
        dst[3] = 1.0f;
    }

    public static void load_BGRA_5551_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetShort(addr);
        dst[0] = (val & (31<<10)) * (1.0f / (31<<10));
        dst[1] = (val & (31<< 5)) * (1.0f / (31<< 5));
        dst[2] = (val & (31    )) * (1.0f / (31    ));
        dst[3] = (val & (1 <<15)) != 0 ? 1.0f : 0.0f;
    }

    public static void load_RGBA_1010102_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        dst[0] = ((val       ) & 0x3ff) * (1.0f/1023);
        dst[1] = ((val >>> 10) & 0x3ff) * (1.0f/1023);
        dst[2] = ((val >>> 20) & 0x3ff) * (1.0f/1023);
        dst[3] = ((val >>> 30)        ) * (1.0f/   3);
    }

    public static void load_BGRA_1010102_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        dst[0] = ((val >>> 20) & 0x3ff) * (1.0f/1023);
        dst[1] = ((val >>> 10) & 0x3ff) * (1.0f/1023);
        dst[2] = ((val       ) & 0x3ff) * (1.0f/1023);
        dst[3] = ((val >>> 30)        ) * (1.0f/   3);
    }

    public static void load_R_8_u(Object base, long addr, float[] dst) {
        dst[0] = (MemoryUtil.memGetByte(addr) & 0xff) * (1/255.0f);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_88_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetShort(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 8) & 0xff) * (1/255.0f);
            dst[1] = ((val      ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val      ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>> 8) & 0xff) * (1/255.0f);
        }
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGB_888_u(Object base, long addr, float[] dst) {
        for (int i = 0; i < 3; i++) {
            dst[i] = (MemoryUtil.memGetByte(addr+i) & 0xff) * (1/255.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBX_8888_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 24)       ) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>>  8) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val       ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 16) & 0xff) * (1/255.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBA_8888_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 24)       ) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[3] = ((val       ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val       ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[3] = ((val >>> 24)       ) * (1/255.0f);
        }
    }

    public static void load_BGRA_8888_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 24)       ) * (1/255.0f);
            dst[3] = ((val       ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val       ) & 0xff) * (1/255.0f);
            dst[3] = ((val >>> 24)       ) * (1/255.0f);
        }
    }

    public static void load_GRAY_8_u(Object base, long addr, float[] dst) {
        float y = (MemoryUtil.memGetByte(addr) & 0xff) * (1/255.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = 1.0f;
    }

    public static void load_GRAY_ALPHA_88_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetShort(addr);
        if (NATIVE_BIG_ENDIAN) {
            float y = ((val >>> 8) & 0xff) * (1/255.0f);
            dst[0] = dst[1] = dst[2] = y;
            dst[3] = ((val      ) & 0xff) * (1/255.0f);
        } else {
            float y = ((val      ) & 0xff) * (1/255.0f);
            dst[0] = dst[1] = dst[2] = y;
            dst[3] = ((val >>> 8) & 0xff) * (1/255.0f);
        }
    }

    public static void load_ALPHA_8_u(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = (MemoryUtil.memGetByte(addr) & 0xff) * (1/255.0f);
    }

    public static void load_R_16_u(Object base, long addr, float[] dst) {
        dst[0] = (MemoryUtil.memGetShort(addr) & 0xffff) * (1/65535.0f);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_1616_u(Object base, long addr, float[] dst) {
        int val = MemoryUtil.memGetInt(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 16)         ) * (1/65535.0f);
            dst[1] = ((val       ) & 0xffff) * (1/65535.0f);
        } else {
            dst[0] = ((val       ) & 0xffff) * (1/65535.0f);
            dst[1] = ((val >>> 16)         ) * (1/65535.0f);
        }
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGB_161616_u(Object base, long addr, float[] dst) {
        for (int i = 0; i < 3; i++) {
            dst[i] = (MemoryUtil.memGetShort(addr+(i<<1)) & 0xffff) * (1/65535.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBA_16161616_u(Object base, long addr, float[] dst) {
        long val = MemoryUtil.memGetLong(addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 48)         ) * (1/65535.0f);
            dst[1] = ((val >>> 32) & 0xffff) * (1/65535.0f);
            dst[2] = ((val >>> 16) & 0xffff) * (1/65535.0f);
            dst[3] = ((val       ) & 0xffff) * (1/65535.0f);
        } else {
            dst[0] = ((val       ) & 0xffff) * (1/65535.0f);
            dst[1] = ((val >>> 16) & 0xffff) * (1/65535.0f);
            dst[2] = ((val >>> 32) & 0xffff) * (1/65535.0f);
            dst[3] = ((val >>> 48)         ) * (1/65535.0f);
        }
    }

    public static void load_GRAY_16_u(Object base, long addr, float[] dst) {
        float y = (MemoryUtil.memGetShort(addr) & 0xffff) * (1/65535.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = 1.0f;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void load_GRAY_ALPHA_1616_u(Object base, long addr, float[] dst) {
        float y = (MemoryUtil.memGetShort(addr+0) & 0xffff) * (1/65535.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = (MemoryUtil.memGetShort(addr+2) & 0xffff) * (1/65535.0f);
    }

    public static void load_ALPHA_16_u(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = (MemoryUtil.memGetShort(addr) & 0xffff) * (1/65535.0f);
    }

    public static void load_R_F16_u(Object base, long addr, float[] dst) {
        dst[0] = MathUtil.halfToFloat(MemoryUtil.memGetShort(addr));
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void load_RG_F16_u(Object base, long addr, float[] dst) {
        dst[0] = MathUtil.halfToFloat(MemoryUtil.memGetShort(addr+0));
        dst[1] = MathUtil.halfToFloat(MemoryUtil.memGetShort(addr+2));
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGBA_F16_u(Object base, long addr, float[] dst) {
        for (int i = 0; i < 4; i++) {
            dst[i] = MathUtil.halfToFloat(MemoryUtil.memGetShort(addr + (i<<1)));
        }
    }

    public static void load_ALPHA_F16_u(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = MathUtil.halfToFloat(MemoryUtil.memGetShort(addr));
    }

    public static void load_R_F32_u(Object base, long addr, float[] dst) {
        dst[0] = MemoryUtil.memGetFloat(addr);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void load_RG_F32_u(Object base, long addr, float[] dst) {
        dst[0] = MemoryUtil.memGetFloat(addr + 0);
        dst[1] = MemoryUtil.memGetFloat(addr + 4);
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void load_RGBA_F32_u(Object base, long addr, float[] dst) {
        dst[0] = MemoryUtil.memGetFloat(addr +  0);
        dst[1] = MemoryUtil.memGetFloat(addr +  4);
        dst[2] = MemoryUtil.memGetFloat(addr +  8);
        dst[3] = MemoryUtil.memGetFloat(addr + 12);
    }

    //---- heap ----

    public static void load_BGR_565_hb(Object base, long addr, float[] dst) {
        int val = ((short[]) base)[(int) (addr>>1)];
        dst[0] = (val & (31<<11)) * (1.0f / (31<<11));
        dst[1] = (val & (63<< 5)) * (1.0f / (63<< 5));
        dst[2] = (val & (31    )) * (1.0f / (31    ));
        dst[3] = 1.0f;
    }

    public static void load_BGRA_5551_hb(Object base, long addr, float[] dst) {
        int val = ((short[]) base)[(int) (addr>>1)];
        dst[0] = (val & (31<<10)) * (1.0f / (31<<10));
        dst[1] = (val & (31<< 5)) * (1.0f / (31<< 5));
        dst[2] = (val & (31    )) * (1.0f / (31    ));
        dst[3] = (val & (1 <<15)) != 0 ? 1.0f : 0.0f;
    }

    public static void load_RGBA_1010102_hb(Object base, long addr, float[] dst) {
        int val = ((int[]) base)[(int) (addr>>2)];
        dst[0] = ((val       ) & 0x3ff) * (1.0f/1023);
        dst[1] = ((val >>> 10) & 0x3ff) * (1.0f/1023);
        dst[2] = ((val >>> 20) & 0x3ff) * (1.0f/1023);
        dst[3] = ((val >>> 30)        ) * (1.0f/   3);
    }

    public static void load_BGRA_1010102_hb(Object base, long addr, float[] dst) {
        int val = ((int[]) base)[(int) (addr>>2)];
        dst[0] = ((val >>> 20) & 0x3ff) * (1.0f/1023);
        dst[1] = ((val >>> 10) & 0x3ff) * (1.0f/1023);
        dst[2] = ((val       ) & 0x3ff) * (1.0f/1023);
        dst[3] = ((val >>> 30)        ) * (1.0f/   3);
    }

    public static void load_R_8_hb(Object base, long addr, float[] dst) {
        dst[0] = (((byte[]) base)[(int) addr] & 0xff) * (1/255.0f);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_88_hb(Object base, long addr, float[] dst) {
        int val = (short) BYTE_ARRAY_AS_SHORT.get((byte[]) base, (int) addr);
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 8) & 0xff) * (1/255.0f);
            dst[1] = ((val      ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val      ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>> 8) & 0xff) * (1/255.0f);
        }
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGB_888_hb(Object base, long addr, float[] dst) {
        byte[] hb = (byte[]) base;
        int index = (int) (addr);
        for (int i = 0; i < 3; i++) {
            dst[i] = (hb[index+i] & 0xff) * (1/255.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBX_8888_hb(Object base, long addr, float[] dst) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 24)       ) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>>  8) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val       ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 16) & 0xff) * (1/255.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBA_8888_hb(Object base, long addr, float[] dst) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>> 24)       ) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[3] = ((val       ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val       ) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[3] = ((val >>> 24)       ) * (1/255.0f);
        }
    }

    public static void load_BGRA_8888_hb(Object base, long addr, float[] dst) {
        int val;
        if (base instanceof byte[]) {
            val = (int) BYTE_ARRAY_AS_INT.get((byte[]) base, (int) addr);
        } else {
            val = ((int[]) base)[(int) (addr>>2)];
        }
        if (NATIVE_BIG_ENDIAN) {
            dst[0] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[1] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[2] = ((val >>> 24)       ) * (1/255.0f);
            dst[3] = ((val       ) & 0xff) * (1/255.0f);
        } else {
            dst[0] = ((val >>> 16) & 0xff) * (1/255.0f);
            dst[1] = ((val >>>  8) & 0xff) * (1/255.0f);
            dst[2] = ((val       ) & 0xff) * (1/255.0f);
            dst[3] = ((val >>> 24)       ) * (1/255.0f);
        }
    }

    public static void load_GRAY_8_hb(Object base, long addr, float[] dst) {
        float y = (((byte[]) base)[(int) addr] & 0xff) * (1/255.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = 1.0f;
    }

    public static void load_GRAY_ALPHA_88_hb(Object base, long addr, float[] dst) {
        int val = (short) BYTE_ARRAY_AS_SHORT.get((byte[]) base, (int) addr);
        if (NATIVE_BIG_ENDIAN) {
            float y = ((val >>> 8) & 0xff) * (1/255.0f);
            dst[0] = dst[1] = dst[2] = y;
            dst[3] = ((val      ) & 0xff) * (1/255.0f);
        } else {
            float y = ((val      ) & 0xff) * (1/255.0f);
            dst[0] = dst[1] = dst[2] = y;
            dst[3] = ((val >>> 8) & 0xff) * (1/255.0f);
        }
    }

    public static void load_ALPHA_8_hb(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = (((byte[]) base)[(int) addr] & 0xff) * (1/255.0f);
    }

    public static void load_R_16_hb(Object base, long addr, float[] dst) {
        dst[0] = (((short[]) base)[(int) (addr>>1)] & 0xffff) * (1/65535.0f);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_1616_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        dst[0] = (hb[index]   & 0xffff) * (1/65535.0f);
        dst[1] = (hb[index+1] & 0xffff) * (1/65535.0f);
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGB_161616_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 3; i++) {
            dst[i] = (hb[index+i] & 0xffff) * (1/65535.0f);
        }
        dst[3] = 1.0f;
    }

    public static void load_RGBA_16161616_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 4; i++) {
            dst[i] = (hb[index+i] & 0xffff) * (1/65535.0f);
        }
    }

    public static void load_GRAY_16_hb(Object base, long addr, float[] dst) {
        float y = (((short[]) base)[(int) (addr>>1)] & 0xffff) * (1/65535.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = 1.0f;
    }

    public static void load_GRAY_ALPHA_1616_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        float y = (hb[index] & 0xffff) * (1/65535.0f);
        dst[0] = dst[1] = dst[2] = y;
        dst[3] = (hb[index+1] & 0xffff) * (1/65535.0f);
    }

    public static void load_ALPHA_16_hb(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = (((short[]) base)[(int) (addr>>1)] & 0xffff) * (1/65535.0f);
    }

    public static void load_R_F16_hb(Object base, long addr, float[] dst) {
        dst[0] = MathUtil.halfToFloat(((short[]) base)[(int) (addr>>1)]);
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_F16_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        dst[0] = MathUtil.halfToFloat(hb[index]);
        dst[1] = MathUtil.halfToFloat(hb[index+1]);
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGBA_F16_hb(Object base, long addr, float[] dst) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 4; i++) {
            dst[i] = MathUtil.halfToFloat(hb[index+i]);
        }
    }

    public static void load_ALPHA_F16_hb(Object base, long addr, float[] dst) {
        dst[0] = dst[1] = dst[2] = 0.0f;
        dst[3] = MathUtil.halfToFloat(((short[]) base)[(int) (addr>>1)]);
    }

    public static void load_R_F32_hb(Object base, long addr, float[] dst) {
        dst[0] = ((float[]) base)[(int) (addr>>2)];
        dst[1] = dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RG_F32_hb(Object base, long addr, float[] dst) {
        float[] hb = (float[]) base;
        int index = (int) (addr>>2);
        dst[0] = hb[index];
        dst[1] = hb[index+1];
        dst[2] = 0.0f;
        dst[3] = 1.0f;
    }

    public static void load_RGBA_F32_hb(Object base, long addr, float[] dst) {
        float[] hb = (float[]) base;
        System.arraycopy(hb, (int) (addr>>2), dst, 0, 4);
    }

    /**
     * Load a pixel value in high precision.
     */
    @NonNull
    @Contract(pure = true)
    public static PixelOp loadOp(@ColorInfo.ColorType int ct, boolean isU) {
        if (isU) {
            // off heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::load_BGR_565_u;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::load_BGRA_5551_u;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::load_RGBA_1010102_u;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::load_BGRA_1010102_u;
                case ColorInfo.CT_R_8             -> PixelUtils::load_R_8_u;
                case ColorInfo.CT_RG_88           -> PixelUtils::load_RG_88_u;
                case ColorInfo.CT_RGB_888         -> PixelUtils::load_RGB_888_u;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::load_RGBX_8888_u;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::load_RGBA_8888_u;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::load_BGRA_8888_u;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::load_GRAY_8_u;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::load_GRAY_ALPHA_88_u;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::load_ALPHA_8_u;
                case ColorInfo.CT_R_16            -> PixelUtils::load_R_16_u;
                case ColorInfo.CT_RG_1616         -> PixelUtils::load_RG_1616_u;
                case ColorInfo.CT_RGB_161616      -> PixelUtils::load_RGB_161616_u;
                case ColorInfo.CT_RGBA_16161616   -> PixelUtils::load_RGBA_16161616_u;
                case ColorInfo.CT_GRAY_16         -> PixelUtils::load_GRAY_16_u;
                case ColorInfo.CT_GRAY_ALPHA_1616 -> PixelUtils::load_GRAY_ALPHA_1616_u;
                case ColorInfo.CT_ALPHA_16        -> PixelUtils::load_ALPHA_16_u;
                case ColorInfo.CT_R_F16           -> PixelUtils::load_R_F16_u;
                case ColorInfo.CT_RG_F16          -> PixelUtils::load_RG_F16_u;
                case ColorInfo.CT_RGBA_F16        -> PixelUtils::load_RGBA_F16_u;
                case ColorInfo.CT_ALPHA_F16       -> PixelUtils::load_ALPHA_F16_u;
                case ColorInfo.CT_R_F32           -> PixelUtils::load_R_F32_u;
                case ColorInfo.CT_RG_F32          -> PixelUtils::load_RG_F32_u;
                case ColorInfo.CT_RGBA_F32        -> PixelUtils::load_RGBA_F32_u;
                default -> throw new AssertionError(ct);
            };
        } else {
            // heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::load_BGR_565_hb;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::load_BGRA_5551_hb;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::load_RGBA_1010102_hb;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::load_BGRA_1010102_hb;
                case ColorInfo.CT_R_8             -> PixelUtils::load_R_8_hb;
                case ColorInfo.CT_RG_88           -> PixelUtils::load_RG_88_hb;
                case ColorInfo.CT_RGB_888         -> PixelUtils::load_RGB_888_hb;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::load_RGBX_8888_hb;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::load_RGBA_8888_hb;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::load_BGRA_8888_hb;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::load_GRAY_8_hb;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::load_GRAY_ALPHA_88_hb;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::load_ALPHA_8_hb;
                case ColorInfo.CT_R_16            -> PixelUtils::load_R_16_hb;
                case ColorInfo.CT_RG_1616         -> PixelUtils::load_RG_1616_hb;
                case ColorInfo.CT_RGB_161616      -> PixelUtils::load_RGB_161616_hb;
                case ColorInfo.CT_RGBA_16161616   -> PixelUtils::load_RGBA_16161616_hb;
                case ColorInfo.CT_GRAY_16         -> PixelUtils::load_GRAY_16_hb;
                case ColorInfo.CT_GRAY_ALPHA_1616 -> PixelUtils::load_GRAY_ALPHA_1616_hb;
                case ColorInfo.CT_ALPHA_16        -> PixelUtils::load_ALPHA_16_hb;
                case ColorInfo.CT_R_F16           -> PixelUtils::load_R_F16_hb;
                case ColorInfo.CT_RG_F16          -> PixelUtils::load_RG_F16_hb;
                case ColorInfo.CT_RGBA_F16        -> PixelUtils::load_RGBA_F16_hb;
                case ColorInfo.CT_ALPHA_F16       -> PixelUtils::load_ALPHA_F16_hb;
                case ColorInfo.CT_R_F32           -> PixelUtils::load_R_F32_hb;
                case ColorInfo.CT_RG_F32          -> PixelUtils::load_RG_F32_hb;
                case ColorInfo.CT_RGBA_F32        -> PixelUtils::load_RGBA_F32_hb;
                default -> throw new AssertionError(ct);
            };
        }
    }
    //@formatter:on

    //---- store ops ----

    //@formatter:off

    //---- off heap ----

    public static void store_BGR_565_u(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 31 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 63 + .5f) << 5  |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 31 + .5f) << 11;
        MemoryUtil.memPutShort(addr, (short) val);
    }

    public static void store_BGRA_5551_u(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 31 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 31 + .5f) << 5  |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 31 + .5f) << 10 |
                  (src[3] >= 0.5f ? (1 << 15) : 0); // NaN to 0
        MemoryUtil.memPutShort(addr, (short) val);
    }

    public static void store_RGBA_1010102_u(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 1023 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 1023 + .5f) << 10 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 1023 + .5f) << 20 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) *    3 + .5f) << 30;
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_BGRA_1010102_u(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 1023 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 1023 + .5f) << 10 |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 1023 + .5f) << 20 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) *    3 + .5f) << 30;
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_R_8_u(Object base, long addr, float[] src) {
        byte val = (byte) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f);
        MemoryUtil.memPutByte(addr, val);
    }

    public static void store_RG_88_u(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 8 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f)      ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)      |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 8 ;
        }
        MemoryUtil.memPutShort(addr, (short) val);
    }

    public static void store_RGB_888_u(Object base, long addr, float[] src) {
        for (int i = 0; i < 3; i++) {
            MemoryUtil.memPutByte(addr+i, (byte) (MathUtil.clamp(src[i], 0.0f, 1.0f) * 255 + .5f));
        }
    }

    public static void store_RGBX_8888_u(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  255;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  255 << 24;
        }
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_RGBA_8888_u(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)       ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 24 ;
        }
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_BGRA_8888_u(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)       ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 24 ;
        }
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_GRAY_8_u(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        byte val = (byte) (y * 255 + .5f);
        MemoryUtil.memPutByte(addr, val);
    }

    public static void store_GRAY_ALPHA_88_u(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (y * 255 + .5f) << 8 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)     ;
        } else {
            val = (int) (y * 255 + .5f)      |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 8;
        }
        MemoryUtil.memPutShort(addr, (short) val);
    }

    public static void store_ALPHA_8_u(Object base, long addr, float[] src) {
        byte val = (byte) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f);
        MemoryUtil.memPutByte(addr, val);
    }

    public static void store_R_16_u(Object base, long addr, float[] src) {
        short val = (short) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f);
        MemoryUtil.memPutShort(addr, val);
    }

    public static void store_RG_1616_u(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 65535 + .5f)       ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 65535 + .5f) << 16 ;
        }
        MemoryUtil.memPutInt(addr, val);
    }

    public static void store_RGB_161616_u(Object base, long addr, float[] src) {
        for (int i = 0; i < 3; i++) {
            MemoryUtil.memPutShort(addr+(i<<1), (short) (MathUtil.clamp(src[i], 0.0f, 1.0f) * 65535 + .5f));
        }
    }

    public static void store_RGBA_16161616_u(Object base, long addr, float[] src) {
        long val;
        if (NATIVE_BIG_ENDIAN) {
            val = (long) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f) << 48 |
                  (long) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 65535 + .5f) << 32 |
                  (long) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 65535 + .5f) << 16 |
                  (long) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f)       ;
        } else {
            val = (long) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f)       |
                  (long) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 65535 + .5f) << 16 |
                  (long) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 65535 + .5f) << 32 |
                  (long) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f) << 48 ;
        }
        MemoryUtil.memPutLong(addr, val);
    }

    public static void store_GRAY_16_u(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        short val = (short) (y * 65535 + .5f);
        MemoryUtil.memPutShort(addr, val);
    }

    public static void store_GRAY_ALPHA_1616_u(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        short r = (short) (y * 65535 + .5f);
        short g = (short) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f);
        MemoryUtil.memPutShort(addr, r);
        MemoryUtil.memPutShort(addr+2, g);
    }

    public static void store_ALPHA_16_u(Object base, long addr, float[] src) {
        short val = (short) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f);
        MemoryUtil.memPutShort(addr, val);
    }

    public static void store_R_F16_u(Object base, long addr, float[] src) {
        short val = MathUtil.floatToHalf(src[0]);
        MemoryUtil.memPutShort(addr, val);
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void store_RG_F16_u(Object base, long addr, float[] src) {
        short r = MathUtil.floatToHalf(src[0]);
        short g = MathUtil.floatToHalf(src[1]);
        MemoryUtil.memPutShort(addr+0, r);
        MemoryUtil.memPutShort(addr+2, g);
    }

    public static void store_RGBA_F16_u(Object base, long addr, float[] src) {
        for (int i = 0; i < 4; i++) {
            MemoryUtil.memPutShort(addr + (i<<1), MathUtil.floatToHalf(src[i]));
        }
    }

    public static void store_ALPHA_F16_u(Object base, long addr, float[] src) {
        short val = MathUtil.floatToHalf(src[3]);
        MemoryUtil.memPutShort(addr, val);
    }

    public static void store_R_F32_u(Object base, long addr, float[] src) {
        MemoryUtil.memPutFloat(addr, src[0]);
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void store_RG_F32_u(Object base, long addr, float[] src) {
        MemoryUtil.memPutFloat(addr +  0, src[0]);
        MemoryUtil.memPutFloat(addr +  4, src[1]);
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static void store_RGBA_F32_u(Object base, long addr, float[] src) {
        MemoryUtil.memPutFloat(addr +  0, src[0]);
        MemoryUtil.memPutFloat(addr +  4, src[1]);
        MemoryUtil.memPutFloat(addr +  8, src[2]);
        MemoryUtil.memPutFloat(addr + 12, src[3]);
    }

    //---- heap ----

    public static void store_BGR_565_hb(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 31 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 63 + .5f) << 5  |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 31 + .5f) << 11;
        ((short[]) base)[(int) (addr>>1)] = (short) val;
    }

    public static void store_BGRA_5551_hb(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 31 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 31 + .5f) << 5  |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 31 + .5f) << 10 |
                  (src[3] >= 0.5f ? (1 << 15) : 0); // NaN to 0
        ((short[]) base)[(int) (addr>>1)] = (short) val;
    }

    public static void store_RGBA_1010102_hb(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 1023 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 1023 + .5f) << 10 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 1023 + .5f) << 20 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) *    3 + .5f) << 30;
        ((int[]) base)[(int) (addr>>2)] = val;
    }

    public static void store_BGRA_1010102_hb(Object base, long addr, float[] src) {
        int val = (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 1023 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 1023 + .5f) << 10 |
                  (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 1023 + .5f) << 20 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) *    3 + .5f) << 30;
        ((int[]) base)[(int) (addr>>2)] = val;
    }

    public static void store_R_8_hb(Object base, long addr, float[] src) {
        byte val = (byte) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f);
        ((byte[]) base)[(int) addr] = val;
    }

    public static void store_RG_88_hb(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 8 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f)      ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)      |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 8 ;
        }
        BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) val);
    }

    public static void store_RGB_888_hb(Object base, long addr, float[] src) {
        byte[] hb = (byte[]) base;
        int index = (int) (addr);
        for (int i = 0; i < 3; i++) {
            hb[index+i] = (byte) (MathUtil.clamp(src[i], 0.0f, 1.0f) * 255 + .5f);
        }
    }

    public static void store_RGBX_8888_hb(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  255;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  255 << 24;
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }

    public static void store_RGBA_8888_hb(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)       ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 24 ;
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }

    public static void store_BGRA_8888_hb(Object base, long addr, float[] src) {
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f) << 24 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)       ;
        } else {
            val = (int) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 255 + .5f) << 16 |
                  (int) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 255 + .5f) <<  8 |
                  (int) (MathUtil.clamp(src[2], 0.0f, 1.0f) * 255 + .5f)       |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 24 ;
        }
        if (base instanceof byte[]) {
            BYTE_ARRAY_AS_INT.set((byte[]) base, (int) addr, val);
        } else {
            ((int[]) base)[(int) (addr>>2)] = val;
        }
    }

    public static void store_GRAY_8_hb(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        byte val = (byte) (y * 255 + .5f);
        ((byte[]) base)[(int) addr] = val;
    }

    public static void store_GRAY_ALPHA_88_hb(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        int val;
        if (NATIVE_BIG_ENDIAN) {
            val = (int) (y * 255 + .5f) << 8 |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f)     ;
        } else {
            val = (int) (y * 255 + .5f)      |
                  (int) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f) << 8;
        }
        BYTE_ARRAY_AS_SHORT.set((byte[]) base, (int) addr, (short) val);
    }

    public static void store_ALPHA_8_hb(Object base, long addr, float[] src) {
        byte val = (byte) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 255 + .5f);
        ((byte[]) base)[(int) addr] = val;
    }

    public static void store_R_16_hb(Object base, long addr, float[] src) {
        short val = (short) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f);
        ((short[]) base)[(int) (addr>>1)] = val;
    }

    public static void store_RG_1616_hb(Object base, long addr, float[] src) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        hb[index] = (short) (MathUtil.clamp(src[0], 0.0f, 1.0f) * 65535 + .5f);
        hb[index+1] = (short) (MathUtil.clamp(src[1], 0.0f, 1.0f) * 65535 + .5f);
    }

    public static void store_RGB_161616_hb(Object base, long addr, float[] src) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 3; i++) {
            hb[index+i] = (short) (MathUtil.clamp(src[i], 0.0f, 1.0f) * 65535 + .5f);
        }
    }

    public static void store_RGBA_16161616_hb(Object base, long addr, float[] src) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 4; i++) {
            hb[index+i] = (short) (MathUtil.clamp(src[i], 0.0f, 1.0f) * 65535 + .5f);
        }
    }

    public static void store_GRAY_16_hb(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        short val = (short) (y * 65535 + .5f);
        ((short[]) base)[(int) (addr>>1)] = val;
    }

    public static void store_GRAY_ALPHA_1616_hb(Object base, long addr, float[] src) {
        float y = MathUtil.clamp(src[0], 0.0f, 1.0f) * 0.2126f +
                  MathUtil.clamp(src[1], 0.0f, 1.0f) * 0.7152f +
                  MathUtil.clamp(src[2], 0.0f, 1.0f) * 0.0722f;
        short r = (short) (y * 65535 + .5f);
        short g = (short) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f);
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        hb[index] = r;
        hb[index+1] = g;
    }

    public static void store_ALPHA_16_hb(Object base, long addr, float[] src) {
        short val = (short) (MathUtil.clamp(src[3], 0.0f, 1.0f) * 65535 + .5f);
        ((short[]) base)[(int) (addr>>1)] = val;
    }

    public static void store_R_F16_hb(Object base, long addr, float[] src) {
        short val = MathUtil.floatToHalf(src[0]);
        ((short[]) base)[(int) (addr>>1)] = val;
    }

    public static void store_RG_F16_hb(Object base, long addr, float[] src) {
        short r = MathUtil.floatToHalf(src[0]);
        short g = MathUtil.floatToHalf(src[1]);
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        hb[index] = r;
        hb[index+1] = g;
    }

    public static void store_RGBA_F16_hb(Object base, long addr, float[] src) {
        short[] hb = (short[]) base;
        int index = (int) (addr>>1);
        for (int i = 0; i < 4; i++) {
            hb[index+i] = MathUtil.floatToHalf(src[i]);
        }
    }

    public static void store_ALPHA_F16_hb(Object base, long addr, float[] src) {
        short val = MathUtil.floatToHalf(src[3]);
        ((short[]) base)[(int) (addr>>1)] = val;
    }

    public static void store_R_F32_hb(Object base, long addr, float[] src) {
        ((float[]) base)[(int) (addr>>2)] = src[0];
    }

    public static void store_RG_F32_hb(Object base, long addr, float[] src) {
        float[] hb = (float[]) base;
        int index = (int) (addr>>2);
        hb[index] = src[0];
        hb[index+1] = src[1];
    }

    public static void store_RGBA_F32_hb(Object base, long addr, float[] src) {
        float[] hb = (float[]) base;
        System.arraycopy(src, 0, hb, (int) (addr>>2), 4);
    }

    /**
     * Store a pixel value in high precision.
     */
    @NonNull
    @Contract(pure = true)
    public static PixelOp storeOp(@ColorInfo.ColorType int ct, boolean isU) {
        if (isU) {
            // off heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::store_BGR_565_u;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::store_BGRA_5551_u;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::store_RGBA_1010102_u;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::store_BGRA_1010102_u;
                case ColorInfo.CT_R_8             -> PixelUtils::store_R_8_u;
                case ColorInfo.CT_RG_88           -> PixelUtils::store_RG_88_u;
                case ColorInfo.CT_RGB_888         -> PixelUtils::store_RGB_888_u;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::store_RGBX_8888_u;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::store_RGBA_8888_u;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::store_BGRA_8888_u;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::store_GRAY_8_u;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::store_GRAY_ALPHA_88_u;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::store_ALPHA_8_u;
                case ColorInfo.CT_R_16            -> PixelUtils::store_R_16_u;
                case ColorInfo.CT_RG_1616         -> PixelUtils::store_RG_1616_u;
                case ColorInfo.CT_RGB_161616      -> PixelUtils::store_RGB_161616_u;
                case ColorInfo.CT_RGBA_16161616   -> PixelUtils::store_RGBA_16161616_u;
                case ColorInfo.CT_GRAY_16         -> PixelUtils::store_GRAY_16_u;
                case ColorInfo.CT_GRAY_ALPHA_1616 -> PixelUtils::store_GRAY_ALPHA_1616_u;
                case ColorInfo.CT_ALPHA_16        -> PixelUtils::store_ALPHA_16_u;
                case ColorInfo.CT_R_F16           -> PixelUtils::store_R_F16_u;
                case ColorInfo.CT_RG_F16          -> PixelUtils::store_RG_F16_u;
                case ColorInfo.CT_RGBA_F16        -> PixelUtils::store_RGBA_F16_u;
                case ColorInfo.CT_ALPHA_F16       -> PixelUtils::store_ALPHA_F16_u;
                case ColorInfo.CT_R_F32           -> PixelUtils::store_R_F32_u;
                case ColorInfo.CT_RG_F32          -> PixelUtils::store_RG_F32_u;
                case ColorInfo.CT_RGBA_F32        -> PixelUtils::store_RGBA_F32_u;
                default -> throw new AssertionError(ct);
            };
        } else {
            // heap
            return switch (ct) {
                case ColorInfo.CT_BGR_565         -> PixelUtils::store_BGR_565_hb;
                case ColorInfo.CT_BGRA_5551       -> PixelUtils::store_BGRA_5551_hb;
                case ColorInfo.CT_RGBA_1010102    -> PixelUtils::store_RGBA_1010102_hb;
                case ColorInfo.CT_BGRA_1010102    -> PixelUtils::store_BGRA_1010102_hb;
                case ColorInfo.CT_R_8             -> PixelUtils::store_R_8_hb;
                case ColorInfo.CT_RG_88           -> PixelUtils::store_RG_88_hb;
                case ColorInfo.CT_RGB_888         -> PixelUtils::store_RGB_888_hb;
                case ColorInfo.CT_RGBX_8888       -> PixelUtils::store_RGBX_8888_hb;
                case ColorInfo.CT_RGBA_8888       -> PixelUtils::store_RGBA_8888_hb;
                case ColorInfo.CT_BGRA_8888       -> PixelUtils::store_BGRA_8888_hb;
                case ColorInfo.CT_GRAY_8          -> PixelUtils::store_GRAY_8_hb;
                case ColorInfo.CT_GRAY_ALPHA_88   -> PixelUtils::store_GRAY_ALPHA_88_hb;
                case ColorInfo.CT_ALPHA_8         -> PixelUtils::store_ALPHA_8_hb;
                case ColorInfo.CT_R_16            -> PixelUtils::store_R_16_hb;
                case ColorInfo.CT_RG_1616         -> PixelUtils::store_RG_1616_hb;
                case ColorInfo.CT_RGB_161616      -> PixelUtils::store_RGB_161616_hb;
                case ColorInfo.CT_RGBA_16161616   -> PixelUtils::store_RGBA_16161616_hb;
                case ColorInfo.CT_GRAY_16         -> PixelUtils::store_GRAY_16_hb;
                case ColorInfo.CT_GRAY_ALPHA_1616 -> PixelUtils::store_GRAY_ALPHA_1616_hb;
                case ColorInfo.CT_ALPHA_16        -> PixelUtils::store_ALPHA_16_hb;
                case ColorInfo.CT_R_F16           -> PixelUtils::store_R_F16_hb;
                case ColorInfo.CT_RG_F16          -> PixelUtils::store_RG_F16_hb;
                case ColorInfo.CT_RGBA_F16        -> PixelUtils::store_RGBA_F16_hb;
                case ColorInfo.CT_ALPHA_F16       -> PixelUtils::store_ALPHA_F16_hb;
                case ColorInfo.CT_R_F32           -> PixelUtils::store_R_F32_hb;
                case ColorInfo.CT_RG_F32          -> PixelUtils::store_RG_F32_hb;
                case ColorInfo.CT_RGBA_F32        -> PixelUtils::store_RGBA_F32_hb;
                default -> throw new AssertionError(ct);
            };
        }
    }
    //@formatter:on

    // Do NOT change these flags
    public static final int
            kColorSpaceXformFlagUnpremul = 0x1,
            kColorSpaceXformFlagLinearize = 0x2,
            kColorSpaceXformFlagGamutTransform = 0x4,
            kColorSpaceXformFlagEncode = 0x8,
            kColorSpaceXformFlagPremul = 0x10;

    /**
     * Performs color type, alpha type, and color space conversion.
     * Addresses (offsets) must be aligned to bytes-per-pixel, scaling is not allowed.
     */
    public static boolean convertPixels(@NonNull Pixmap src, @NonNull Pixmap dst) {
        return convertPixels(src.getInfo(), src.getBase(), src.getAddress(), src.getRowBytes(),
                dst.getInfo(), dst.getBase(), dst.getAddress(), dst.getRowBytes(), false);
    }

    /**
     * Performs color type, alpha type, color space, and origin conversion.
     * Addresses (offsets) must be aligned to bytes-per-pixel (except for non-power-of-two),
     * scaling is not allowed.
     */
    public static boolean convertPixels(@NonNull Pixmap src, @NonNull Pixmap dst, boolean flipY) {
        return convertPixels(src.getInfo(), src.getBase(), src.getAddress(), src.getRowBytes(),
                dst.getInfo(), dst.getBase(), dst.getAddress(), dst.getRowBytes(), flipY);
    }

    /**
     * Performs color type, alpha type, and color space conversion.
     * Addresses (offsets) must be aligned to bytes-per-pixel, scaling is not allowed.
     */
    public static boolean convertPixels(@NonNull ImageInfo srcInfo, Object srcBase,
                                        long srcAddr, long srcRowBytes,
                                        @NonNull ImageInfo dstInfo, Object dstBase,
                                        long dstAddr, long dstRowBytes) {
        return convertPixels(srcInfo, srcBase, srcAddr, srcRowBytes,
                dstInfo, dstBase, dstAddr, dstRowBytes, false);
    }

    private static int checkAlignment(Object base, long addr, long rowBytes, @ColorInfo.ColorType int ct) {
        int bpp = ColorInfo.bytesPerPixel(ct);
        if (!ColorInfo.validMemoryAddress(ct, base, addr)) {
            return 0;
        }
        if (!ColorInfo.validMemoryAddress(ct, null, rowBytes)) {
            return 0;
        }
        return bpp;
    }

    /**
     * Performs color type, alpha type, color space, and origin conversion.
     * Addresses (offsets) must be aligned to bytes-per-pixel (except for non-power-of-two),
     * scaling is not allowed.
     */
    public static boolean convertPixels(@NonNull ImageInfo srcInfo, Object srcBase,
                                        long srcAddr, long srcRowBytes,
                                        @NonNull ImageInfo dstInfo, Object dstBase,
                                        long dstAddr, long dstRowBytes,
                                        boolean flipY) {
        if (!srcInfo.isValid() || !dstInfo.isValid()) {
            return false;
        }
        if (srcInfo.width() != dstInfo.width() ||
                srcInfo.height() != dstInfo.height()) {
            return false;
        }
        if ((srcBase == null && srcAddr == MemoryUtil.NULL) ||
                (dstBase == null && dstAddr == MemoryUtil.NULL)) {
            return false;
        }
        if (srcRowBytes < srcInfo.minRowBytes() ||
                dstRowBytes < dstInfo.minRowBytes()) {
            return false;
        }
        int srcBpp = checkAlignment(srcBase, srcAddr, srcRowBytes, srcInfo.colorType());
        int dstBpp = checkAlignment(dstBase, dstAddr, dstRowBytes, dstInfo.colorType());
        if (srcBpp == 0 || dstBpp == 0) {
            return false;
        }

        ColorSpace srcCS = srcInfo.colorSpace();
        @ColorInfo.ColorType var srcCT = srcInfo.colorType();
        @ColorInfo.AlphaType var srcAT = srcInfo.alphaType();
        ColorSpace dstCS = dstInfo.colorSpace();
        @ColorInfo.ColorType var dstCT = dstInfo.colorType();
        @ColorInfo.AlphaType var dstAT = dstInfo.alphaType();

        // Opaque outputs are treated as the same alpha type as the source input.
        if (dstAT == ColorInfo.AT_OPAQUE) {
            dstAT = srcAT;
        }

        if (srcCS == null) {
            srcCS = ColorSpace.get(ColorSpace.Named.SRGB);
        }
        if (dstCS == null) {
            dstCS = srcCS;
        }

        boolean csXform = !srcCS.equals(dstCS);

        int flags = 0;

        if (csXform || srcAT != dstAT) {
            if (srcAT == ColorInfo.AT_PREMUL) {
                flags |= kColorSpaceXformFlagUnpremul;
            }
            if (srcAT != ColorInfo.AT_OPAQUE && dstAT == ColorInfo.AT_PREMUL) {
                flags |= kColorSpaceXformFlagPremul;
            }
        }

        if (ColorInfo.colorTypeIsAlphaOnly(srcCT) &&
                ColorInfo.colorTypeIsAlphaOnly(dstCT)) {
            csXform = false;
            flags = 0;
        }

        int width = srcInfo.width();
        int height = srcInfo.height();

        // We can copy the pixels when no color type, alpha type, or color space changes.
        if (srcCT == dstCT && !csXform && flags == 0) {
            copyImage(srcBase, srcAddr, srcRowBytes,
                    dstBase, dstAddr, dstRowBytes,
                    srcInfo.minRowBytes(), height, flipY);
            return true;
        }

        if (flipY) {
            dstAddr += dstRowBytes * (height - 1);
            dstRowBytes = -dstRowBytes;
        }

        if (flags == 0 && !csXform &&
                ColorInfo.maxBitsPerChannel(srcCT) <= 8 &&
                ColorInfo.maxBitsPerChannel(dstCT) <= 8) {
            // low precision pipeline
            final PixelLoad load = load(srcCT, srcBase == null);
            final PixelStore store = store(dstCT, dstBase == null);

            for (int i = 0; i < height; i++) {
                long nextSrcAddr = srcAddr + srcRowBytes;
                long nextDstAddr = dstAddr + dstRowBytes;
                for (int j = 0; j < width; j++) {
                    store.store(dstBase, dstAddr, load.load(srcBase, srcAddr));
                    srcAddr += srcBpp;
                    dstAddr += dstBpp;
                }
                srcAddr = nextSrcAddr;
                dstAddr = nextDstAddr;
            }
        } else {
            // high precision pipeline
            final PixelOp load = loadOp(srcCT, srcBase == null);
            final boolean unpremul = (flags & kColorSpaceXformFlagUnpremul) != 0;
            final ColorSpace.Connector connector = csXform ? ColorSpace.connect(srcCS, dstCS) : null;
            final boolean premul = (flags & kColorSpaceXformFlagPremul) != 0;
            final PixelOp store = storeOp(dstCT, dstBase == null);

            float[] col = new float[4];
            for (int i = 0; i < height; i++) {
                long nextSrcAddr = srcAddr + srcRowBytes;
                long nextDstAddr = dstAddr + dstRowBytes;
                for (int j = 0; j < width; j++) {
                    load.op(srcBase, srcAddr, col);
                    if (unpremul) {
                        float scale = 1.0f / col[3];
                        if (!Float.isFinite(scale)) { // NaN or Inf
                            scale = 0;
                        }
                        col[0] *= scale;
                        col[1] *= scale;
                        col[2] *= scale;
                    }
                    if (connector != null) {
                        connector.transformUnclamped(col);
                    }
                    if (premul) {
                        float scale = col[3];
                        col[0] *= scale;
                        col[1] *= scale;
                        col[2] *= scale;
                    }
                    store.op(dstBase, dstAddr, col);
                    srcAddr += srcBpp;
                    dstAddr += dstBpp;
                }
                srcAddr = nextSrcAddr;
                dstAddr = nextDstAddr;
            }
        }

        return true;
    }
}

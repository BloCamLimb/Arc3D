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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable structure that pairs ImageInfo with pixels and row bytes.
 * <p>
 * This class does not try to manage the lifetime of pixels, unless it's backed
 * by a heap array, use {@link PixelRef} to manage the native pixel memory.
 */
public class Pixmap {

    @NonNull
    protected final ImageInfo mInfo;
    @Nullable
    protected final Object mBase;
    protected final long mAddress;
    protected final int mRowBytes;

    /**
     * Creates {@link Pixmap} from info width, height, AlphaType, ColorType and ColorSpace.
     * <var>rowBytes</var> should be info.width() times info.bytesPerPixel(), or larger.
     * <p>
     * No parameter checking is performed; it is up to the caller to ensure that
     * <var>address</var> and <var>rowBytes</var> agree with <var>info</var>.
     * <p>
     * The memory lifetime of pixels is managed by the caller.
     *
     * @param info     width, height, AlphaType, ColorType and ColorSpace
     * @param base     array if heap buffer; may be null
     * @param address  address if native buffer, or array offset; may be NULL
     * @param rowBytes size of one row of buffer; width times bpp, or larger
     */
    public Pixmap(@NonNull ImageInfo info,
                  @Nullable Object base,
                  @NativeType("const void *") long address,
                  int rowBytes) {
        assert ColorInfo.validMemoryAddress(info.colorType(),
                base, address);
        assert ColorInfo.validMemoryAddress(info.colorType(),
                null, rowBytes);
        mInfo = Objects.requireNonNull(info);
        mBase = base;
        mAddress = address;
        mRowBytes = rowBytes;
    }

    /**
     * Reinterprets an existing {@link Pixmap} with <var>newInfo</var>.
     */
    public Pixmap(@NonNull ImageInfo newInfo,
                  @NonNull Pixmap oldPixmap) {
        this(newInfo, oldPixmap.mBase, oldPixmap.mAddress, oldPixmap.mRowBytes);
    }

    /**
     * Returns width, height, AlphaType, ColorType, and ColorSpace.
     */
    @NonNull
    public ImageInfo getInfo() {
        return mInfo;
    }

    /**
     * Returns pixel count in each pixel row. Should be equal or less than:
     * rowBytes() / info().bytesPerPixel().
     *
     * @return pixel width in ImageInfo
     */
    public int getWidth() {
        return mInfo.width();
    }

    /**
     * Returns pixel row count.
     *
     * @return pixel height in ImageInfo
     */
    public int getHeight() {
        return mInfo.height();
    }

    @ColorInfo.ColorType
    public int getColorType() {
        return mInfo.colorType();
    }

    @ColorInfo.AlphaType
    public int getAlphaType() {
        return mInfo.alphaType();
    }

    /**
     * Returns ColorSpace, the range of colors, associated with ImageInfo.
     */
    @Nullable
    public ColorSpace getColorSpace() {
        return mInfo.colorSpace();
    }

    /**
     * The array if heap buffer; may be null.
     */
    @Nullable
    public Object getBase() {
        return mBase;
    }

    /**
     * The address if native buffer, the base address corresponding to the pixel origin,
     * or array offset; may be NULL.
     */
    public long getAddress() {
        return mAddress;
    }

    /**
     * The size, in bytes, between the start of one pixel row/scanline and the next in buffer,
     * including any unused padding between them. This value must be at least the width multiplied
     * by the bytes-per-pixel, where the bytes-per-pixel depends on the color type.
     * <p>
     * Returns zero if colorType() is unknown. It is up to the caller to ensure that row bytes is
     * a useful value.
     */
    public int getRowBytes() {
        return mRowBytes;
    }

    /**
     * Returns address/offset at (x, y). Returns NULL if {@link #getAddress()} is NULL
     * and {@link #getBase()} is null.
     * <p>
     * Input is not validated, and return value is undefined if ColorType is unknown.
     *
     * @param x column index, zero or greater, and less than width()
     * @param y row index, zero or greater, and less than height()
     * @return readable generic pointer/offset to pixel
     */
    public long getAddress(int x, int y) {
        assert x < getWidth();
        assert y < getHeight();
        long addr = mAddress;
        if (addr == MemoryUtil.NULL && mBase == null) {
            return MemoryUtil.NULL;
        }
        return addr + (long) y * mRowBytes +
                (long) x * mInfo.bytesPerPixel();
    }

    /**
     * Make a pixmap width, height, pixel address to intersection of this with subset,
     * if intersection is not empty; and return the new pixmap. Otherwise, return null.
     *
     * @param subset bounds to intersect
     * @return a pixmap if intersection of this and subset is not empty
     */
    @Nullable
    public Pixmap makeSubset(@NonNull Rect2ic subset) {
        var r = new Rect2i(0, 0, getWidth(), getHeight());
        if (!r.intersect(subset)) {
            return null;
        }

        assert (r.x() < getWidth());
        assert (r.y() < getHeight());

        return new Pixmap(
                getInfo().makeWH(r.width(), r.height()), getBase(),
                getAddress(r.x(), r.y()), getRowBytes()
        );
    }

    /**
     * Returns origin of pixels within Pixels pix. Pixmap bounds is always contained
     * by Pixels bounds, which may be the same size or larger. Multiple Pixmap
     * can share the same Pixels instance, where each Pixmap has different bounds.
     * <p>
     * The returned origin added to Pixmap dimensions equals or is smaller than the
     * Pixels dimensions.
     */
    public void getPixelOrigin(long pix,
                               @Size(2) int @NonNull [] origin) {
        long rb = getRowBytes();
        long off = getAddress() - pix;
        if (off < 0 || rb <= 0) {
            origin[0] = 0;
            origin[1] = 0;
        } else {
            origin[0] = (int) ((off % rb) / getInfo().bytesPerPixel());
            origin[1] = (int) (off / rb);
        }
    }

    /**
     * Gets the pixel value at (x, y), and converts it to {@link ColorInfo#CT_BGRA_8888_NATIVE},
     * {@link ColorInfo#AT_UNPREMUL}, and {@link ColorSpace.Named#SRGB}.
     * <p>
     * Input is not validated: out of bounds values of x or y trigger an assertion error;
     * and returns undefined values or may crash. Fails if color type is unknown or
     * pixel data is NULL.
     * <p>
     * If the max bits per channel for the color type is greater than 8, or colors are premultiplied,
     * then color precision may be lost in the conversion. Otherwise, precision will not be lost.
     * If the color space is not sRGB, then this method will perform color space transformation,
     * which can be slow.
     *
     * @param x column index, zero or greater, and less than width()
     * @param y row index, zero or greater, and less than height()
     * @return pixel converted to unpremultiplied color
     * @see #getColor4f(int, int, float[])
     */
    @ColorInt
    public int getColor(int x, int y) {
        assert getBase() != null || getAddress() != MemoryUtil.NULL;
        assert x < getWidth();
        assert y < getHeight();
        Object base = getBase();
        long addr = getAddress(x, y);
        var ct = getColorType();
        var at = getAlphaType();
        var cs = getColorSpace();
        if (at != ColorInfo.AT_PREMUL && (cs == null || cs.isSrgb())) {
            // no alpha type and color space conversion
            try {
                return PixelUtils.load(ct, base == null)
                        .load(base, addr);
            } catch (UnsupportedOperationException ignored) {
                // high precision fallback
            }
        }

        var srcInfo = new ImageInfo(1, 1, ct, at, cs);
        var dstInfo = new ImageInfo(1, 1, ColorInfo.CT_BGRA_8888_NATIVE,
                ColorInfo.AT_UNPREMUL, ColorSpace.get(ColorSpace.Named.SRGB));
        int[] col = new int[1];
        boolean res = PixelUtils.convertPixels(
                srcInfo, base, addr, getRowBytes(),
                dstInfo, col, 0, getRowBytes()
        );
        assert res;
        return col[0];
    }

    /**
     * Gets the pixel at (x, y), and converts it to {@link ColorInfo#CT_RGBA_F32}.
     * This method will not perform alpha type or color space transformation,
     * the resulting color has {@link #getAlphaType()} and is in {@link #getColorSpace()}.
     * <p>
     * Input is not validated: out of bounds values of x or y trigger an assertion error;
     * and returns undefined values or may crash. Fails if color type is unknown or
     * pixel data is NULL.
     *
     * @param x   column index, zero or greater, and less than width()
     * @param y   row index, zero or greater, and less than height()
     * @param dst pixel converted to float color
     */
    public void getColor4f(int x, int y, @Size(4) float @NonNull [] dst) {
        assert getBase() != null || getAddress() != MemoryUtil.NULL;
        assert x < getWidth();
        assert y < getHeight();
        Object base = getBase();
        PixelUtils.loadOp(getColorType(), base == null)
                .op(base, getAddress(x, y), dst);
    }

    /**
     * Sets the pixel at (x, y), from {@link ColorInfo#CT_RGBA_F32} to {@link #getColorType()}.
     * This method will not perform alpha type or color space transformation,
     * the given color should have {@link #getAlphaType()} and be in {@link #getColorSpace()}.
     * <p>
     * Input is not validated: out of bounds values of x or y trigger an assertion error;
     * and returns undefined values or may crash. Fails if color type is unknown or
     * pixel data is NULL.
     *
     * @param x   column index, zero or greater, and less than width()
     * @param y   row index, zero or greater, and less than height()
     * @param src float color to set
     */
    public void setColor4f(int x, int y, @Size(4) float @NonNull [] src) {
        assert getBase() != null || getAddress() != MemoryUtil.NULL;
        assert x < getWidth();
        assert y < getHeight();
        Object base = getBase();
        PixelUtils.storeOp(getColorType(), base == null)
                .op(base, getAddress(x, y), src);
    }

    /**
     * Copies a Rect of pixels to dst. Copy starts at (srcX, srcY), and does not
     * exceed Pixmap (width(), height()). dst specifies width, height, ColorType,
     * AlphaType, and ColorSpace of destination.  Returns true if pixels are copied.
     * Returns false if dst address equals nullptr, or dst.rowBytes() is less than
     * dst ImageInfo::minRowBytes.
     * <p>
     * Pixels are copied only if pixel conversion is possible. Returns
     * false if pixel conversion is not possible.
     * <p>
     * srcX and srcY may be negative to copy only top or left of source. Returns
     * false pixmap width() or height() is zero or negative. Returns false if:
     * srcX >= pixmap width(), or if srcY >= pixmap height().
     *
     * @param dst  ImageInfo and pixel address to write to
     * @param srcX column index whose absolute value is less than width()
     * @param srcY row index whose absolute value is less than height()
     * @return true if pixels are copied to dst
     */
    public boolean readPixels(@NonNull Pixmap dst, int srcX, int srcY) {
        ImageInfo dstInfo = dst.getInfo();
        if (srcX < 0 || srcY < 0 ||
                srcX + dstInfo.width() > getWidth() ||
                srcY + dstInfo.height() > getHeight()) {
            return false;
        }

        long srcAddr = getAddress(srcX, srcY);
        ImageInfo srcInfo = getInfo().makeWH(dstInfo.width(), dstInfo.height());
        return PixelUtils.convertPixels(
                srcInfo, getBase(), srcAddr, getRowBytes(),
                dstInfo, dst.getBase(), dst.getAddress(), dst.getRowBytes()
        );
    }

    /**
     * Copies a Rect of pixels from src. Copy starts at (dstX, dstY), and does not exceed
     * (src.width(), src.height()).
     * <p>
     * src specifies width, height, ColorType, AlphaType, ColorSpace, pixel storage,
     * and row bytes of source. src.rowBytes() specifics the gap from one source
     * row to the next. Returns true if pixels are copied. Returns false if:
     * - src pixel storage equals nullptr
     * - src.rowBytes is less than ImageInfo::minRowBytes()
     * <p>
     * Pixels are copied only if pixel conversion is possible. Returns
     * false if pixel conversion is not possible.
     * <p>
     * dstX and dstY may be negative to copy only top or left of source. Returns
     * false if width() or height() is zero or negative.
     * Returns false if dstX >= pixmap width(), or if dstY >= pixmap height().
     *
     * @param src  source Pixmap: ImageInfo, pixels, row bytes
     * @param dstX column index whose absolute value is less than width()
     * @param dstY row index whose absolute value is less than height()
     * @return true if src pixels are copied to pixmap
     */
    public boolean writePixels(@NonNull Pixmap src, int dstX, int dstY) {
        ImageInfo srcInfo = src.getInfo();
        if (dstX < 0 || dstY < 0 ||
                dstX + srcInfo.width() > getWidth() ||
                dstY + srcInfo.height() > getHeight()) {
            return false;
        }

        long dstAddr = getAddress(dstX, dstY);
        ImageInfo dstInfo = getInfo().makeWH(srcInfo.width(), srcInfo.height());
        return PixelUtils.convertPixels(
                srcInfo, src.getBase(), src.getAddress(), src.getRowBytes(),
                dstInfo, getBase(), dstAddr, getRowBytes()
        );
    }

    /**
     * Writes color to pixels bounded by subset; returns true on success.
     * if subset is null, writes colors pixels inside bounds(). Returns false if
     * {@link #getColorType()} is unknown, if subset is not null and does
     * not intersect bounds(), or if subset is null and bounds() is empty.
     * <p>
     * This method will not perform alpha type or color space transformation,
     * the given color should have {@link #getAlphaType()} and be in {@link #getColorSpace()}.
     *
     * @param color  float color to write
     * @param subset bounding box of pixels to write; may be null
     * @return true if pixels are changed
     */
    public boolean clear(@Size(4) float @NonNull [] color,
                         @Nullable Rect2ic subset) {
        var ct = getColorType();
        if (ct == ColorInfo.CT_UNKNOWN) {
            return false;
        }

        var clip = new Rect2i(0, 0, getWidth(), getHeight());
        if (subset != null && !clip.intersect(subset)) {
            return false;
        }

        Object base = getBase();
        int bpp = ColorInfo.bytesPerPixel(ct);

        // RGBA_F32 is the only type with a bpp of 16
        if (ct == ColorInfo.CT_RGBA_F32) {
            assert bpp == 16;
            if (Float.floatToRawIntBits(color[0]) == 0 &&
                    Float.floatToRawIntBits(color[1]) == 0 &&
                    Float.floatToRawIntBits(color[2]) == 0 &&
                    Float.floatToRawIntBits(color[3]) == 0) {
                // fill with zeros
                if (base != null) {
                    float[] hb = (float[]) base;
                    int elements = clip.width() * 4;
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        int index = (int) (getAddress(clip.x(), y)>>2);
                        Arrays.fill(hb, index, index + elements, 0);
                    }
                } else {
                    long rowBytes = (long) clip.width() * 16;
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        MemoryUtil.memSet(
                                getAddress(clip.x(), y), 0, rowBytes);
                    }
                }
            } else {
                if (base != null) {
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        for (int i = 0, e = clip.width(); i < e; ++i) {
                            PixelUtils.store_RGBA_F32_hb(base, addr, color);
                            addr += 16;
                        }
                    }
                } else {
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        for (int i = 0, e = clip.width(); i < e; ++i) {
                            PixelUtils.store_RGBA_F32_u(null, addr, color);
                            addr += 16;
                        }
                    }
                }
            }
            return true;
        }

        assert bpp >= 1 && bpp <= 8;

        try (var stack = MemoryStack.stackPush()) {
            // convert to ct
            long dst = stack.nmalloc(8, 8);
            PixelUtils.storeOp(ct, true)
                    .op(null, dst, color);

            boolean fast = true;
            byte v0 = MemoryUtil.memGetByte(dst);
            for (int i = 1; i < bpp; ++i) {
                byte v = MemoryUtil.memGetByte(dst + i);
                if (v != v0) {
                    fast = false;
                    break;
                }
            }
            if (fast) {
                // fill with value
                long rowBytes = (long) clip.width() * bpp;
                if (base == null) {
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        MemoryUtil.memSet(
                                getAddress(clip.x(), y), v0 & 0xff, rowBytes);
                    }
                } else if (base instanceof byte[] hb) {
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        Arrays.fill(hb, (int) addr, (int) (addr+rowBytes), v0);
                    }
                } else if (base instanceof short[] hb) {
                    short wideValue = (short) ((v0 & 0xFF) * 0x0101);
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        Arrays.fill(hb, (int) (addr>>1), (int) ((addr+rowBytes)>>1), wideValue);
                    }
                } else if (base instanceof int[] hb) {
                    int wideValue = (v0 & 0xFF) * 0x01010101;
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        Arrays.fill(hb, (int) (addr>>2), (int) ((addr+rowBytes)>>2), wideValue);
                    }
                } else if (base instanceof float[] hb) {
                    float wideValue = Float.intBitsToFloat((v0 & 0xFF) * 0x01010101);
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        Arrays.fill(hb, (int) (addr>>2), (int) ((addr+rowBytes)>>2), wideValue);
                    }
                } else {
                    assert false;
                }

                return true;
            }

            vectorized: {
                if (bpp == 2) {
                    short value = MemoryUtil.memGetShort(dst);
                    if (base == null) {
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            long addr = getAddress(clip.x(), y);
                            setPixel16(addr, value, clip.width());
                        }
                    } else if (base instanceof short[] hb) {
                        // packed or single channel
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            int index = (int) (getAddress(clip.x(), y) >> 1);
                            Arrays.fill(hb, index, index + clip.width(), value);
                        }
                    } else {
                        break vectorized;
                    }
                } else if (bpp == 4) {
                    int value = MemoryUtil.memGetInt(dst);
                    if (base == null) {
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            long addr = getAddress(clip.x(), y);
                            setPixel32(addr, value, clip.width());
                        }
                    } else if (base instanceof int[] hb) {
                        // packed or single channel
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            int index = (int) (getAddress(clip.x(), y) >> 2);
                            Arrays.fill(hb, index, index + clip.width(), value);
                        }
                    } else if (base instanceof float[] hb) {
                        // single channel
                        float fValue = Float.intBitsToFloat(value);
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            int index = (int) (getAddress(clip.x(), y) >> 2);
                            Arrays.fill(hb, index, index + clip.width(), fValue);
                        }
                    } else {
                        break vectorized;
                    }
                } else if (bpp == 8) {
                    long value = MemoryUtil.memGetLong(dst);
                    if (base == null) {
                        for (int y = clip.mTop; y < clip.mBottom; ++y) {
                            long addr = getAddress(clip.x(), y);
                            setPixel64(addr, value, clip.width());
                        }
                    } else {
                        break vectorized;
                    }
                } else if (ct == ColorInfo.CT_RGB_888) {
                    // RGB_888 is a type where bpp is not a power of 2
                    assert bpp == 3;
                    if (base != null) {
                        break vectorized;
                    }
                    byte v1 = MemoryUtil.memGetByte(dst + 1);
                    byte v2 = MemoryUtil.memGetByte(dst + 2);
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        for (int i = 0, e = clip.width(); i < e; ++i) {
                            MemoryUtil.memPutByte(addr, v0);
                            MemoryUtil.memPutByte(addr + 1, v1);
                            MemoryUtil.memPutByte(addr + 2, v2);
                            addr += 3;
                        }
                    }
                } else if (ct == ColorInfo.CT_RGB_161616) {
                    // RGB_161616 is a type where bpp is not a power of 2
                    assert bpp == 6;
                    if (base != null) {
                        break vectorized;
                    }
                    short vv = MemoryUtil.memGetShort(dst);
                    short v1 = MemoryUtil.memGetShort(dst + 2);
                    short v2 = MemoryUtil.memGetShort(dst + 4);
                    for (int y = clip.mTop; y < clip.mBottom; ++y) {
                        long addr = getAddress(clip.x(), y);
                        for (int i = 0, e = clip.width(); i < e; ++i) {
                            MemoryUtil.memPutShort(addr, vv);
                            MemoryUtil.memPutShort(addr + 2, v1);
                            MemoryUtil.memPutShort(addr + 4, v2);
                            addr += 6;
                        }
                    }
                } else {
                    assert false;
                }

                return true;
            }

            // heap slow path
            assert base != null;
            var store = PixelUtils.storeOp(ct, base == null);
            for (int y = clip.mTop; y < clip.mBottom; ++y) {
                long addr = getAddress(clip.x(), y);
                for (int i = 0, e = clip.width(); i < e; ++i) {
                    store.op(base, addr, color);
                    addr += bpp;
                }
            }

            return true;
        }
    }

    // vectorized memory fill

    private static void setPixel16(long addr,
                                   short value, int count) {
        assert count > 0;
        long wideValue = (long) value << 16 | value;
        wideValue |= wideValue << 32;
        assert MathUtil.isAlign2(addr);
        long pad = (-addr) & 7;
        while (pad > 0 && count != 0) {
            MemoryUtil.memPutShort(addr, value);
            addr += 2;
            count--;
            pad -= 2;
        }
        assert count == 0 || pad == 0;
        assert count == 0 || MathUtil.isAlign8(addr);
        while (count >= 4) {
            MemoryUtil.memPutLong(addr, wideValue);
            addr += 8;
            count -= 4;
        }
        while (count-- != 0) {
            MemoryUtil.memPutShort(addr, value);
            addr += 2;
        }
    }

    private static void setPixel32(long addr,
                                   int value, int count) {
        assert count > 0;
        long wideValue = (long) value << 32 | value;
        assert MathUtil.isAlign4(addr);
        if (!MathUtil.isAlign8(addr)) {
            MemoryUtil.memPutInt(addr, value);
            addr += 4;
            count--;
        }
        assert MathUtil.isAlign8(addr);
        while (count >= 2) {
            MemoryUtil.memPutLong(addr, wideValue);
            addr += 8;
            count -= 2;
        }
        if (count != 0) {
            assert count == 1;
            MemoryUtil.memPutInt(addr, value);
        }
    }

    private static void setPixel64(long addr,
                                   long value, int count) {
        assert MathUtil.isAlign8(addr);
        for (int i = 0; i < count; i++) {
            MemoryUtil.memPutLong(addr, value);
            addr += 8;
        }
    }

    @Override
    public String toString() {
        return "Pixmap{" +
                "mInfo=" + mInfo +
                ", mBase=" + mBase +
                ", mAddress=0x" + Long.toHexString(mAddress) +
                ", mRowBytes=" + mRowBytes +
                '}';
    }
}

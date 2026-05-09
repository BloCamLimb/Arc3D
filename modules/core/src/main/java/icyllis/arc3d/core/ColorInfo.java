/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2024 BloCamLimb <pocamelards@gmail.com>
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

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteOrder;

/**
 * Describes pixel dimensions and encoding.
 * <p>
 * ColorInfo is used to interpret a color: color type + alpha type + color space.
 */
public final class ColorInfo {

    //@formatter:off
    @ApiStatus.Internal
    @MagicConstant(intValues = {
            COMPRESSION_NONE,
            COMPRESSION_ETC2_RGB8_UNORM,
            COMPRESSION_BC1_RGB8_UNORM,
            COMPRESSION_BC1_RGBA8_UNORM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CompressionType {
    }

    /**
     * Compression types.
     * <table>
     *   <tr>
     *     <th>Core</th>
     *     <th>GL_COMPRESSED_*</th>
     *     <th>VK_FORMAT_*_BLOCK</th>
     *   </tr>
     *   <tr>
     *     <td>ETC2_RGB8_UNORM</td>
     *     <td>RGB8_ETC2</td>
     *     <td>ETC2_R8G8B8_UNORM</td>
     *   </tr>
     *   <tr>
     *     <td>BC1_RGB8_UNORM</td>
     *     <td>RGB_S3TC_DXT1_EXT</td>
     *     <td>BC1_RGB_UNORM</td>
     *   </tr>
     *   <tr>
     *     <td>BC1_RGBA8_UNORM</td>
     *     <td>RGBA_S3TC_DXT1_EXT</td>
     *     <td>BC1_RGBA_UNORM</td>
     *   </tr>
     * </table>
     */
    public static final int
            COMPRESSION_NONE            = 0,
            COMPRESSION_ETC2_RGB8_UNORM = 1,
            COMPRESSION_BC1_RGB8_UNORM  = 2,
            COMPRESSION_BC1_RGBA8_UNORM = 3,
            COMPRESSION_COUNT           = 4;

    /**
     * Describes how to interpret the alpha component of a pixel.
     */
    @ApiStatus.Internal
    @MagicConstant(intValues = {
            AT_UNKNOWN,
            AT_OPAQUE,
            AT_PREMUL,
            AT_UNPREMUL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AlphaType {
    }

    /**
     * Alpha types.
     * <p>
     * Describes how to interpret the alpha component of a pixel. A pixel may
     * be opaque, or alpha, describing multiple levels of transparency.
     * <p>
     * In simple blending, alpha weights the source color and the destination
     * color to create a new color. If alpha describes a weight from zero to one:
     * <p>
     * result color = source color * alpha + destination color * (1 - alpha)
     * <p>
     * In practice alpha is encoded in two or more bits, where 1.0 equals all bits set.
     * <p>
     * RGB may have alpha included in each component value; the stored
     * value is the original RGB multiplied by alpha. Premultiplied color
     * components improve performance, but it will reduce the image quality.
     * The usual practice is to premultiply alpha in the GPU, since they were
     * converted into floating-point values.
     */
    public static final int
            AT_UNKNOWN  = 0, // uninitialized
            AT_OPAQUE   = 1, // pixel is opaque
            AT_PREMUL   = 2, // pixel components are premultiplied by alpha
            AT_UNPREMUL = 3; // pixel components are unassociated with alpha

    static {
        // the following color types assume little-endian machines,
        // and LWJGL does not support big-endian machines as well
        if ((ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN)) {
            throw new RuntimeException("Color encoding requires little-endian");
        }
    }

    /**
     * Describes how pixel bits encode color.
     */
    @ApiStatus.Internal
    @MagicConstant(intValues = {
            CT_UNKNOWN,
            CT_R_8,
            CT_RG_88,
            CT_RGBA_8888,
            CT_R_16,
            CT_RG_1616,
            CT_RGBA_16161616,
            CT_R_F16,
            CT_RG_F16,
            CT_RGBA_F16,
            CT_R_F32,
            CT_RG_F32,
            CT_RGBA_F32,
            CT_ALPHA_8,
            CT_ALPHA_16,
            CT_ALPHA_F16,
            CT_GRAY_8,
            CT_GRAY_16,
            CT_GRAY_ALPHA_88,
            CT_GRAY_ALPHA_1616,
            CT_BGRA_8888,
            CT_BGR_565,
            CT_BGRA_5551,
            CT_RGBA_1010102,
            CT_BGRA_1010102,
            CT_RGBE_9995,
            CT_RGB_888,
            CT_RGB_161616,
            CT_RGBX_8888,
            CT_R5G6B5_UNORM,
            CT_R8G8_UNORM,
            CT_A16_UNORM,
            CT_A16_FLOAT,
            CT_A16G16_UNORM,
            CT_R16G16_FLOAT,
            CT_R16G16B16A16_UNORM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ColorType {
    }

    /**
     * Color types.
     * <p>
     * Describes a layout of pixel data in CPU or GPU memory. A pixel may be an alpha mask, a grayscale,
     * RGB, or RGBA. It specifies the channels, their type, and width. It does not refer to a texture
     * format and the mapping to texture formats may be many-to-many. It does not specify the sRGB
     * encoding of the stored values.
     * <p>
     * Color types are divided into two classes: array and packed.<br>
     * For array types, the components are listed in order of where they appear in memory. For example,
     * {@link #CT_RGBA_8888} means that the pixel memory should be interpreted as an array of uint8 values,
     * and the R channel appears at the first uint8 value. This is the same naming convention as Vulkan.<br>
     * For packed types, the first component appear in the least-significant bits. For example,
     * {@link #CT_BGR_565} means that each pixel is packed as {@code (b << 0) | (g << 5) | (r << 11)},
     * an uint16 value. This is in the reverse order of Vulkan's naming convention.
     * <p>
     * Note that if bytes-per-pixel of a color type is 1, 2, 4, or 8, then Arc3D requires pixel memory
     * to be aligned to bytes-per-pixel, otherwise it should be aligned to the size of data type as normal.
     */
    public static final int
            CT_UNKNOWN          = 0; // uninitialized

    // Single channel data (8 bits) interpreted as red.
    // G and B are forced to 0, alpha is forced to opaque.
    public static final int
            CT_R_8              = 1;
    // Two channel RG data (8 bits per channel).
    // Blue is forced to 0, alpha is forced to opaque.
    // This can be interpreted as two R,G uint8_t or a LE 16-bit word.
    //  Bits: [G:15..8 R:7..0]
    public static final int
            CT_RG_88            = 2;
    // Four channel RGBA data (8 bits per channel).
    // This can be interpreted as four R,G,B,A uint8_t or a LE 32-bit word.
    //  Bits: [A:31..24 B:23..16 G:15..8 R:7..0]
    public static final int
            CT_RGBA_8888        = 3;

    // Single channel data (16 bits) interpreted as red.
    // G and B are forced to 0, alpha is forced to opaque.
    public static final int
            CT_R_16             = 4;
    // Two channel RG data (16 bits per channel).
    // Blue is forced to 0, alpha is forced to opaque.
    // This can be interpreted as two R,G uint16_t or a LE 32-bit word.
    //  Bits: [G:31..16 R:15..0]
    public static final int
            CT_RG_1616          = 5;
    // Four channel RGBA data (16 bits per channel).
    // This can be interpreted as four R,G,B,A uint16_t or a LE 64-bit word.
    //  Bits: [A:63..48 B:47..32 G:31..16 R:15..0]
    public static final int
            CT_RGBA_16161616    = 6;

    // Single channel data (16-bit half-float) interpreted as red.
    // G and B are forced to 0, alpha is forced to opaque.
    public static final int
            CT_R_F16            = 7;
    // Two channel RG data (16-bit half-float per channel).
    // Blue is forced to 0, alpha is forced to opaque.
    // This can be interpreted as two R,G float16_t or a LE 32-bit word.
    //  Bits: [G:31..16 R:15..0]
    public static final int
            CT_RG_F16           = 8;
    // Four channel RGBA data (16-bit half-float per channel).
    // This can be interpreted as four R,G,B,A float16_t or a LE 64-bit word.
    //  Bits: [A:63..48 B:47..32 G:31..16 R:15..0]
    public static final int
            CT_RGBA_F16         = 9;

    // Single channel data (32-bit float) interpreted as red.
    // G and B are forced to 0, alpha is forced to opaque.
    public static final int
            CT_R_F32            = 10;
    // Two channel RG data (32-bit float per channel).
    // Blue is forced to 0, alpha is forced to opaque.
    // This can be interpreted as two R,G float32_t or a LE 64-bit word.
    //  Bits: [G:63..32 R:31..0]
    public static final int
            CT_RG_F32           = 11;
    // Four channel RGBA data (32-bit float per channel).
    // This can be interpreted as four R,G,B,A float32_t or a LE 128-bit word.
    //  Bits: [A:127..96 B:95..64 G:63..32 R:31..0]
    public static final int
            CT_RGBA_F32         = 12;

    // Single channel data (8 bits) interpreted as an alpha value. RGB are 0.
    public static final int
            CT_ALPHA_8          = 13;
    // Single channel data (16 bits) interpreted as an alpha value. RGB are 0.
    public static final int
            CT_ALPHA_16         = 14;
    // Single channel data (16-bit half-float) interpreted as an alpha value. RGB are 0.
    public static final int
            CT_ALPHA_F16        = 15;

    // Single channel data (8 bits) interpreted as a grayscale value (e.g. replicated to RGB).
    public static final int
            CT_GRAY_8           = 16;
    // Single channel data (16 bits) interpreted as a grayscale value (e.g. replicated to RGB).
    public static final int
            CT_GRAY_16          = 17;
    @ApiStatus.Internal
    public static final int
            CT_GRAY_ALPHA_88    = 18;
    @ApiStatus.Internal
    public static final int
            CT_GRAY_ALPHA_1616  = 19;

    // Four channel BGRA data (8 bits per channel).
    // This can be interpreted as four B,G,R,A uint8_t or a LE 32-bit word.
    //   Bits: [A:31..24 R:23..16 G:15..8 B:7..0]
    public static final int
            CT_BGRA_8888        = 20;

    // Three channel BGR data (5 bits red, 6 bits green, 5 bits blue)
    // This must be interpreted as a LE 16-bit word.
    //  Bits: [R:15..11 G:10..5 B:4..0]
    public static final int
            CT_BGR_565          = 21;
    // Three channel BGRA data (5 bits per color, 1 bit for alpha)
    // This must be interpreted as a LE 16-bit word.
    //  Bits: [A:15 R:14..10 G:9..5 B:4..0]
    public static final int
            CT_BGRA_5551        = 22;
    // Four channel RGBA data (10 bits per color, 2 bits for alpha).
    // This must be interpreted as a LE 32-bit word.
    //  Bits: [A:31..30 B:29..20 G:19..10 R:9..0]
    public static final int
            CT_RGBA_1010102     = 23;
    // Four channel BGRA data (10 bits per color, 2 bits for alpha).
    // This must be interpreted as a LE 32-bit word.
    //  Bits: [A:31..30 R:29..20 G:19..10 B:9..0]
    public static final int
            CT_BGRA_1010102     = 24;
    // Three channel RGB data (9 bits per color, 5 bits for shared exponent)
    // This must be interpreted as a LE 32-bit word.
    //  Bits: [E:31..27 B:26..18 G:17..9 R:8..0]
    public static final int
            CT_RGBE_9995        = 25; // TODO not supported on CPU yet

    // Three channel RGB data (8 bits per channel).
    // This must be interpreted as three R,G,B uint8_t, no alignment requirement.
    @ApiStatus.Internal
    public static final int
            CT_RGB_888          = 26;
    // Three channel RGB data (16 bits per channel).
    // This must be interpreted as three R,G,B uint16_t, no alignment requirement.
    @ApiStatus.Internal
    public static final int
            CT_RGB_161616       = 27;

    // Three channel RGB data (8 bits per channel).
    // The remaining 8 bits are ignored and alpha is forced to opaque when read.
    // This can be interpreted as four R,G,B,X uint8_t or a LE 32-bit word.
    //  Bits: [X:31..24 B:23..16 G:15..8 R:7..0]
    public static final int
            CT_RGBX_8888        = 28;

    /**
     * An alias based on host endianness, packed as
     * {@code (r << 0) | (g << 8) | (b << 16) | (a << 24)} an uint32 value.
     * <p>
     * This is not a standalone packed format, it just depends on CPU:
     * on little-endian machine this is {@link #CT_RGBA_8888}.
     */
    @ColorType
    public static final int
            CT_RGBA_8888_NATIVE = CT_RGBA_8888;
    /**
     * An alias based on host endianness, packed as
     * {@code (b << 0) | (g << 8) | (r << 16) | (a << 24)} an uint32 value.
     * <p>
     * This is not a standalone packed format, it just depends on CPU:
     * on little-endian machine this is {@link #CT_BGRA_8888}.
     */
    @ColorType
    public static final int
            CT_BGRA_8888_NATIVE = CT_BGRA_8888;
    /**
     * Aliases.
     */
    @ColorType
    public static final int
            CT_R5G6B5_UNORM       = CT_BGR_565,
            CT_R8G8_UNORM         = CT_RG_88,
            CT_A16_UNORM          = CT_ALPHA_16,
            CT_A16_FLOAT          = CT_ALPHA_F16,
            CT_A16G16_UNORM       = CT_RG_1616,
            CT_R16G16_FLOAT       = CT_RG_F16,
            CT_R16G16B16A16_UNORM = CT_RGBA_16161616;
    @ApiStatus.Internal
    public static final int
            CT_COUNT        = 29;
    //@formatter:on

    /**
     * Returns the number of color types but avoids inlining at compile-time.
     */
    public static int colorTypeCount() {
        return CT_COUNT;
    }

    /**
     * Returns the number of bytes required to store a pixel.
     *
     * @return bytes per pixel
     */
    public static int bytesPerPixel(@ColorType int ct) {
        return switch (ct) {
            case CT_UNKNOWN -> 0;
            case CT_R_8,
                 CT_ALPHA_8,
                 CT_GRAY_8 -> 1;
            case CT_BGR_565,
                 CT_BGRA_5551,
                 CT_RG_88,
                 CT_GRAY_ALPHA_88,
                 CT_R_16,
                 CT_R_F16,
                 CT_ALPHA_16,
                 CT_ALPHA_F16,
                 CT_GRAY_16 -> 2;
            case CT_RGB_888 -> 3;
            case CT_RGBA_8888,
                 CT_BGRA_8888,
                 CT_RGBX_8888,
                 CT_BGRA_1010102,
                 CT_RGBA_1010102,
                 CT_RGBE_9995,
                 CT_RG_1616,
                 CT_RG_F16,
                 CT_GRAY_ALPHA_1616,
                 CT_R_F32 -> 4;
            case CT_RGB_161616 -> 6;
            case CT_RGBA_16161616,
                 CT_RGBA_F16,
                 CT_RG_F32 -> 8;
            case CT_RGBA_F32 -> 16;
            default -> throw new AssertionError(ct);
        };
    }

    public static int maxBitsPerChannel(@ColorType int ct) {
        return switch (ct) {
            case CT_UNKNOWN -> 0;
            case CT_BGRA_5551 -> 5;
            case CT_BGR_565 -> 6;
            case CT_R_8,
                 CT_ALPHA_8,
                 CT_GRAY_8,
                 CT_RG_88,
                 CT_GRAY_ALPHA_88,
                 CT_RGB_888,
                 CT_RGBA_8888,
                 CT_BGRA_8888,
                 CT_RGBX_8888 -> 8;
            case CT_RGBE_9995 -> 9;
            case CT_RGBA_1010102,
                 CT_BGRA_1010102 -> 10;
            case CT_R_16,
                 CT_R_F16,
                 CT_ALPHA_16,
                 CT_ALPHA_F16,
                 CT_GRAY_16,
                 CT_RG_1616,
                 CT_RG_F16,
                 CT_GRAY_ALPHA_1616,
                 CT_RGB_161616,
                 CT_RGBA_16161616,
                 CT_RGBA_F16 -> 16;
            case CT_R_F32,
                 CT_RG_F32,
                 CT_RGBA_F32 -> 32;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Returns a valid AlphaType for <var>ct</var>. If there is more than one valid
     * AlphaType, returns <var>at</var>, if valid.
     *
     * @return a valid AlphaType
     * @throws IllegalArgumentException <var>at</var> is unknown, <var>ct</var> is not
     *                                  unknown, and <var>ct</var> has alpha channel.
     */
    @AlphaType
    public static int validateAlphaType(@ColorType int ct, @AlphaType int at) {
        switch (ct) {
            case CT_UNKNOWN:
                at = AT_UNKNOWN;
                break;
            case CT_ALPHA_8:
            case CT_ALPHA_16:
            case CT_ALPHA_F16:
                if (at == AT_UNPREMUL) {
                    at = AT_PREMUL;
                }
                // fallthrough
            case CT_GRAY_ALPHA_88:
            case CT_GRAY_ALPHA_1616:
            case CT_BGRA_5551:
            case CT_RGBA_8888:
            case CT_BGRA_8888:
            case CT_RGBA_1010102:
            case CT_BGRA_1010102:
            case CT_RGBA_F16:
            case CT_RGBA_F32:
            case CT_RGBA_16161616:
                if (at != AT_OPAQUE && at != AT_PREMUL && at != AT_UNPREMUL) {
                    throw new IllegalArgumentException("at is unknown");
                }
                break;
            case CT_GRAY_8:
            case CT_GRAY_16:
            case CT_R_8:
            case CT_RG_88:
            case CT_BGR_565:
            case CT_RGB_888:
            case CT_RGBX_8888:
            case CT_R_16:
            case CT_R_F16:
            case CT_RG_1616:
            case CT_RG_F16:
            case CT_RGB_161616:
            case CT_RGBE_9995:
            case CT_R_F32:
            case CT_RG_F32:
                at = AT_OPAQUE;
                break;
            default:
                throw new AssertionError(ct);
        }
        return at;
    }

    /**
     * Check the array type is valid for ct, and address is aligned for ct, for pixel operations.
     */
    public static boolean validMemoryAddress(@ColorType int ct, @Nullable Object base, long address) {
        if (base != null) {
            // heap array is limited
            if (address < 0 || address > Integer.MAX_VALUE) {
                return false;
            }
        }
        return switch (ct) {
            case CT_UNKNOWN -> true;
            case CT_R_8,
                 CT_ALPHA_8,
                 CT_GRAY_8,
                 CT_RGB_888 -> {
                if (base != null &&
                        !(base instanceof byte[])) {
                    yield false;
                }
                yield true;
            }
            case CT_RG_88,
                 CT_GRAY_ALPHA_88 -> {
                if (base != null &&
                        !(base instanceof byte[])) {
                    yield false;
                }
                yield MathUtil.isAlign2(address);
            }
            case CT_BGR_565,
                 CT_BGRA_5551,
                 CT_R_16,
                 CT_R_F16,
                 CT_ALPHA_16,
                 CT_ALPHA_F16,
                 CT_GRAY_16,
                 CT_RGB_161616,
                 CT_RG_F16,
                 CT_RGBA_F16 -> {
                if (base != null &&
                        !(base instanceof short[])) {
                    yield false;
                }
                yield MathUtil.isAlign2(address);
            }
            case CT_RG_1616,
                 CT_GRAY_ALPHA_1616 -> {
                if (base != null &&
                        !(base instanceof short[])) {
                    yield false;
                }
                yield MathUtil.isAlign4(address);
            }
            case CT_RGBA_8888,
                 CT_BGRA_8888,
                 CT_RGBX_8888 -> {
                if (base != null &&
                        !(base instanceof byte[]) &&
                        !(base instanceof int[])) { // assume little-endian host, it can be packed
                    yield false;
                }
                yield MathUtil.isAlign4(address);
            }
            case CT_BGRA_1010102,
                 CT_RGBA_1010102,
                 CT_RGBE_9995 -> {
                if (base != null &&
                        !(base instanceof int[])) {
                    yield false;
                }
                yield MathUtil.isAlign4(address);
            }
            case CT_R_F32,
                 CT_RG_F32,
                 CT_RGBA_F32 -> {
                if (base != null &&
                        !(base instanceof float[])) {
                    yield false;
                }
                yield MathUtil.isAlign4(address);
            }
            case CT_RGBA_16161616 -> {
                if (base != null &&
                        !(base instanceof short[])) {
                    yield false;
                }
                yield MathUtil.isAlign8(address);
            }
            default -> throw new AssertionError(ct);
        };
    }

    @ApiStatus.Internal
    public static int colorTypeChannelFlags(@ColorType int ct) {
        return switch (ct) {
            case CT_UNKNOWN -> 0;
            case CT_ALPHA_8,
                 CT_ALPHA_16,
                 CT_ALPHA_F16 -> Color.COLOR_CHANNEL_FLAG_ALPHA;
            case CT_BGR_565,
                 CT_RGB_888,
                 CT_RGB_161616,
                 CT_RGBX_8888,
                 CT_RGBE_9995 -> Color.COLOR_CHANNEL_FLAGS_RGB;
            case CT_BGRA_5551,
                 CT_RGBA_16161616,
                 CT_RGBA_F32,
                 CT_RGBA_F16,
                 CT_BGRA_1010102,
                 CT_RGBA_1010102,
                 CT_RGBA_8888,
                 CT_BGRA_8888 -> Color.COLOR_CHANNEL_FLAGS_RGBA;
            case CT_RG_88,
                 CT_RG_1616,
                 CT_RG_F16,
                 CT_RG_F32 -> Color.COLOR_CHANNEL_FLAGS_RG;
            case CT_GRAY_8,
                 CT_GRAY_16 -> Color.COLOR_CHANNEL_FLAG_GRAY;
            case CT_R_8,
                 CT_R_16,
                 CT_R_F16,
                 CT_R_F32 -> Color.COLOR_CHANNEL_FLAG_RED;
            case CT_GRAY_ALPHA_88,
                 CT_GRAY_ALPHA_1616 -> Color.COLOR_CHANNEL_FLAG_GRAY | Color.COLOR_CHANNEL_FLAG_ALPHA;
            default -> throw new AssertionError(ct);
        };
    }

    public static boolean colorTypeIsAlphaOnly(@ColorType int ct) {
        return colorTypeChannelFlags(ct) == Color.COLOR_CHANNEL_FLAG_ALPHA;
    }

    //@formatter:off
    public static String colorTypeToString(@ColorType int ct) {
        return switch (ct) {
            case CT_UNKNOWN             -> "UNKNOWN";
            case CT_R_8                 -> "R_8";
            case CT_ALPHA_8             -> "ALPHA_8";
            case CT_GRAY_8              -> "GRAY_8";
            case CT_GRAY_16             -> "GRAY_16";
            case CT_BGR_565             -> "BGR_565";
            case CT_BGRA_5551           -> "BGRA_5551";
            case CT_RG_88               -> "RG_88";
            case CT_R_16                -> "R_16";
            case CT_R_F16               -> "R_F16";
            case CT_R_F32               -> "R_F32";
            case CT_ALPHA_16            -> "ALPHA_16";
            case CT_ALPHA_F16           -> "ALPHA_F16";
            case CT_GRAY_ALPHA_88       -> "GRAY_ALPHA_88";
            case CT_GRAY_ALPHA_1616     -> "GRAY_ALPHA_1616";
            case CT_RGBE_9995           -> "RGBE_9995";
            case CT_RGB_888             -> "RGB_888";
            case CT_RGBX_8888           -> "RGBX_8888";
            case CT_RGBA_8888           -> "RGBA_8888";
            case CT_BGRA_8888           -> "BGRA_8888";
            case CT_BGRA_1010102        -> "BGRA_1010102";
            case CT_RGBA_1010102        -> "RGBA_1010102";
            case CT_RGB_161616          -> "RGB_161616";
            case CT_RG_1616             -> "RG_1616";
            case CT_RG_F16              -> "RG_F16";
            case CT_RG_F32              -> "RG_F32";
            case CT_RGBA_16161616       -> "RGBA_16161616";
            case CT_RGBA_F16            -> "RGBA_F16";
            case CT_RGBA_F32            -> "RGBA_F32";
            default -> throw new AssertionError(ct);
        };
    }
    //@formatter:on

    private ColorInfo() {
    }
}

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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.Color;
import icyllis.arc3d.core.ColorInfo;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * Shared constants, enums and utilities for Arc3D Engine.
 */
public interface Engine {

    /**
     * Block engine-private values.
     */
    //TODO delete this
    @ColorInfo.ColorType
    static int colorTypeToPublic(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                    ColorInfo.CT_ALPHA_8,
                    ColorInfo.CT_BGR_565,
                    ColorInfo.CT_RGBA_8888,
                    ColorInfo.CT_RGBX_8888,
                    ColorInfo.CT_RG_88,
                    ColorInfo.CT_BGRA_8888,
                    ColorInfo.CT_RGBA_1010102,
                    ColorInfo.CT_BGRA_1010102,
                    ColorInfo.CT_GRAY_8,
                    ColorInfo.CT_ALPHA_F16,
                    ColorInfo.CT_RGBA_F16,
                    ColorInfo.CT_RGBA_F32,
                    ColorInfo.CT_ALPHA_16,
                    ColorInfo.CT_RG_1616,
                    ColorInfo.CT_RG_F16,
                    ColorInfo.CT_RGBA_16161616,
                    ColorInfo.CT_R_8 -> ct;
            case ColorInfo.CT_RGB_888,
                    ColorInfo.CT_R_16,
                    ColorInfo.CT_R_F16,
                    ColorInfo.CT_GRAY_ALPHA_88 -> ColorInfo.CT_UNKNOWN;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Possible 3D APIs that may be used by Arc3D Engine.
     */
    interface BackendApi {
        /**
         * OpenGL 3.3 to 4.6 core profile (desktop);
         * OpenGL ES 3.0 to 3.2 (mobile)
         */
        int kOpenGL = 0;
        /**
         * Vulkan 1.1 to 1.3 (desktop and mobile)
         */
        int kVulkan = 1;
        /**
         * Mock draws nothing. It is used for unit tests and to measure CPU overhead.
         */
        int kMock = 2;

        static String toString(int value) {
            return switch (value) {
                case kOpenGL -> "OpenGL";
                case kVulkan -> "Vulkan";
                case kMock -> "Mock";
                default -> String.valueOf(value);
            };
        }
    }

    /**
     * Image and Surfaces can be stored such that (0, 0) in texture space may correspond to
     * either the upper-left or lower-left content pixel.
     */
    interface SurfaceOrigin {

        int kUpperLeft = 0; // top left, Vulkan
        int kLowerLeft = 1; // bottom left, OpenGL
    }

    /**
     * A Context's cache of backend context state can be partially invalidated.
     * These enums are specific to the GL backend.
     *
     * @see ImmediateContext#resetContext(int)
     */
    interface GLBackendState {

        int kRenderTarget = 1;
        int kPixelStore = 1 << 1;
        /**
         * Shader stages, vertex array and input buffers
         */
        int kPipeline = 1 << 2;
        /**
         * Also includes samplers bound to texture units
         */
        int kTexture = 1 << 3;
        int kStencil = 1 << 4;
        /**
         * Antialiasing and conservative raster
         */
        int kRaster = 1 << 5;
        int kBlend = 1 << 6;
        /**
         * View state stands for scissor and viewport
         */
        int kView = 1 << 7;
        int kMisc = 1 << 8;
    }

    /**
     * Known image view formats between backends. Actual support depends on {@link Caps}.
     */
    interface ImageFormat {
        int kUnsupported = 0;

        int
                kR8 = 1,
                kR16 = 2,
                kR16F = 3,
                kR32F = 4,
                kRG8 = 5,
                kRG16 = 6,
                kRG16F = 7,
                kRG32F = 8,
                kRGB8 = 9,
                kRGB16 = 10,
                kRGBA8 = 11,
                kRGBA16 = 12,
                kRGBA16F = 13,
                kRGBA32F = 14,
                kRGBA8_sRGB = 15,
                kBGRA8 = 16,
                kBGRA8_sRGB = 17,
                kB5_G6_R5 = 18,
                kBGR5_A1 = 19,
                kRGB10_A2 = 20,
                kBGR10_A2 = 21,
                kR11F_G11F_B10F = 22,
                kRGB9_E5 = 23;

        int kLastPacked = kRGB9_E5;

        int
                kRGB8_ETC2 = kLastPacked + 1,
                kRGB8_BC1 = kLastPacked + 2,
                kRGBA8_BC1 = kLastPacked + 3;

        int kLastCompressed = kRGBA8_BC1;

        int
                kYUV8_P2_420 = kLastCompressed + 1,
                kYUV8_P3_420 = kLastCompressed + 2;

        int kLastColor = kYUV8_P3_420;

        int
                kS8 = kLastColor + 1,
                kD16 = kLastColor + 2,
                kD24 = kLastColor + 3, // X8
                kD32F = kLastColor + 4,
                kD24_S8 = kLastColor + 5,
                kD32F_S8 = kLastColor + 6;

        int kLast = kD32F_S8;
        int kCount = kLast + 1;

        /**
         * @see Color#COLOR_CHANNEL_FLAGS_RGBA
         */
        static int channelFlags(int format) {
            switch (format) {

                case kR8:
                case kR16:
                case kR16F:
                case kR32F:           return Color.COLOR_CHANNEL_FLAG_RED;

                case kRG8:
                case kRG16:
                case kRG16F:
                case kRG32F:          return Color.COLOR_CHANNEL_FLAGS_RG;

                case kRGB8:
                case kRGB16:
                case kB5_G6_R5:
                case kR11F_G11F_B10F:
                case kRGB9_E5:
                case kRGB8_ETC2:
                case kRGB8_BC1:
                case kYUV8_P2_420:
                case kYUV8_P3_420:    return Color.COLOR_CHANNEL_FLAGS_RGB;

                case kRGBA8:
                case kRGBA16:
                case kRGBA16F:
                case kRGBA32F:
                case kRGBA8_sRGB:
                case kBGRA8:
                case kBGRA8_sRGB:
                case kBGR5_A1:
                case kRGB10_A2:
                case kBGR10_A2:
                case kRGBA8_BC1:       return Color.COLOR_CHANNEL_FLAGS_RGBA;

                case kS8:
                case kD16:
                case kD24:
                case kD32F:
                case kD24_S8:
                case kD32F_S8:
                case kUnsupported:    return 0;

                default: throw new AssertionError(format);
            }
        }

        /**
         * @see ColorInfo#COMPRESSION_NONE
         */
        @ColorInfo.CompressionType
        static int compressionType(int format) {
            return switch (format) {
                case kRGB8_ETC2 -> ColorInfo.COMPRESSION_ETC2_RGB8_UNORM;
                case kRGB8_BC1 -> ColorInfo.COMPRESSION_BC1_RGB8_UNORM;
                case kRGBA8_BC1 -> ColorInfo.COMPRESSION_BC1_RGBA8_UNORM;
                default -> ColorInfo.COMPRESSION_NONE;
            };
        }

        static boolean isSRGB(int format) {
            return switch (format) {
                case kRGBA8_sRGB,
                     kBGRA8_sRGB -> true;
                default -> false;
            };
        }

        /**
         * Currently we are just over estimating this value to be used in gpu size calculations even
         * though the actually size is probably less. We should instead treat planar formats similar
         * to compressed textures that go through their own special query for calculating size.
         */
        static int bytesPerBlock(int format) {
            switch (format) {
                case kUnsupported:    return 0;
                case kR8:             return 1;
                case kR16:            return 2;
                case kR16F:           return 2;
                case kR32F:           return 4;
                case kRG8:            return 2;
                case kRG16:           return 4;
                case kRG16F:          return 4;
                case kRG32F:          return 8;
                case kRGB8:           return 3;
                case kRGB16:          return 6;
                case kRGBA8:          return 4;
                case kRGBA16:         return 8;
                case kRGBA16F:        return 8;
                case kRGBA32F:        return 16;
                case kRGBA8_sRGB:     return 4;
                case kBGRA8:          return 4;
                case kBGRA8_sRGB:     return 4;
                case kB5_G6_R5:       return 2;
                case kBGR5_A1:        return 2;
                case kRGB10_A2:       return 4;
                case kBGR10_A2:       return 4;
                case kR11F_G11F_B10F: return 4;
                case kRGB9_E5:        return 4;
                case kS8:             return 1;
                case kD16:            return 2;
                case kD24:            return 4;
                case kD32F:           return 4;
                case kD24_S8:         return 4;
                case kD32F_S8:        return 8; // We assume the GPU stores this format 8 byte aligned
                // NOTE: For compressed formats, the block size refers to an actual compressed block of
                // multiple texels, whereas with other formats the block size represents a single pixel.
                case kRGB8_ETC2:
                case kRGB8_BC1:
                case kRGBA8_BC1:
                    return 8;
                //TODO We are just over estimating this value to be used in gpu size
                // calculations even though the actually size is probably less. We should instead treat
                // planar formats similar to compressed textures that go through their own special query for
                // calculating size.
                case kYUV8_P2_420:
                case kYUV8_P3_420:
                    return 3;
                default:
                    throw new AssertionError(format);
            }
        }

        static int depthBits(int format) {
            return switch (format) {
                case kD16 -> 16;
                case kD24,
                     kD24_S8 -> 24;
                case kD32F,
                     kD32F_S8 -> 32;
                default -> 0;
            };
        }

        static int stencilBits(int format) {
            return switch (format) {
                case kS8,
                     kD24_S8,
                     kD32F_S8 -> 8;
                default -> 0;
            };
        }

        static boolean isPackedDepthStencil(int format) {
            return format == kD24_S8 || format == kD32F_S8;
        }

        static boolean isDepthOrStencil(int format) {
            return switch (format) {
                case kS8,
                     kD16,
                     kD24,
                     kD32F,
                     kD24_S8,
                     kD32F_S8 -> true;
                default -> false;
            };
        }

        static boolean isMultiplanar(int format) {
            return format == kYUV8_P2_420 || format == kYUV8_P3_420;
        }

        @Contract(pure = true)
        static @NonNull String toString(int format) {
            return switch (format) {
                case kUnsupported -> "Unsupported";
                case kR8 -> "R8";
                case kR16 -> "R16";
                case kR16F -> "R16F";
                case kR32F -> "R32F";
                case kRG8 -> "RG8";
                case kRG16 -> "RG16";
                case kRG16F -> "RG16F";
                case kRG32F -> "RG32F";
                case kRGB8 -> "RGB8";
                case kRGB16 -> "RGB16";
                case kRGBA8 -> "RGBA8";
                case kRGBA16 -> "RBGA16";
                case kRGBA16F -> "RGBA16F";
                case kRGBA32F -> "RGBA32F";
                case kRGBA8_sRGB -> "RGBA8_sRGB";
                case kBGRA8 -> "BGRA8";
                case kBGRA8_sRGB -> "BGRA8_sRGB";
                case kB5_G6_R5 -> "B5_G6_R5";
                case kBGR5_A1 -> "BGR5_A1";
                case kRGB10_A2 -> "RGB10_A2";
                case kBGR10_A2 -> "BGR10_A2";
                case kR11F_G11F_B10F -> "R11F_G11F_B10F";
                case kRGB9_E5 -> "RGB9_E5";
                case kRGB8_ETC2 -> "RGB8_ETC2";
                case kRGB8_BC1 -> "RGB8_BC1";
                case kRGBA8_BC1 -> "RGBA8_BC1";
                case kYUV8_P2_420 -> "YUV8_P2_420";
                case kYUV8_P3_420 -> "YUV8_P3_420";
                case kS8 -> "S8";
                case kD16 -> "D16";
                case kD24 -> "D24";
                case kD32F -> "D32F";
                case kD24_S8 -> "D24_S8";
                case kD32F_S8 -> "D32F_S8";
                default -> throw new AssertionError(format);
            };
        }
    }

    /**
     * Describes image view type.
     */
    interface ImageType {
        /**
         * None may represent OpenGL renderbuffers.
         */
        int kNone = 0;
        /**
         * GL_TEXTURE_2D, GL_TEXTURE_2D_MULTISAMPLE;
         * VK_IMAGE_TYPE_2D, layers=1; VK_IMAGE_VIEW_TYPE_2D;
         */
        int k2D = 1;
        /**
         * GL_TEXTURE_2D_ARRAY, GL_TEXTURE_2D_MULTISAMPLE_ARRAY;
         * VK_IMAGE_TYPE_2D, layers=N; VK_IMAGE_VIEW_TYPE_2D_ARRAY;
         */
        int k2DArray = 2;
        /**
         * GL_TEXTURE_CUBE_MAP;
         * VK_IMAGE_TYPE_2D, layers=6; VK_IMAGE_VIEW_TYPE_CUBE;
         */
        int kCube = 3;
        /**
         * GL_TEXTURE_CUBE_MAP_ARRAY;
         * VK_IMAGE_TYPE_2D, layers=6N; VK_IMAGE_VIEW_TYPE_CUBE_ARRAY;
         */
        int kCubeArray = 4;
        /**
         * GL_TEXTURE_3D;
         * VK_IMAGE_TYPE_3D, layers=1; VK_IMAGE_VIEW_TYPE_3D;
         */
        int k3D = 5;
        int kCount = 6;
    }

    interface ImageCreateFlags {

    }

    /**
     * Indicates the type of pending IO operations that can be recorded for GPU resources.
     */
    interface IOType {
        int kRead = 0;
        int kWrite = 1;
        int kRW = 2;
    }

    /**
     * Indicates whether depth buffer or stencil buffer or both will be used.
     */
    interface DepthStencilFlags {
        int kNone = 0x0;
        int kDepth = 0x1;
        int kStencil = 0x2;
        int kDepthStencil = kDepth | kStencil;
    }

    /**
     * Describes the intended usage (type + access) a GPU buffer.
     * This will affect memory allocation and pipeline commands.
     * <p>
     * Note that the special type flag {@link #kUpload} and {@link #kReadback}
     * cannot be combined with other type flags, and must be {@link #kHostVisible}.
     */
    interface BufferUsageFlags {
        // DO NOT CHANGE THE ORDER OR THE BIT VALUE
        /**
         * Vertex buffer (also includes instance buffer).
         */
        int kVertex = 1;
        /**
         * Index buffer, also known as element buffer.
         */
        int kIndex = 1 << 1;
        /**
         * Indirect buffer, also known as argument buffer.
         * Not always available, check caps first.
         */
        int kDrawIndirect = 1 << 2;

        // Note: the above buffers are mesh buffers.
        // Note: draw indirect buffers may not be supported by the backend API.

        /**
         * Staging buffers for CPU to device transfer.
         */
        int kUpload = 1 << 3; // transfer src and host coherent
        /**
         * Read-back buffers for device to CPU transfer.
         */
        int kReadback = 1 << 4; // transfer dst and host cached

        // Note: transfer buffers must be created with HostVisible flag.

        /**
         * Uniform buffer, also known as constant buffer.
         * It's better <em>not</em> to combine this with other type flags.
         */
        int kUniform = 1 << 5;
        /**
         * Shader storage buffer, may be combined with other type flags.
         */
        int kStorage = 1 << 6;

        // Note: shader storage buffers may not be supported by the backend API.

        /**
         * Data store will be written to once by CPU.
         * Use device-local only memory.
         * A staging buffer is required to update it contents.
         * For static or dynamic VBO, static UBO.
         */
        int kDeviceLocal = 1 << 16;
        /**
         * Data store will be written to occasionally, CPU writes, GPU reads.
         * Use host-visible host-coherent memory and persistent mapping, and possibly device-local.
         * If write-combined memory is used, then write directly and requires sync.
         * For streaming VBO, dynamic or streaming UBO.
         */
        int kHostVisible = 1 << 17;

        // 0-16 bits are usage
        int kTypeMask = 0x0000FFFF;
        // 16-24 bits are heap
        int kAccessMask = 0x00FF0000;
    }

    /**
     * Shader stage flags.
     */
    interface ShaderFlags {

        int kVertex = 1;
        int kGeometry = 1 << 1;
        int kFragment = 1 << 2;
        int kCompute = 1 << 3;
        int kTask = 1 << 4;
        int kMesh = 1 << 5;

        int kCount = 6;
        int kAll = (1 << kCount) - 1;
    }

    /**
     * Types of descriptors.
     */
    interface DescriptorType {

        int kCombinedImageSampler = 0;
        int kSampledImage = 1;
        int kStorageImage = 2;
        int kUniformTexelBuffer = 3;
        int kStorageTexelBuffer = 4;
        int kUniformBuffer = 5;
        int kStorageBuffer = 6;
        int kInputAttachment = 7;
        int kAccelerationStructure = 8;

        int kCount = 9;
    }

    /**
     * Describes the encoding of channel data in a ColorType.
     *
     * @see #colorTypeEncoding(int)
     */
    int
            COLOR_ENCODING_UNORM = 0,
            COLOR_ENCODING_UNORM_PACK16 = 1,
            COLOR_ENCODING_UNORM_PACK32 = 2,
            COLOR_ENCODING_UNORM_SRGB = 3,
            COLOR_ENCODING_SFLOAT = 4,
            COLOR_ENCODING_UFLOAT_PACK32 = 5;

    static int colorTypeEncoding(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                 ColorInfo.CT_ALPHA_8,
                 ColorInfo.CT_RGBA_8888,
                 ColorInfo.CT_RGBX_8888,
                 ColorInfo.CT_RG_88,
                 ColorInfo.CT_BGRA_8888,
                 ColorInfo.CT_GRAY_8,
                 ColorInfo.CT_GRAY_16,
                 ColorInfo.CT_ALPHA_16,
                 ColorInfo.CT_RG_1616,
                 ColorInfo.CT_RGB_161616,
                 ColorInfo.CT_RGBA_16161616,
                 ColorInfo.CT_RGB_888,
                 ColorInfo.CT_R_8,
                 ColorInfo.CT_R_16,
                 ColorInfo.CT_GRAY_ALPHA_88,
                 ColorInfo.CT_GRAY_ALPHA_1616 -> COLOR_ENCODING_UNORM;
            case ColorInfo.CT_BGR_565,
                 ColorInfo.CT_BGRA_5551 -> COLOR_ENCODING_UNORM_PACK16;
            case ColorInfo.CT_RGBA_1010102,
                 ColorInfo.CT_BGRA_1010102 -> COLOR_ENCODING_UNORM_PACK32;
            case ColorInfo.CT_ALPHA_F16,
                 ColorInfo.CT_RGBA_F16,
                 ColorInfo.CT_RGBA_F32,
                 ColorInfo.CT_RG_F16,
                 ColorInfo.CT_R_F16,
                 ColorInfo.CT_RG_F32,
                 ColorInfo.CT_R_F32 -> COLOR_ENCODING_SFLOAT;
            case ColorInfo.CT_RGBE_9995 -> COLOR_ENCODING_UFLOAT_PACK32;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Some pixel configs are inherently clamped to [0,1], some are allowed to go outside that range,
     * and some are FP but manually clamped in the XP.
     *
     * @see #colorTypeClampType(int)
     */
    int
            CLAMP_TYPE_AUTO = 0,    // Normalized, fixed-point configs
            CLAMP_TYPE_MANUAL = 1,  // Clamped FP configs
            CLAMP_TYPE_NONE = 2;    // Normal (un-clamped) FP configs

    static int colorTypeClampType(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                 ColorInfo.CT_ALPHA_8,
                 ColorInfo.CT_BGR_565,
                 ColorInfo.CT_BGRA_5551,
                 ColorInfo.CT_RGBA_8888,
                 ColorInfo.CT_RGBX_8888,
                 ColorInfo.CT_RG_88,
                 ColorInfo.CT_BGRA_8888,
                 ColorInfo.CT_RGBA_1010102,
                 ColorInfo.CT_BGRA_1010102,
                 ColorInfo.CT_GRAY_8,
                 ColorInfo.CT_GRAY_16,
                 ColorInfo.CT_ALPHA_16,
                 ColorInfo.CT_RG_1616,
                 ColorInfo.CT_RGB_161616,
                 ColorInfo.CT_RGBA_16161616,
                 ColorInfo.CT_RGB_888,
                 ColorInfo.CT_R_8,
                 ColorInfo.CT_R_16,
                 ColorInfo.CT_GRAY_ALPHA_88,
                 ColorInfo.CT_GRAY_ALPHA_1616 -> CLAMP_TYPE_AUTO;
            case ColorInfo.CT_ALPHA_F16,
                 ColorInfo.CT_RGBA_F16,
                 ColorInfo.CT_RGBA_F32,
                 ColorInfo.CT_RG_F16,
                 ColorInfo.CT_R_F16,
                 ColorInfo.CT_RG_F32,
                 ColorInfo.CT_R_F32,
                 ColorInfo.CT_RGBE_9995 -> CLAMP_TYPE_NONE;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Geometric primitives used for drawing.
     * <p>
     * We can't simply use point or line, because both OpenGL and Vulkan can only guarantee
     * the rasterization of one pixel in screen coordinates, may or may not anti-aliased.
     */
    interface PrimitiveType {
        byte kPointList = 0; // 1 px only
        byte kLineList = 1; // 1 px wide only
        byte kLineStrip = 2; // 1 px wide only
        byte kTriangleList = 3; // separate triangle
        byte kTriangleStrip = 4; // connected triangle
        byte kCount = 5;
    }

    /**
     * Mask formats. Used by the font atlas. Important that these are 0-based.
     * <p>
     * Using {@link #maskFormatBytesPerPixel(int)} to get the number of bytes-per-pixel
     * for the specified mask format.
     */
    int
            MASK_FORMAT_A8 = 0,     // 1-byte per pixel
            MASK_FORMAT_A565 = 1,   // 2-bytes per pixel, RGB represent 3-channel LCD coverage
            MASK_FORMAT_ARGB = 2;   // 4-bytes per pixel, color format
    int LAST_MASK_FORMAT = MASK_FORMAT_ARGB;
    int MASK_FORMAT_COUNT = LAST_MASK_FORMAT + 1;

    /**
     * Return the number of bytes-per-pixel for the specified mask format.
     */
    static int maskFormatBytesPerPixel(int maskFormat) {
        assert maskFormat < MASK_FORMAT_COUNT;
        // A8 returns 1,
        // A565 returns 2,
        // ARGB returns 4.
        return 1 << maskFormat;
    }

    /**
     * Return an appropriate color type for the specified mask format.
     */
    @ColorInfo.ColorType
    static int maskFormatToColorType(int maskFormat) {
        int ct = switch (maskFormat) {
            case MASK_FORMAT_A8 -> ColorInfo.CT_R_8;
            case MASK_FORMAT_A565 -> ColorInfo.CT_BGR_565;
            case MASK_FORMAT_ARGB -> ColorInfo.CT_RGBA_8888;
            default -> throw new AssertionError();
        };
        // consistency
        assert maskFormatBytesPerPixel(maskFormat) == ColorInfo.bytesPerPixel(ct);
        return ct;
    }

    /**
     * Budget types. Used with resources that have a large memory allocation.
     *
     * @see Resource
     */
    // @formatter:off
    interface BudgetType {
        /**
         * The resource is budgeted and is subject to cleaning up under budget pressure.
         */
        byte Budgeted       = 0;
        /**
         * The resource is not budgeted and is cleaned up as soon as it has no refs regardless
         * of whether it has a unique or scratch key.
         */
        byte NotBudgeted    = 1;
        /**
         * The resource is not budgeted and is allowed to remain in the cache with no refs
         * if it has a unique key. Scratch keys are ignored.
         */
        byte WrapCacheable  = 2;
    }

    /**
     * Load ops. Used to specify the load operation to be used when an OpsTask/OpsRenderPass
     * begins execution.
     */
    interface LoadOp {
        byte kLoad      = 0;
        byte kClear     = 1;
        byte kDiscard   = 2;
        byte kCount     = 3;
    }

    /**
     * Store ops. Used to specify the store operation to be used when an OpsTask/OpsRenderPass
     * ends execution.
     */
    interface StoreOp {
        byte kStore     = 0;
        byte kDiscard   = 1;
        byte kCount     = 2;
    }

    /**
     * Combination of load ops and store ops.
     */
    interface LoadStoreOps {

        byte StoreOpShift = Byte.SIZE / 2;

        /**
         * Combination of load ops and store ops.
         * 0-4 bits: LoadOp;
         * 4-8 bits: StoreOp
         */
        byte
                Load_Store          = LoadOp.kLoad     | (StoreOp.kStore    << StoreOpShift),
                Clear_Store         = LoadOp.kClear    | (StoreOp.kStore    << StoreOpShift),
                DontLoad_Store      = LoadOp.kDiscard | (StoreOp.kStore    << StoreOpShift),
                Load_DontStore      = LoadOp.kLoad     | (StoreOp.kDiscard << StoreOpShift),
                Clear_DontStore     = LoadOp.kClear    | (StoreOp.kDiscard << StoreOpShift),
                DontLoad_DontStore  = LoadOp.kDiscard | (StoreOp.kDiscard << StoreOpShift);

        static byte make(byte load, byte store) {
            assert (load  < (1 << StoreOpShift));
            assert (store < (1 << StoreOpShift));
            return (byte) (load | (store << StoreOpShift));
        }

        /**
         * @see LoadOp
         */
        static byte loadOp(byte ops) {
            return (byte) (ops & ((1 << StoreOpShift) - 1));
        }

        /**
         * @see StoreOp
         */
        static byte storeOp(byte ops) {
            return (byte) (ops >>> StoreOpShift);
        }
    }
    // @formatter:on

    /**
     * Specifies if the holder owns the backend, OpenGL or Vulkan, object.
     */
    boolean
            Ownership_Borrowed = false, // Holder does not destroy the backend object.
            Ownership_Owned = true;     // Holder destroys the backend object.

    /**
     * Types used to describe format of indices in arrays.
     */
    interface IndexType {
        // DO NOT CHANGE THE ORDER OR THE ENUM VALUE
        int
                kUShort = 0,    // 16-bit
                kUInt = 1;      // 32-bit

        /**
         * @return size in bytes
         */
        static int size(int type) {
            assert type == kUShort || type == kUInt;
            return 2 << type;
        }
    }

    /**
     * Types used to describe format of vertices in arrays.
     */
    interface VertexAttribType {

        byte
                kFloat = 0,
                kFloat2 = 1,
                kFloat3 = 2,
                kFloat4 = 3,
                kHalf = 4,
                kHalf2 = 5,
                kHalf4 = 6;
        byte
                kInt2 = 7,   // vector of 2 32-bit ints
                kInt3 = 8,   // vector of 3 32-bit ints
                kInt4 = 9;   // vector of 4 32-bit ints
        byte
                kByte = 10,   // signed byte
                kByte2 = 11,  // vector of 2 8-bit signed bytes
                kByte4 = 12,  // vector of 4 8-bit signed bytes
                kUByte = 13,  // unsigned byte
                kUByte2 = 14, // vector of 2 8-bit unsigned bytes
                kUByte4 = 15; // vector of 4 8-bit unsigned bytes
        byte
                kUByte_norm = 16,  // unsigned byte, e.g. coverage, 0 -> 0.0f, 255 -> 1.0f.
                kUByte4_norm = 17; // vector of 4 unsigned bytes, e.g. colors, 0 -> 0.0f, 255 -> 1.0f.
        byte
                kShort2 = 18,       // vector of 2 16-bit shorts.
                kShort4 = 19;       // vector of 4 16-bit shorts.
        byte
                kUShort2 = 20,      // vector of 2 unsigned shorts. 0 -> 0, 65535 -> 65535.
                kUShort2_norm = 21; // vector of 2 unsigned shorts. 0 -> 0.0f, 65535 -> 1.0f.
        byte
                kInt = 22,
                kUInt = 23;
        byte
                kUShort_norm = 24;
        byte
                kUShort4_norm = 25; // vector of 4 unsigned shorts. 0 -> 0.0f, 65535 -> 1.0f.
        byte
                kLast = kUShort4_norm;

        /**
         * @return size in bytes
         */
        static int size(byte type) {
            switch (type) {
                case kFloat:
                    return Float.BYTES;
                case kFloat2:
                    return 2 * Float.BYTES;
                case kFloat3:
                    return 3 * Float.BYTES;
                case kFloat4:
                    return 4 * Float.BYTES;
                case kHalf:
                case kUShort_norm:
                    return Short.BYTES;
                case kHalf2:
                case kShort2:
                case kUShort2:
                case kUShort2_norm:
                    return 2 * Short.BYTES;
                case kHalf4:
                case kShort4:
                case kUShort4_norm:
                    return 4 * Short.BYTES;
                case kInt2:
                    return 2 * Integer.BYTES;
                case kInt3:
                    return 3 * Integer.BYTES;
                case kInt4:
                    return 4 * Integer.BYTES;
                case kByte:
                case kUByte:
                case kUByte_norm:
                    return Byte.BYTES;
                case kByte2:
                case kUByte2:
                    return 2 * Byte.BYTES;
                case kByte4:
                case kUByte4:
                case kUByte4_norm:
                    return 4 * Byte.BYTES;
                case kInt:
                case kUInt:
                    return Integer.BYTES;
            }
            throw new AssertionError(type);
        }
    }

    /**
     * ResourceHandle is an opaque handle to a resource, actually a table index.
     */
    int INVALID_RESOURCE_HANDLE = -1;
}

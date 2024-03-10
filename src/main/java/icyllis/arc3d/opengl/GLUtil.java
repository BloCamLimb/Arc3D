/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.opengl;

import icyllis.arc3d.core.Color;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.engine.ContextOptions;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.NativeType;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.AMDDebugOutput.*;
import static org.lwjgl.opengl.ARBDebugOutput.*;
import static org.lwjgl.opengl.EXTTextureCompressionS3TC.*;
import static org.lwjgl.opengl.GL43C.*;

/**
 * Provides OpenGL utilities.
 */
public final class GLUtil {

    /**
     * Known vendors. Internal use only.
     */
    public enum GLVendor {
        OTHER,
        NVIDIA,
        ATI,
        INTEL,
        QUALCOMM
    }

    /**
     * Known drivers. Internal use only.
     */
    public enum GLDriver {
        OTHER,
        NVIDIA,
        AMD,
        INTEL,
        FREEDRENO,
        MESA
    }

    public static GLVendor findVendor(String vendorString) {
        Objects.requireNonNull(vendorString);
        if (vendorString.equals("NVIDIA Corporation")) {
            return GLVendor.NVIDIA;
        }
        if (vendorString.equals("ATI Technologies Inc.")) {
            return GLVendor.ATI;
        }
        if (vendorString.startsWith("Intel ") || vendorString.equals("Intel")) {
            return GLVendor.INTEL;
        }
        if (vendorString.equals("Qualcomm") || vendorString.equals("freedreno")) {
            return GLVendor.QUALCOMM;
        }
        return GLVendor.OTHER;
    }

    public static GLDriver findDriver(GLVendor vendor,
                                      String vendorString,
                                      String versionString) {
        Objects.requireNonNull(vendorString);
        if (vendorString.equals("freedreno")) {
            return GLDriver.FREEDRENO;
        }
        if (vendor == GLVendor.NVIDIA) {
            return GLDriver.NVIDIA;
        }
        {
            var matcher = Pattern.compile("\\d+\\.\\d+( \\(Core Profile\\))? Mesa \\d+\\.\\d+")
                    .matcher(versionString);
            if (matcher.find()) {
                return GLDriver.MESA;
            }
        }
        if (vendor == GLVendor.ATI) {
            return GLDriver.AMD;
        }
        if (vendor == GLVendor.INTEL) {
            return GLDriver.INTEL;
        }
        return GLDriver.OTHER;
    }

    public static void info(ContextOptions options,
                            String msg) {
        PrintWriter pw = options.mInfoWriter;
        if (pw != null) {
            pw.println("[Arc3D/OpenGL] " + msg);
        }
    }

    /**
     * @see #glFormatToIndex(int)
     */
    //@formatter:off
    public static final int
            LAST_COLOR_FORMAT_INDEX = 16,
            LAST_FORMAT_INDEX       = 19;

    /**
     * Lists all supported OpenGL texture formats and converts to table index.
     * 0 is reserved for unsupported formats.
     *
     * @see #glIndexToFormat(int)
     */
    public static int glFormatToIndex(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_RGBA8                         -> 1;
            case GL_R8                            -> 2;
            case GL_RGB565                        -> 3;
            case GL_RGBA16F                       -> 4;
            case GL_R16F                          -> 5;
            case GL_RGB8                          -> 6;
            case GL_RG8                           -> 7;
            case GL_RGB10_A2                      -> 8;
            case GL_SRGB8_ALPHA8                  -> 9;
            case GL_COMPRESSED_RGB8_ETC2          -> 10;
            case GL_COMPRESSED_RGB_S3TC_DXT1_EXT  -> 11;
            case GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> 12;
            case GL_R16                           -> 13;
            case GL_RG16                          -> 14;
            case GL_RGBA16                        -> 15;
            case GL_RG16F                         -> 16; // LAST_COLOR_FORMAT_INDEX
            case GL_STENCIL_INDEX8                -> 17;
            case GL_STENCIL_INDEX16               -> 18;
            case GL_DEPTH24_STENCIL8              -> 19; // LAST_FORMAT_INDEX
            default -> 0;
        };
    }
    //@formatter:on

    /**
     * Reverse of {@link #glFormatToIndex(int)}.
     */
    @NativeType("GLenum")
    public static int glIndexToFormat(int index) {
        return switch (index) {
            case 0 -> 0;
            case 1 -> GL_RGBA8;
            case 2 -> GL_R8;
            case 3 -> GL_RGB565;
            case 4 -> GL_RGBA16F;
            case 5 -> GL_R16F;
            case 6 -> GL_RGB8;
            case 7 -> GL_RG8;
            case 8 -> GL_RGB10_A2;
            case 9 -> GL_SRGB8_ALPHA8;
            case 10 -> GL_COMPRESSED_RGB8_ETC2;
            case 11 -> GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
            case 12 -> GL_COMPRESSED_RGBA_S3TC_DXT1_EXT;
            case 13 -> GL_R16;
            case 14 -> GL_RG16;
            case 15 -> GL_RGBA16;
            case 16 -> GL_RG16F;
            case 17 -> GL_STENCIL_INDEX8;
            case 18 -> GL_STENCIL_INDEX16;
            case 19 -> GL_DEPTH24_STENCIL8;
            default -> {
                assert false : index;
                yield 0;
            }
        };
    }

    /**
     * @see Color#COLOR_CHANNEL_FLAGS_RGBA
     */
    public static int glFormatChannels(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_RGBA8,
                    GL_RGBA16,
                    GL_COMPRESSED_RGBA_S3TC_DXT1_EXT,
                    GL_SRGB8_ALPHA8,
                    GL_RGB10_A2,
                    GL_RGBA16F -> Color.COLOR_CHANNEL_FLAGS_RGBA;
            case GL_R8,
                    GL_R16,
                    GL_R16F -> Color.COLOR_CHANNEL_FLAG_RED;
            case GL_RGB565,
                    GL_COMPRESSED_RGB_S3TC_DXT1_EXT,
                    GL_COMPRESSED_RGB8_ETC2,
                    GL_RGB8 -> Color.COLOR_CHANNEL_FLAGS_RGB;
            case GL_RG8,
                    GL_RG16F,
                    GL_RG16 -> Color.COLOR_CHANNEL_FLAGS_RG;
            default -> 0;
        };
    }

    /**
     * Consistent with {@link #glFormatToIndex(int)}
     */
    public static boolean glFormatIsSupported(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_RGBA8,
                    GL_R8,
                    GL_RGB565,
                    GL_RGBA16F,
                    GL_R16F,
                    GL_RGB8,
                    GL_RG8,
                    GL_RGB10_A2,
                    GL_SRGB8_ALPHA8,
                    GL_COMPRESSED_RGB8_ETC2,
                    GL_COMPRESSED_RGB_S3TC_DXT1_EXT,
                    GL_COMPRESSED_RGBA_S3TC_DXT1_EXT,
                    GL_R16,
                    GL_RG16,
                    GL_RGBA16,
                    GL_RG16F,
                    GL_STENCIL_INDEX8,
                    GL_STENCIL_INDEX16,
                    GL_DEPTH24_STENCIL8 -> true;
            default -> false;
        };
    }

    /**
     * @see ImageInfo#COMPRESSION_NONE
     */
    @ImageInfo.CompressionType
    public static int glFormatCompressionType(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_COMPRESSED_RGB8_ETC2 -> ImageInfo.COMPRESSION_ETC2_RGB8_UNORM;
            case GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> ImageInfo.COMPRESSION_BC1_RGB8_UNORM;
            case GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> ImageInfo.COMPRESSION_BC1_RGBA8_UNORM;
            default -> ImageInfo.COMPRESSION_NONE;
        };
    }

    public static int glFormatBytesPerBlock(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_RGBA8,
                    GL_DEPTH24_STENCIL8,
                    GL_RG16F,
                    GL_RG16,
                    GL_SRGB8_ALPHA8,
                    GL_RGB10_A2,
                    // We assume the GPU stores this format 4 byte aligned
                    GL_RGB8 -> 4;
            case GL_R8,
                    GL_STENCIL_INDEX8 -> 1;
            case GL_STENCIL_INDEX16,
                    GL_R16,
                    GL_RG8,
                    GL_R16F,
                    GL_RGB565 -> 2;
            case GL_RGBA16F,
                    GL_RGBA16,
                    GL_COMPRESSED_RGBA_S3TC_DXT1_EXT,
                    GL_COMPRESSED_RGB_S3TC_DXT1_EXT,
                    GL_COMPRESSED_RGB8_ETC2 -> 8;
            default -> 0;
        };
    }

    public static int glFormatStencilBits(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_STENCIL_INDEX8,
                    GL_DEPTH24_STENCIL8 -> 8;
            case GL_STENCIL_INDEX16 -> 16;
            default -> 0;
        };
    }

    public static boolean glFormatIsPackedDepthStencil(@NativeType("GLenum") int format) {
        return format == GL_DEPTH24_STENCIL8;
    }

    public static boolean glFormatIsSRGB(@NativeType("GLenum") int format) {
        return format == GL_SRGB8_ALPHA8;
    }

    public static boolean glFormatIsCompressed(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_COMPRESSED_RGB8_ETC2,
                    GL_COMPRESSED_RGB_S3TC_DXT1_EXT,
                    GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> true;
            default -> false;
        };
    }

    public static String glFormatName(@NativeType("GLenum") int format) {
        return switch (format) {
            case GL_RGBA8 -> "RGBA8";
            case GL_R8 -> "R8";
            case GL_RGB565 -> "RGB565";
            case GL_RGBA16F -> "RGBA16F";
            case GL_R16F -> "R16F";
            case GL_RGB8 -> "RGB8";
            case GL_RG8 -> "RG8";
            case GL_RGB10_A2 -> "RGB10_A2";
            case GL_RGBA32F -> "RGBA32F";
            case GL_SRGB8_ALPHA8 -> "SRGB8_ALPHA8";
            case GL_COMPRESSED_RGB8_ETC2 -> "ETC2";
            case GL_COMPRESSED_RGB_S3TC_DXT1_EXT -> "RGB8_BC1";
            case GL_COMPRESSED_RGBA_S3TC_DXT1_EXT -> "RGBA8_BC1";
            case GL_R16 -> "R16";
            case GL_RG16 -> "RG16";
            case GL_RGBA16 -> "RGBA16";
            case GL_RG16F -> "RG16F";
            case GL_STENCIL_INDEX8 -> "STENCIL_INDEX8";
            case GL_STENCIL_INDEX16 -> "STENCIL_INDEX16";
            case GL_DEPTH24_STENCIL8 -> "DEPTH24_STENCIL8";
            default -> APIUtil.apiUnknownToken(format);
        };
    }

    @Nonnull
    public static String getDebugSource(int source) {
        return switch (source) {
            case GL_DEBUG_SOURCE_API -> "API";
            case GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "Window System";
            case GL_DEBUG_SOURCE_SHADER_COMPILER -> "Shader Compiler";
            case GL_DEBUG_SOURCE_THIRD_PARTY -> "Third Party";
            case GL_DEBUG_SOURCE_APPLICATION -> "Application";
            case GL_DEBUG_SOURCE_OTHER -> "Other";
            default -> APIUtil.apiUnknownToken(source);
        };
    }

    @Nonnull
    public static String getDebugType(int type) {
        return switch (type) {
            case GL_DEBUG_TYPE_ERROR -> "Error";
            case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "Deprecated Behavior";
            case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "Undefined Behavior";
            case GL_DEBUG_TYPE_PORTABILITY -> "Portability";
            case GL_DEBUG_TYPE_PERFORMANCE -> "Performance";
            case GL_DEBUG_TYPE_OTHER -> "Other";
            case GL_DEBUG_TYPE_MARKER -> "Marker";
            default -> APIUtil.apiUnknownToken(type);
        };
    }

    @Nonnull
    public static String getDebugSeverity(int severity) {
        return switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH -> "High";
            case GL_DEBUG_SEVERITY_MEDIUM -> "Medium";
            case GL_DEBUG_SEVERITY_LOW -> "Low";
            case GL_DEBUG_SEVERITY_NOTIFICATION -> "Notification";
            default -> APIUtil.apiUnknownToken(severity);
        };
    }

    @Nonnull
    public static String getSourceARB(int source) {
        return switch (source) {
            case GL_DEBUG_SOURCE_API_ARB -> "API";
            case GL_DEBUG_SOURCE_WINDOW_SYSTEM_ARB -> "Window System";
            case GL_DEBUG_SOURCE_SHADER_COMPILER_ARB -> "Shader Compiler";
            case GL_DEBUG_SOURCE_THIRD_PARTY_ARB -> "Third Party";
            case GL_DEBUG_SOURCE_APPLICATION_ARB -> "Application";
            case GL_DEBUG_SOURCE_OTHER_ARB -> "Other";
            default -> APIUtil.apiUnknownToken(source);
        };
    }

    @Nonnull
    public static String getTypeARB(int type) {
        return switch (type) {
            case GL_DEBUG_TYPE_ERROR_ARB -> "Error";
            case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR_ARB -> "Deprecated Behavior";
            case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR_ARB -> "Undefined Behavior";
            case GL_DEBUG_TYPE_PORTABILITY_ARB -> "Portability";
            case GL_DEBUG_TYPE_PERFORMANCE_ARB -> "Performance";
            case GL_DEBUG_TYPE_OTHER_ARB -> "Other";
            default -> APIUtil.apiUnknownToken(type);
        };
    }

    @Nonnull
    public static String getSeverityARB(int severity) {
        return switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH_ARB -> "High";
            case GL_DEBUG_SEVERITY_MEDIUM_ARB -> "Medium";
            case GL_DEBUG_SEVERITY_LOW_ARB -> "Low";
            default -> APIUtil.apiUnknownToken(severity);
        };
    }

    @Nonnull
    public static String getCategoryAMD(int category) {
        return switch (category) {
            case GL_DEBUG_CATEGORY_API_ERROR_AMD -> "API Error";
            case GL_DEBUG_CATEGORY_WINDOW_SYSTEM_AMD -> "Window System";
            case GL_DEBUG_CATEGORY_DEPRECATION_AMD -> "Deprecation";
            case GL_DEBUG_CATEGORY_UNDEFINED_BEHAVIOR_AMD -> "Undefined Behavior";
            case GL_DEBUG_CATEGORY_PERFORMANCE_AMD -> "Performance";
            case GL_DEBUG_CATEGORY_SHADER_COMPILER_AMD -> "Shader Compiler";
            case GL_DEBUG_CATEGORY_APPLICATION_AMD -> "Application";
            case GL_DEBUG_CATEGORY_OTHER_AMD -> "Other";
            default -> APIUtil.apiUnknownToken(category);
        };
    }

    @Nonnull
    public static String getSeverityAMD(int severity) {
        return switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH_AMD -> "High";
            case GL_DEBUG_SEVERITY_MEDIUM_AMD -> "Medium";
            case GL_DEBUG_SEVERITY_LOW_AMD -> "Low";
            default -> APIUtil.apiUnknownToken(severity);
        };
    }
}

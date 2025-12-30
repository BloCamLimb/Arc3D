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

package icyllis.arc3d.vulkan;

import icyllis.arc3d.core.Color;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.engine.ContextOptions;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.engine.Engine.ImageFormat;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.engine.Swizzle;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.NativeType;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.KHRAccelerationStructure;

import static org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK11.*;

import static icyllis.arc3d.engine.Engine.*;

/**
 * Provides user-defined Vulkan utilities.
 */
public final class VKUtil {

    /**
     * Creates a DirectContext for a backend context, using default context options.
     *
     * @return context or null if failed to create
     * @see #makeVulkan(VulkanBackendContext, ContextOptions)
     */
    @Nullable
    public static ImmediateContext makeVulkan(VulkanBackendContext backendContext) {
        return makeVulkan(backendContext, new ContextOptions());
    }

    /**
     * Creates a ImmediateContext for a backend context, using specified context options.
     * <p>
     * The Vulkan context (VkQueue, VkDevice, VkInstance) must be kept alive until the returned
     * ImmediateContext is destroyed. This also means that any objects created with this
     * ImmediateContext (e.g. Surfaces, Images, etc.) must also be released as they may hold
     * refs on the ImmediateContext. Once all these objects and the ImmediateContext are released,
     * then it is safe to delete the Vulkan objects.
     *
     * @return context or null if failed to create
     */
    @Nullable
    public static ImmediateContext makeVulkan(VulkanBackendContext backendContext, ContextOptions options) {
        var device = VulkanDevice.make(backendContext, options);
        if (device == null) {
            return null;
        }
        var queueManager = new VulkanQueueManager(device);
        ImmediateContext context = new ImmediateContext(device, queueManager);
        if (context.init()) {
            return context;
        }
        context.unref();
        return null;
    }

    /**
     * Runtime assertion against a {@code VkResult} value, throws an exception
     * with a human-readable error message if failed.
     *
     * @param vkResult the {@code VkResult} value
     * @throws AssertionError the VkResult is not VK_SUCCESS
     */
    public static void _CHECK_(@NativeType("VkResult") int vkResult) {
        if (vkResult != VK_SUCCESS) throw new AssertionError(getResultMessage(vkResult));
    }

    /**
     * Runtime assertion against a {@code VkResult} value, throws an exception
     * with a human-readable error message if failed.
     *
     * @param vkResult the {@code VkResult} value
     * @throws AssertionError the VkResult is negative
     */
    public static void _CHECK_ERROR_(@NativeType("VkResult") int vkResult) {
        if (vkResult < VK_SUCCESS) throw new AssertionError(getResultMessage(vkResult));
    }

    /**
     * Translates a Vulkan {@code VkResult} value to a String describing the result.
     *
     * @param result the {@code VkResult} value
     * @return the result description
     */
    public static String getResultMessage(int result) {
        return switch (result) {
            // Success codes
            case VK_SUCCESS -> "Command successfully completed.";
            case VK_NOT_READY -> "A fence or query has not yet completed.";
            case VK_TIMEOUT -> "A wait operation has not completed in the specified time.";
            case VK_EVENT_SET -> "An event is signaled.";
            case VK_EVENT_RESET -> "An event is unsignaled.";
            case VK_INCOMPLETE -> "A return array was too small for the result.";
            case VK_SUBOPTIMAL_KHR -> "A swap-chain no longer matches the surface properties exactly, but can still " +
                    "be used to present to the surface successfully.";

            // Error codes
            case VK_ERROR_OUT_OF_HOST_MEMORY -> "A host memory allocation has failed.";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "A device memory allocation has failed.";
            case VK_ERROR_INITIALIZATION_FAILED -> "Initialization of an object could not be completed for " +
                    "implementation-specific reasons.";
            case VK_ERROR_DEVICE_LOST -> "The logical or physical device has been lost.";
            case VK_ERROR_MEMORY_MAP_FAILED -> "Mapping of a memory object has failed.";
            case VK_ERROR_LAYER_NOT_PRESENT -> "A requested layer is not present or could not be loaded.";
            case VK_ERROR_EXTENSION_NOT_PRESENT -> "A requested extension is not supported.";
            case VK_ERROR_FEATURE_NOT_PRESENT -> "A requested feature is not supported.";
            case VK_ERROR_INCOMPATIBLE_DRIVER -> "The requested version of Vulkan is not supported by the driver or " +
                    "is otherwise incompatible for implementation-specific reasons.";
            case VK_ERROR_TOO_MANY_OBJECTS -> "Too many objects of the type have already been created.";
            case VK_ERROR_FORMAT_NOT_SUPPORTED -> "A requested format is not supported on this device.";
            case VK_ERROR_SURFACE_LOST_KHR -> "A surface is no longer available.";
            case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -> "The requested window is already connected to a VkSurfaceKHR, " +
                    "or to some other non-Vulkan API.";
            case VK_ERROR_OUT_OF_DATE_KHR -> "A surface has changed in such a way that it is no longer compatible " +
                    "with the swap-chain, and further presentation requests using the swap-chain will fail. " +
                    "Applications must query the new surface properties and recreate their swap-chain if they wish" +
                    "to continue presenting to the surface.";
            case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -> "The display used by a swap-chain does not use the same " +
                    "presentable image layout, or is incompatible in a way that prevents sharing an image.";
            case VK_ERROR_VALIDATION_FAILED_EXT -> "A validation layer found an error.";
            default -> String.format("%s [%d]", "Unknown", result);
        };
    }

    /**
     * Known vendor IDs.
     */
    public static final int
            kAMD_VendorID = 0x1002,
            kImgTec_VendorID = 0x1010,
            kApple_VendorID = 0x106B,
            kNVIDIA_VendorID = 0x10DE,
            kARM_VendorID = 0x13B5,
            kBroadcom_VendorID = 0x14E4,
            kGoogle_VendorID = 0x1AE0,
            kMooreThreads_VendorID = 0x1ED5,
            kQualcomm_VendorID = 0x5143,
            kIntel_VendorID = 0x8086;

    public static String getVendorIDName(int vkVendorID) {
        return switch (vkVendorID) {
            case kAMD_VendorID -> "AMD";
            case kImgTec_VendorID -> "ImgTec";
            case kApple_VendorID -> "Apple";
            case kNVIDIA_VendorID -> "NVIDIA";
            case kARM_VendorID -> "ARM";
            case kBroadcom_VendorID -> "Broadcom";
            case kGoogle_VendorID -> "Google";
            case kMooreThreads_VendorID -> "Moore Threads";
            case kQualcomm_VendorID -> "Qualcomm";
            case kIntel_VendorID -> "Intel";
            case VK_VENDOR_ID_MESA -> "Mesa";
            default -> APIUtil.apiUnknownToken(vkVendorID);
        };
    }

    public static String getPhysicalDeviceTypeName(@NativeType("VkPhysicalDeviceType") int vkPhysicalDeviceType) {
        return switch (vkPhysicalDeviceType) {
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> "Integrated GPU";
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> "Discrete GPU";
            case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> "Virtual GPU";
            case VK_PHYSICAL_DEVICE_TYPE_CPU -> "CPU";
            default -> "Other";
        };
    }

    //@formatter:off

    /**
     * Lists all supported Vulkan image formats and converts to table index.
     * 0 is reserved for unsupported formats.
     */
    public static int vkFormatToImageFormat(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8_UNORM                  -> ImageFormat.kR8;
            case VK_FORMAT_R16_UNORM                 -> ImageFormat.kR16;
            case VK_FORMAT_R16_SFLOAT                -> ImageFormat.kR16F;
            case VK_FORMAT_R32_SFLOAT                -> ImageFormat.kR32F;
            case VK_FORMAT_R8G8_UNORM                -> ImageFormat.kRG8;
            case VK_FORMAT_R16G16_UNORM              -> ImageFormat.kRG16;
            case VK_FORMAT_R16G16_SFLOAT             -> ImageFormat.kRG16F;
            case VK_FORMAT_R32G32_SFLOAT             -> ImageFormat.kRG32F;
            case VK_FORMAT_R8G8B8_UNORM              -> ImageFormat.kRGB8;
            case VK_FORMAT_R16G16B16_UNORM           -> ImageFormat.kRGB16;
            case VK_FORMAT_R5G6B5_UNORM_PACK16       -> ImageFormat.kB5_G6_R5;
            case VK_FORMAT_R8G8B8A8_UNORM            -> ImageFormat.kRGBA8;
            case VK_FORMAT_R16G16B16A16_UNORM        -> ImageFormat.kRGBA16;
            case VK_FORMAT_R16G16B16A16_SFLOAT       -> ImageFormat.kRGBA16F;
            case VK_FORMAT_R32G32B32A32_SFLOAT       -> ImageFormat.kRGBA32F;
            case VK_FORMAT_A2B10G10R10_UNORM_PACK32  -> ImageFormat.kRGB10_A2;
            case VK_FORMAT_A1R5G5B5_UNORM_PACK16     -> ImageFormat.kBGR5_A1;
            case VK_FORMAT_B8G8R8A8_UNORM            -> ImageFormat.kBGRA8;
            case VK_FORMAT_A2R10G10B10_UNORM_PACK32  -> ImageFormat.kBGR10_A2;
            case VK_FORMAT_E5B9G9R9_UFLOAT_PACK32    -> ImageFormat.kRGB9_E5;
            case VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK   -> ImageFormat.kRGB8_ETC2;
            case VK_FORMAT_BC1_RGB_UNORM_BLOCK       -> ImageFormat.kRGB8_BC1;
            case VK_FORMAT_BC1_RGBA_UNORM_BLOCK      -> ImageFormat.kRGBA8_BC1;
            case VK_FORMAT_G8_B8R8_2PLANE_420_UNORM  -> ImageFormat.kYUV8_P2_420;
            case VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM -> ImageFormat.kYUV8_P3_420;
            case VK_FORMAT_S8_UINT                   -> ImageFormat.kS8;
            case VK_FORMAT_D16_UNORM                 -> ImageFormat.kD16;
            case VK_FORMAT_D32_SFLOAT                -> ImageFormat.kD32F;
            case VK_FORMAT_D24_UNORM_S8_UINT         -> ImageFormat.kD24_S8;
            case VK_FORMAT_D32_SFLOAT_S8_UINT        -> ImageFormat.kD32F_S8;
            default -> ImageFormat.kUnsupported;
        };
    }

    /**
     * Reverse of {@link #vkFormatToImageFormat(int)}.
     */
    @NativeType("VkFormat")
    public static int toVkFormat(int imageFormat) {
        return switch (imageFormat) {
            case ImageFormat.kR8             -> VK_FORMAT_R8_UNORM;
            case ImageFormat.kR16            -> VK_FORMAT_R16_UNORM;
            case ImageFormat.kR16F           -> VK_FORMAT_R16_SFLOAT;
            case ImageFormat.kR32F           -> VK_FORMAT_R32_SFLOAT;
            case ImageFormat.kRG8            -> VK_FORMAT_R8G8_UNORM;
            case ImageFormat.kRG16           -> VK_FORMAT_R16G16_UNORM;
            case ImageFormat.kRG16F          -> VK_FORMAT_R16G16_SFLOAT;
            case ImageFormat.kRG32F          -> VK_FORMAT_R32G32_SFLOAT;
            case ImageFormat.kRGB8           -> VK_FORMAT_R8G8B8_UNORM;
            case ImageFormat.kRGB16          -> VK_FORMAT_R16G16B16_UNORM;
            case ImageFormat.kB5_G6_R5       -> VK_FORMAT_R5G6B5_UNORM_PACK16;
            case ImageFormat.kRGBA8          -> VK_FORMAT_R8G8B8A8_UNORM;
            case ImageFormat.kRGBA16         -> VK_FORMAT_R16G16B16A16_UNORM;
            case ImageFormat.kRGBA16F        -> VK_FORMAT_R16G16B16A16_SFLOAT;
            case ImageFormat.kRGBA32F        -> VK_FORMAT_R32G32B32A32_SFLOAT;
            case ImageFormat.kRGB10_A2       -> VK_FORMAT_A2B10G10R10_UNORM_PACK32;
            case ImageFormat.kBGR5_A1        -> VK_FORMAT_A1R5G5B5_UNORM_PACK16;
            case ImageFormat.kBGRA8          -> VK_FORMAT_B8G8R8A8_UNORM;
            case ImageFormat.kBGR10_A2       -> VK_FORMAT_A2R10G10B10_UNORM_PACK32;
            case ImageFormat.kRGB9_E5        -> VK_FORMAT_E5B9G9R9_UFLOAT_PACK32;
            case ImageFormat.kRGB8_ETC2      -> VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK;
            case ImageFormat.kRGB8_BC1       -> VK_FORMAT_BC1_RGB_UNORM_BLOCK;
            case ImageFormat.kRGBA8_BC1      -> VK_FORMAT_BC1_RGBA_UNORM_BLOCK;
            case ImageFormat.kYUV8_P2_420    -> VK_FORMAT_G8_B8R8_2PLANE_420_UNORM;
            case ImageFormat.kYUV8_P3_420    -> VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM;
            case ImageFormat.kS8             -> VK_FORMAT_S8_UINT;
            case ImageFormat.kD16            -> VK_FORMAT_D16_UNORM;
            case ImageFormat.kD32F           -> VK_FORMAT_D32_SFLOAT;
            case ImageFormat.kD24_S8         -> VK_FORMAT_D24_UNORM_S8_UINT;
            case ImageFormat.kD32F_S8        -> VK_FORMAT_D32_SFLOAT_S8_UINT;
            default -> {
                // guaranteed by VulkanCaps
                assert false : imageFormat;
                yield VK_FORMAT_UNDEFINED;
            }
        };
    }

    //@formatter:on

    /**
     * Consistent with {@link #vkFormatToIndex(int)}
     */
    public static boolean vkFormatIsSupported(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8G8B8A8_UNORM,
                 VK_FORMAT_R8_UNORM,
                 VK_FORMAT_R5G6B5_UNORM_PACK16 -> true;
            default -> false;
        };
    }

    /**
     * @return see Color
     */
    public static int vkFormatChannels(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8G8B8A8_UNORM,
                 VK_FORMAT_R16G16B16A16_UNORM,
                 VK_FORMAT_BC1_RGBA_UNORM_BLOCK,
                 VK_FORMAT_R8G8B8A8_SRGB,
                 VK_FORMAT_R4G4B4A4_UNORM_PACK16,
                 VK_FORMAT_B4G4R4A4_UNORM_PACK16,
                 VK_FORMAT_A2R10G10B10_UNORM_PACK32,
                 VK_FORMAT_A2B10G10R10_UNORM_PACK32,
                 VK_FORMAT_R16G16B16A16_SFLOAT,
                 VK_FORMAT_B8G8R8A8_UNORM -> Color.COLOR_CHANNEL_FLAGS_RGBA;
            case VK_FORMAT_R8_UNORM,
                 VK_FORMAT_R16_UNORM,
                 VK_FORMAT_R16_SFLOAT -> Color.COLOR_CHANNEL_FLAG_RED;
            case VK_FORMAT_R5G6B5_UNORM_PACK16,
                 VK_FORMAT_BC1_RGB_UNORM_BLOCK,
                 VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK,
                 VK_FORMAT_R8G8B8_UNORM -> Color.COLOR_CHANNEL_FLAGS_RGB;
            case VK_FORMAT_R8G8_UNORM,
                 VK_FORMAT_R16G16_SFLOAT,
                 VK_FORMAT_R16G16_UNORM -> Color.COLOR_CHANNEL_FLAGS_RG;
            // either depth/stencil format or unsupported yet
            default -> 0;
        };
    }

    @ColorInfo.CompressionType
    public static int vkFormatCompressionType(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK -> ColorInfo.COMPRESSION_ETC2_RGB8_UNORM;
            case VK_FORMAT_BC1_RGB_UNORM_BLOCK -> ColorInfo.COMPRESSION_BC1_RGB8_UNORM;
            case VK_FORMAT_BC1_RGBA_UNORM_BLOCK -> ColorInfo.COMPRESSION_BC1_RGBA8_UNORM;
            default -> ColorInfo.COMPRESSION_NONE;
        };
    }


    public static int vkFormatBytesPerBlock(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8G8B8A8_UNORM,
                 VK_FORMAT_D24_UNORM_S8_UINT,
                 VK_FORMAT_R16G16_SFLOAT,
                 VK_FORMAT_R16G16_UNORM,
                 VK_FORMAT_R8G8B8A8_SRGB,
                 VK_FORMAT_A2R10G10B10_UNORM_PACK32,
                 VK_FORMAT_A2B10G10R10_UNORM_PACK32,
                 VK_FORMAT_B8G8R8A8_UNORM -> 4;
            case VK_FORMAT_R8_UNORM,
                 VK_FORMAT_S8_UINT -> 1;
            case VK_FORMAT_R5G6B5_UNORM_PACK16,
                 VK_FORMAT_R16_UNORM,
                 VK_FORMAT_R4G4B4A4_UNORM_PACK16,
                 VK_FORMAT_B4G4R4A4_UNORM_PACK16,
                 VK_FORMAT_R8G8_UNORM,
                 VK_FORMAT_R16_SFLOAT -> 2;
            case VK_FORMAT_R16G16B16A16_SFLOAT,
                 VK_FORMAT_D32_SFLOAT_S8_UINT,
                 VK_FORMAT_R16G16B16A16_UNORM,
                 VK_FORMAT_BC1_RGBA_UNORM_BLOCK,
                 VK_FORMAT_BC1_RGB_UNORM_BLOCK,
                 VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK -> 8;
            case VK_FORMAT_R8G8B8_UNORM,
                 VK_FORMAT_G8_B8R8_2PLANE_420_UNORM,
                 VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM -> 3;
            default -> 0;
        };
    }

    public static int vkFormatDepthBits(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_D16_UNORM,
                 VK_FORMAT_D16_UNORM_S8_UINT -> 16;
            case VK_FORMAT_D24_UNORM_S8_UINT,
                 VK_FORMAT_X8_D24_UNORM_PACK32 -> 24;
            case VK_FORMAT_D32_SFLOAT,
                 VK_FORMAT_D32_SFLOAT_S8_UINT -> 32;
            default -> 0;
        };
    }

    public static int vkFormatStencilBits(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_S8_UINT,
                 VK_FORMAT_D16_UNORM_S8_UINT,
                 VK_FORMAT_D24_UNORM_S8_UINT,
                 VK_FORMAT_D32_SFLOAT_S8_UINT -> 8;
            default -> 0;
        };
    }

    public static String vkFormatName(@NativeType("VkFormat") int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_R8G8B8A8_UNORM -> "R8G8B8A8_UNORM";
            case VK_FORMAT_R8_UNORM -> "R8_UNORM";
            case VK_FORMAT_B8G8R8A8_UNORM -> "B8G8R8A8_UNORM";
            case VK_FORMAT_R5G6B5_UNORM_PACK16 -> "R5G6B5_UNORM_PACK16";
            case VK_FORMAT_R16G16B16A16_SFLOAT -> "R16G16B16A16_SFLOAT";
            case VK_FORMAT_R16_SFLOAT -> "R16_SFLOAT";
            case VK_FORMAT_R8G8B8_UNORM -> "R8G8B8_UNORM";
            case VK_FORMAT_R8G8_UNORM -> "R8G8_UNORM";
            case VK_FORMAT_A2B10G10R10_UNORM_PACK32 -> "A2B10G10R10_UNORM_PACK32";
            case VK_FORMAT_A2R10G10B10_UNORM_PACK32 -> "A2R10G10B10_UNORM_PACK32";
            case VK_FORMAT_B4G4R4A4_UNORM_PACK16 -> "B4G4R4A4_UNORM_PACK16";
            case VK_FORMAT_R4G4B4A4_UNORM_PACK16 -> "R4G4B4A4_UNORM_PACK16";
            case VK_FORMAT_R32G32B32A32_SFLOAT -> "R32G32B32A32_SFLOAT";
            case VK_FORMAT_R8G8B8A8_SRGB -> "R8G8B8A8_SRGB";
            case VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK -> "ETC2_R8G8B8_UNORM_BLOCK";
            case VK_FORMAT_BC1_RGB_UNORM_BLOCK -> "BC1_RGB_UNORM_BLOCK";
            case VK_FORMAT_BC1_RGBA_UNORM_BLOCK -> "BC1_RGBA_UNORM_BLOCK";
            case VK_FORMAT_R16_UNORM -> "R16_UNORM";
            case VK_FORMAT_R16G16_UNORM -> "R16G16_UNORM";
            case VK_FORMAT_R16G16B16A16_UNORM -> "R16G16B16A16_UNORM";
            case VK_FORMAT_R16G16_SFLOAT -> "R16G16_SFLOAT";
            case VK_FORMAT_S8_UINT -> "S8_UINT";
            case VK_FORMAT_D24_UNORM_S8_UINT -> "D24_UNORM_S8_UINT";
            case VK_FORMAT_D32_SFLOAT_S8_UINT -> "D32_SFLOAT_S8_UINT";
            default -> "Unknown";
        };
    }

    public static int toVkSampleCount(int sampleCount) {
        assert sampleCount >= 1;
        return switch (sampleCount) {
            case 1 -> VK_SAMPLE_COUNT_1_BIT;
            case 2 -> VK_SAMPLE_COUNT_2_BIT;
            case 4 -> VK_SAMPLE_COUNT_4_BIT;
            case 8 -> VK_SAMPLE_COUNT_8_BIT;
            case 16 -> VK_SAMPLE_COUNT_16_BIT;
            case 32 -> VK_SAMPLE_COUNT_32_BIT;
            case 64 -> VK_SAMPLE_COUNT_64_BIT;
            default -> 0;
        };
    }

    public static int toVkPipelineStageFlags(int shaderFlags) {
        int result = 0;
        if ((shaderFlags & ShaderFlags.kVertex) != 0) {
            result |= VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
        }
        if ((shaderFlags & ShaderFlags.kGeometry) != 0) {
            result |= VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT;
        }
        if ((shaderFlags & ShaderFlags.kFragment) != 0) {
            result |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        }
        if ((shaderFlags & ShaderFlags.kCompute) != 0) {
            result |= VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        }
        if ((shaderFlags & ShaderFlags.kTask) != 0) {
            result |= EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT;
        }
        if ((shaderFlags & ShaderFlags.kMesh) != 0) {
            result |= EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
        }

        return result;
    }

    public static int toVkLoadOp(byte loadOp) {
        return switch (loadOp) {
            case LoadOp.kLoad -> VK_ATTACHMENT_LOAD_OP_LOAD;
            case LoadOp.kClear -> VK_ATTACHMENT_LOAD_OP_CLEAR;
            case LoadOp.kDiscard -> VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            default -> throw new AssertionError(loadOp);
        };
    }

    public static int toVkStoreOp(byte storeOp) {
        return switch (storeOp) {
            case StoreOp.kStore -> VK_ATTACHMENT_STORE_OP_STORE;
            case StoreOp.kDiscard -> VK_ATTACHMENT_STORE_OP_DONT_CARE;
            default -> throw new AssertionError(storeOp);
        };
    }

    public static int toVkImageViewType(int imageType) {
        return switch (imageType) {
            case ImageType.k2D -> VK_IMAGE_VIEW_TYPE_2D;
            case ImageType.k2DArray -> VK_IMAGE_VIEW_TYPE_2D_ARRAY;
            case ImageType.kCube -> VK_IMAGE_VIEW_TYPE_CUBE;
            case ImageType.kCubeArray -> VK_IMAGE_VIEW_TYPE_CUBE_ARRAY;
            case ImageType.k3D -> VK_IMAGE_VIEW_TYPE_3D;
            default -> {
                assert false : imageType;
                yield VK_IMAGE_VIEW_TYPE_1D;
            }
        };
    }

    public static int toVkComponentSwizzle(int index) {
        return switch (index) {
            case Swizzle.COMPONENT_R    -> VK_COMPONENT_SWIZZLE_R;
            case Swizzle.COMPONENT_G    -> VK_COMPONENT_SWIZZLE_G;
            case Swizzle.COMPONENT_B    -> VK_COMPONENT_SWIZZLE_B;
            case Swizzle.COMPONENT_A    -> VK_COMPONENT_SWIZZLE_A;
            case Swizzle.COMPONENT_ZERO -> VK_COMPONENT_SWIZZLE_ZERO;
            case Swizzle.COMPONENT_ONE  -> VK_COMPONENT_SWIZZLE_ONE;
            default -> {
                assert false : index;
                yield VK_COMPONENT_SWIZZLE_IDENTITY;
            }
        };
    }

    public static int getFullAspectMask(int vkFormat) {
        return switch (vkFormat) {
            case VK_FORMAT_S8_UINT -> VK_IMAGE_ASPECT_STENCIL_BIT;
            case VK_FORMAT_D16_UNORM,
                 VK_FORMAT_X8_D24_UNORM_PACK32,
                 VK_FORMAT_D32_SFLOAT -> VK_IMAGE_ASPECT_DEPTH_BIT;
            case VK_FORMAT_D16_UNORM_S8_UINT,
                 VK_FORMAT_D24_UNORM_S8_UINT,
                 VK_FORMAT_D32_SFLOAT_S8_UINT -> VK_IMAGE_ASPECT_STENCIL_BIT | VK_IMAGE_ASPECT_DEPTH_BIT;
            default -> VK_IMAGE_ASPECT_COLOR_BIT;
        };
    }

    public static int toVkDescriptorType(int type) {
        //TODO currently we only use dynamic offsets, but backend is guaranteed to support only a
        // small number of dynamic bindings,
        return switch (type) {
            case DescriptorType.kCombinedImageSampler ->
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case DescriptorType.kSampledImage ->
                    VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case DescriptorType.kStorageImage ->
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case DescriptorType.kUniformTexelBuffer ->
                    VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            case DescriptorType.kStorageTexelBuffer ->
                    VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER;
            case DescriptorType.kUniformBuffer ->
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            case DescriptorType.kStorageBuffer ->
                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
            case DescriptorType.kInputAttachment ->
                    VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT;
            case DescriptorType.kAccelerationStructure ->
                    KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            default -> throw new AssertionError(type);
        };
    }

    public static int toVkFilter(int filter) {
        return switch (filter) {
            case SamplerDesc.FILTER_NEAREST ->
                    VK_FILTER_NEAREST;
            case SamplerDesc.FILTER_LINEAR ->
                    VK_FILTER_LINEAR;
            default -> throw new AssertionError(filter);
        };
    }

    public static int toVkMipmapMode(int mipmapMode) {
        return switch (mipmapMode) {
            case SamplerDesc.MIPMAP_MODE_NONE ->
                // none mipmap will use lod clamp instead, see VulkanSampler
                    VK_SAMPLER_MIPMAP_MODE_NEAREST;
            case SamplerDesc.MIPMAP_MODE_NEAREST ->
                    VK_SAMPLER_MIPMAP_MODE_NEAREST;
            case SamplerDesc.MIPMAP_MODE_LINEAR ->
                    VK_SAMPLER_MIPMAP_MODE_LINEAR;
            default -> throw new AssertionError(mipmapMode);
        };
    }

    public static int toVkAddressMode(int addressMode) {
        return switch (addressMode) {
            case SamplerDesc.ADDRESS_MODE_REPEAT ->
                VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case SamplerDesc.ADDRESS_MODE_MIRRORED_REPEAT ->
                VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case SamplerDesc.ADDRESS_MODE_CLAMP_TO_EDGE ->
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case SamplerDesc.ADDRESS_MODE_CLAMP_TO_BORDER ->
                VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            default -> throw new AssertionError(addressMode);
        };
    }
}

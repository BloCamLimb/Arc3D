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

import icyllis.arc3d.compiler.*;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.engine.Engine.ImageFormat;
import icyllis.arc3d.engine.ShaderCaps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.util.Arrays;
import java.util.Objects;

import static icyllis.arc3d.vulkan.VKUtil.*;
import static org.lwjgl.vulkan.VK11.*;

public class VulkanCaps extends Caps {

    // the minimum value of 'maxBoundDescriptorSets' required by VkSpec
    public static final int MAX_BOUND_SETS = 4;

    int mAPIVersion;
    int mDriverVersion;
    int mVendorID;
    int mDeviceID;
    int mDeviceType;
    String mDeviceName;
    final byte[] mPipelineCacheUUID = new byte[VK_UUID_SIZE];

    float mMaxSamplerAnisotropy = 1.f;

    /**
     * Vulkan image format table.
     */
    final FormatInfo[] mFormatTable =
            new FormatInfo[ImageFormat.kLastColor + 1];

    /**
     * Map {@link ColorInfo}.CT_XXX to {@link ImageFormat}.
     * May contain {@link ImageFormat#kUnsupported}.
     */
    private final int[] mColorTypeToFormat =
            new int[ColorInfo.CT_COUNT];

    public VulkanCaps(ContextOptions options,
                      VkPhysicalDevice physDev,
                      int physicalDeviceVersion,
                      VkPhysicalDeviceFeatures2 deviceFeatures2,
                      VKCapabilitiesInstance capabilitiesInstance,
                      VKCapabilitiesDevice capabilitiesDevice) {
        super(options);

        Logger logger = Objects.requireNonNullElse(options.mLogger, NOPLogger.NOP_LOGGER);

        mDepthClipNegativeOneToOne = false;

        ShaderCaps shaderCaps = mShaderCaps;
        shaderCaps.mTargetApi = TargetApi.VULKAN_1_0;
        shaderCaps.mGLSLVersion = GLSLVersion.GLSL_450;

        logger.info(MARKER, "Physical device version: {}.{}.{}",
                VK_VERSION_MAJOR(physicalDeviceVersion),
                VK_VERSION_MINOR(physicalDeviceVersion),
                VK_VERSION_PATCH(physicalDeviceVersion));

        try (var stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties physProps = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(physDev, physProps);
            mAPIVersion = physProps.apiVersion();
            mDriverVersion = physProps.driverVersion();
            mVendorID = physProps.vendorID();
            mDeviceID = physProps.deviceID();
            mDeviceType = physProps.deviceType();
            mDeviceName = physProps.deviceNameString();
            physProps.pipelineCacheUUID().get(0, mPipelineCacheUUID);

            VkPhysicalDeviceLimits limits = physProps.limits();
            VkPhysicalDeviceFeatures features = deviceFeatures2.features();

            if (Integer.compareUnsigned(physicalDeviceVersion,
                    VK_MAKE_VERSION(1, 3, 0)) >= 0) {
                shaderCaps.mSPIRVVersion = SPIRVVersion.SPIRV_1_6;
            } else if (Integer.compareUnsigned(physicalDeviceVersion,
                    VK_MAKE_VERSION(1, 2, 0)) >= 0) {
                shaderCaps.mSPIRVVersion = SPIRVVersion.SPIRV_1_5;
            } else if (Integer.compareUnsigned(physicalDeviceVersion,
                    VK_MAKE_VERSION(1, 1, 0)) >= 0) {
                shaderCaps.mSPIRVVersion = SPIRVVersion.SPIRV_1_3;
            } else {
                shaderCaps.mSPIRVVersion = SPIRVVersion.SPIRV_1_0;
            }

            mMaxVertexAttributes = (int) Math.min(
                    Integer.toUnsignedLong(limits.maxVertexInputAttributes()), Integer.MAX_VALUE);
            mMaxVertexBindings = (int) Math.min(
                    Integer.toUnsignedLong(limits.maxVertexInputBindings()), Integer.MAX_VALUE);

            mMaxTextureSize = (int) Math.min(
                    Integer.toUnsignedLong(limits.maxImageDimension2D()), Integer.MAX_VALUE);
            assert mMaxTextureSize >= 4096;

            mMaxPushConstantsSize = (int) Math.min(
                    Integer.toUnsignedLong(limits.maxPushConstantsSize()), Integer.MAX_VALUE);
            // our attachment points are consistent with draw buffers
            mMaxColorAttachments = (int) Math.min(Math.min(
                            Integer.toUnsignedLong(limits.maxFragmentOutputAttachments()),
                            Integer.toUnsignedLong(limits.maxColorAttachments())),
                    MAX_COLOR_TARGETS);
            assert mMaxColorAttachments >= 4;

            mMinUniformBufferOffsetAlignment = (int) limits.minUniformBufferOffsetAlignment();
            mMinStorageBufferOffsetAlignment = (int) limits.minStorageBufferOffsetAlignment();
            // many drivers report 1 but actually trigger slow path, use 4 at least.
            // actually used alignment is generally aligned up to a higher value.
            mOptimalBufferCopyOffsetAlignment = Math.max((int) limits.optimalBufferCopyOffsetAlignment(), 4);
            mOptimalBufferCopyRowBytesAlignment = Math.max((int) limits.optimalBufferCopyRowPitchAlignment(), 4);

            mAnisotropySupport = features.samplerAnisotropy();
            if (mAnisotropySupport) {
                mMaxSamplerAnisotropy = limits.maxSamplerAnisotropy();
            }

            initFormatTable(logger, physDev, physProps, stack);

            initGLSL(deviceFeatures2, features, limits);
        }
    }

    void initFormatTable(Logger logger,
                         VkPhysicalDevice physDev,
                         VkPhysicalDeviceProperties physProps,
                         MemoryStack stack) {
        for (int i = 0; i < mFormatTable.length; i++) {
            mFormatTable[i] = new FormatInfo();
        }

        // Format: VK_FORMAT_R8_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR8);
            info.init(logger, physDev, physProps, VK_FORMAT_R8_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[3];
                // Format: R8, Surface: kR_8
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_R_8;
                    ctInfo.mTransferColorType = ColorInfo.CT_R_8;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Format: R8, Surface: kAlpha_8
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_ALPHA_8;
                    ctInfo.mTransferColorType = ColorInfo.CT_ALPHA_8;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("000r");
                    ctInfo.mWriteSwizzle = Swizzle.make("a000");
                }

                // Format: R8, Surface: kGray_8
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[2] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_8;
                    ctInfo.mTransferColorType = ColorInfo.CT_GRAY_8;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrr1");
                }
            }
        }

        // Format: VK_FORMAT_R8G8_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG8);
            info.init(logger, physDev, physProps, VK_FORMAT_R8G8_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[2];
                // Format: RG8, Surface: kRG_88
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RG_88;
                    ctInfo.mTransferColorType = ColorInfo.CT_RG_88;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Format: RG8, Surface: kGrayAlpha_88
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_ALPHA_88;
                    ctInfo.mTransferColorType = ColorInfo.CT_GRAY_ALPHA_88;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrrg");
                }
            }
        }

        // Format: VK_FORMAT_R8G8B8_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8);
            info.init(logger, physDev, physProps, VK_FORMAT_R8G8B8_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: RGB8, Surface: kRGB_888
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGB_888;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGB_888;
                    // disallow rendering to this format
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                }
            }
        }

        // Format: VK_FORMAT_R8G8B8A8_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA8);
            info.init(logger, physDev, physProps, VK_FORMAT_R8G8B8A8_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[2];
                // Format: VK_FORMAT_R8G8B8A8_UNORM, Surface: kRGBA_8888
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBA_8888;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGBA_8888;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
                // Format: VK_FORMAT_R8G8B8A8_UNORM, Surface: kRGBX_8888
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBX_8888;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGBX_8888;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.RGB1;
                }
            }
        }

        // Format: VK_FORMAT_R16_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR16);
            info.init(logger, physDev, physProps, VK_FORMAT_R16_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[3];

                // Format: R16, Surface: kR_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_R_16;
                    ctInfo.mTransferColorType = ColorInfo.CT_R_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Format: R16, Surface: kAlpha_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_ALPHA_16;
                    ctInfo.mTransferColorType = ColorInfo.CT_ALPHA_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("000r");
                    ctInfo.mWriteSwizzle = Swizzle.make("a000");
                }

                // Format: R16, Surface: kGray_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[2] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_16;
                    ctInfo.mTransferColorType = ColorInfo.CT_GRAY_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrr1");
                }
            }
        }

        // Format: VK_FORMAT_R16G16_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG16);
            info.init(logger, physDev, physProps, VK_FORMAT_R16G16_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[2];
                // Format: RG16, Surface: kRG_1616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RG_1616;
                    ctInfo.mTransferColorType = ColorInfo.CT_RG_1616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Format: RG16, Surface: kGrayAlpha_1616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_ALPHA_1616;
                    ctInfo.mTransferColorType = ColorInfo.CT_GRAY_ALPHA_1616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrrg");
                }
            }
        }

        // Format: VK_FORMAT_R16G16B16_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB16);
            info.init(logger, physDev, physProps, VK_FORMAT_R16G16B16_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: RGB16, Surface: kRGB_161616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGB_161616;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGB_161616;
                    // disallow rendering to this format
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                }
            }
        }

        // Format: VK_FORMAT_R16G16B16_UNORM
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA16);
            info.init(logger, physDev, physProps, VK_FORMAT_R16G16B16A16_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                // Format: GL_RGBA16, Surface: kRGBA_16161616
                info.mColorTypeInfos = new ColorTypeInfo[1];
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBA_16161616;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGBA_16161616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_R16_SFLOAT
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR16F);
            info.init(logger, physDev, physProps, VK_FORMAT_R16_SFLOAT, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[2];
                // Format: R16F, Surface: kR_F16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_R_F16;
                    ctInfo.mTransferColorType = ColorInfo.CT_R_F16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
                // Format: R16F, Surface: kAlpha_F16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_ALPHA_F16;
                    ctInfo.mTransferColorType = ColorInfo.CT_ALPHA_F16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("000r");
                    ctInfo.mWriteSwizzle = Swizzle.make("a000");
                }
            }
        }

        // Format: VK_FORMAT_R16G16_SFLOAT
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG16F);
            info.init(logger, physDev, physProps, VK_FORMAT_R16G16_SFLOAT, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: GL_RG16F, Surface: kRG_F16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RG_F16;
                    ctInfo.mTransferColorType = ColorInfo.CT_RG_F16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_R16G16B16A16_SFLOAT
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA16F);
            info.init(logger, physDev, physProps, VK_FORMAT_R16G16B16A16_SFLOAT, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: VK_FORMAT_R8G8B8A8_UNORM, Surface: kRGBA_8888
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBA_F16;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGBA_F16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_B8G8R8A8_UNORM
        {
            FormatInfo info = getFormatInfo(Engine.ImageFormat.kBGRA8);
            info.init(logger, physDev, physProps, VK_FORMAT_B8G8R8A8_UNORM, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: BGRA8, Surface: kBGRA_8888
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_BGRA_8888;
                    ctInfo.mTransferColorType = ColorInfo.CT_BGRA_8888;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_R5G6B5_UNORM_PACK16
        {
            FormatInfo info = getFormatInfo(ImageFormat.kB5_G6_R5);
            info.init(logger, physDev, physProps, VK_FORMAT_R5G6B5_UNORM_PACK16, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: B5_G6_R5, Surface: kBGR_565
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_BGR_565;
                    ctInfo.mTransferColorType = ColorInfo.CT_BGR_565;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_A1R5G5B5_UNORM_PACK16
        {
            FormatInfo info = getFormatInfo(Engine.ImageFormat.kBGR5_A1);
            info.init(logger, physDev, physProps, VK_FORMAT_A1R5G5B5_UNORM_PACK16, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: BGR5_A1, Surface: kBGRA_5551
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_BGRA_5551;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_A2B10G10R10_UNORM_PACK32
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB10_A2);
            info.init(logger, physDev, physProps, VK_FORMAT_A2B10G10R10_UNORM_PACK32, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: RGB10_A2, Surface: kRGBA_1010102
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBA_1010102;
                    ctInfo.mTransferColorType = ColorInfo.CT_RGBA_1010102;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_A2R10G10B10_UNORM_PACK32
        {
            FormatInfo info = getFormatInfo(ImageFormat.kBGR10_A2);
            info.init(logger, physDev, physProps, VK_FORMAT_A2R10G10B10_UNORM_PACK32, stack);
            if (info.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: BGR10_A2, Surface: kBGRA_1010102
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_BGRA_1010102;
                    ctInfo.mTransferColorType = ColorInfo.CT_BGRA_1010102;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8_ETC2);
            info.init(logger, physDev, physProps, VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK, stack);
        }

        // Format: VK_FORMAT_BC1_RGB_UNORM_BLOCK
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8_BC1);
            info.init(logger, physDev, physProps, VK_FORMAT_BC1_RGB_UNORM_BLOCK, stack);
        }

        // Format: VK_FORMAT_BC1_RGBA_UNORM_BLOCK
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA8_BC1);
            info.init(logger, physDev, physProps, VK_FORMAT_BC1_RGBA_UNORM_BLOCK, stack);
        }

        setColorTypeFormat(ColorInfo.CT_R_8, ImageFormat.kR8);
        setColorTypeFormat(ColorInfo.CT_GRAY_8, ImageFormat.kR8);
        setColorTypeFormat(ColorInfo.CT_ALPHA_8, ImageFormat.kR8);
        setColorTypeFormat(ColorInfo.CT_RG_88, ImageFormat.kRG8);
        setColorTypeFormat(ColorInfo.CT_GRAY_ALPHA_88, ImageFormat.kRG8);
        setColorTypeFormat(ColorInfo.CT_RGB_888, ImageFormat.kRGB8);
        setColorTypeFormat(ColorInfo.CT_RGBA_8888, ImageFormat.kRGBA8);
        setColorTypeFormat(ColorInfo.CT_RGBX_8888, ImageFormat.kRGBA8);
        setColorTypeFormat(ColorInfo.CT_R_16, ImageFormat.kR16);
        setColorTypeFormat(ColorInfo.CT_ALPHA_16, ImageFormat.kR16);
        setColorTypeFormat(ColorInfo.CT_GRAY_16, ImageFormat.kR16);
        setColorTypeFormat(ColorInfo.CT_RG_1616, ImageFormat.kRG16);
        setColorTypeFormat(ColorInfo.CT_GRAY_ALPHA_1616, ImageFormat.kRG16);
        setColorTypeFormat(ColorInfo.CT_RGB_161616, ImageFormat.kRGB16);
        setColorTypeFormat(ColorInfo.CT_RGBA_16161616, ImageFormat.kRGBA16);
        setColorTypeFormat(ColorInfo.CT_R_F16, ImageFormat.kR16F);
        setColorTypeFormat(ColorInfo.CT_ALPHA_F16, ImageFormat.kR16F);
        setColorTypeFormat(ColorInfo.CT_RG_F16, ImageFormat.kRG16F);
        setColorTypeFormat(ColorInfo.CT_RGBA_F16, ImageFormat.kRGBA16F);
        setColorTypeFormat(ColorInfo.CT_BGRA_8888, ImageFormat.kBGRA8);
        setColorTypeFormat(ColorInfo.CT_BGR_565, ImageFormat.kB5_G6_R5);
        setColorTypeFormat(ColorInfo.CT_BGRA_5551, ImageFormat.kBGR5_A1);
        setColorTypeFormat(ColorInfo.CT_RGBA_1010102, ImageFormat.kRGB10_A2);
        setColorTypeFormat(ColorInfo.CT_BGRA_1010102, ImageFormat.kBGR10_A2);
    }

    private void initGLSL(VkPhysicalDeviceFeatures2 deviceFeatures2,
                          VkPhysicalDeviceFeatures features,
                          VkPhysicalDeviceLimits limits) {
        ShaderCaps shaderCaps = mShaderCaps;

        shaderCaps.mPreferFlatInterpolation = true;
        shaderCaps.mNoPerspectiveInterpolationSupport = true;
        shaderCaps.mVertexIDSupport = true;
        shaderCaps.mInfinitySupport = true;
        shaderCaps.mNonConstantArrayIndexSupport = true;
        shaderCaps.mBitManipulationSupport = true;
        shaderCaps.mFMASupport = true;
        shaderCaps.mTextureQueryLod = true;
        shaderCaps.mVaryingLocationSupport = true;
        shaderCaps.mUniformBindingSupport = true;
        shaderCaps.mUseBlockMemberOffset = true;
        shaderCaps.mUsePrecisionModifiers = true;
        shaderCaps.mDualSourceBlendingSupport = features.dualSrcBlend();
        shaderCaps.mMaxFragmentSamplers = (int) Math.min(Math.min(
                Integer.toUnsignedLong(limits.maxPerStageDescriptorSampledImages()),
                Integer.toUnsignedLong(limits.maxPerStageDescriptorSamplers())),
                Integer.MAX_VALUE);
    }

    FormatInfo getFormatInfo(int format) {
        return mFormatTable[format];
    }

    private void setColorTypeFormat(int colorType, int... formats) {
        for (int format : formats) {
            var info = getFormatInfo(format);
            for (var ctInfo : info.mColorTypeInfos) {
                if (ctInfo.mColorType == colorType) {
                    mColorTypeToFormat[colorType] = format;
                    return;
                }
            }
        }
    }

    public boolean hasUnifiedMemory() {
        return false;
    }

    @Override
    public boolean isFormatTexturable(int format) {
        return false;
    }

    @Override
    public int getMaxRenderTargetSampleCount(int format, boolean sampled) {
        return 0;
    }

    @Override
    public boolean isRenderableFormat(int format, int sampleCount, boolean sampled) {
        var info = getFormatInfo(format);
        for (var count : info.mColorSampleCounts) {
            if (count == sampleCount) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRenderTargetSampleCount(int sampleCount, int format, boolean sampled) {
        return 0;
    }

    @Override
    public int getSupportedWriteColorType(int surfaceColorType, ImageDesc dstDesc) {
        return surfaceColorType;
    }

    @Override
    protected long onSupportedReadColorType(int srcColorType, BackendFormat srcFormat, int dstColorType) {
        return 0;
    }

    @Nullable
    @Override
    public ImageDesc getDefaultColorImageDesc(int imageType,
                                              int colorType,
                                              int width, int height, int depthOrArraySize,
                                              int mipLevelCount, int sampleCount, int imageFlags) {
        if (width < 1 || height < 1 || depthOrArraySize < 1 ||
                mipLevelCount < 0 || sampleCount < 0) {
            return null;
        }
        sampleCount = Math.max(1, sampleCount);
        int format = mColorTypeToFormat[colorType];
        FormatInfo formatInfo = getFormatInfo(format);
        if ((imageFlags & ISurface.FLAG_SAMPLED_IMAGE) != 0 &&
                !formatInfo.isSampled(VK_IMAGE_TILING_OPTIMAL)) {
            return null;
        }
        if ((imageFlags & ISurface.FLAG_STORAGE_IMAGE) != 0 &&
                !formatInfo.isStorage(VK_IMAGE_TILING_OPTIMAL)) {
            return null;
        }
        if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0 &&
                !formatInfo.isRenderable(VK_IMAGE_TILING_OPTIMAL, sampleCount)) {
            return null;
        }
        int vkFormat = VKUtil.toVkFormat(format);

        //TODO

        final int depth;
        final int arraySize;
        switch (imageType) {
            case Engine.ImageType.k3D:
                depth = depthOrArraySize;
                arraySize = 1;
                break;
            case Engine.ImageType.k2DArray, Engine.ImageType.kCubeArray:
                depth = 1;
                arraySize = depthOrArraySize;
                break;
            default:
                depth = arraySize = 1;
                break;
        }

        if (width > mMaxTextureSize || height > mMaxTextureSize) {
            return null;
        }

        int maxMipLevels = DataUtils.computeMipLevelCount(width, height, depth);
        if (mipLevelCount == 0) {
            mipLevelCount = (imageFlags & ISurface.FLAG_MIPMAPPED) != 0
                    ? maxMipLevels
                    : 1; // only base level
        } else {
            mipLevelCount = Math.min(mipLevelCount, maxMipLevels);
        }

        if (sampleCount > 1 && mipLevelCount > 1) {
            return null;
        }

        int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        if ((imageFlags & ISurface.FLAG_SAMPLED_IMAGE) != 0) {
            usage |= VK_IMAGE_USAGE_SAMPLED_BIT;
        }
        if ((imageFlags & ISurface.FLAG_STORAGE_IMAGE) != 0) {
            usage |= VK_IMAGE_USAGE_STORAGE_BIT;
        }
        if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0) {
            usage |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                    VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            if ((imageFlags & ISurface.FLAG_MEMORYLESS) != 0) {
                usage |= VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
            }
        }

        return new VulkanImageDesc(
                0,
                VK_IMAGE_TYPE_2D,
                vkFormat,
                VK_IMAGE_TILING_OPTIMAL,
                usage,
                VK_SHARING_MODE_EXCLUSIVE,
                imageType, format,
                width, height, depth, arraySize,
                mipLevelCount, sampleCount, imageFlags
        );
    }

    //TODO validation
    @Override
    public @Nullable ImageDesc getDefaultDepthStencilImageDesc(int depthBits, int stencilBits,
                                                               int width, int height,
                                                               int sampleCount, int imageFlags) {
        if (depthBits < 0 || depthBits > 32) {
            return null;
        }
        if (stencilBits < 0 || stencilBits > 8) {
            return null;
        }
        if (depthBits == 0 && stencilBits == 0) {
            return null;
        }
        int depthStencilFormat;
        int viewFormat;
        if (stencilBits > 0) {
            if (depthBits <= 24) {
                depthStencilFormat = VK_FORMAT_D24_UNORM_S8_UINT;
                viewFormat = ImageFormat.kD24_S8;
            } else {
                depthStencilFormat = VK_FORMAT_D32_SFLOAT_S8_UINT;
                viewFormat = ImageFormat.kD32F_S8;
            }
        } else {
            if (depthBits <= 16) {
                depthStencilFormat = VK_FORMAT_D16_UNORM;
                viewFormat = ImageFormat.kD16;
            } else if (depthBits <= 24) {
                depthStencilFormat = VK_FORMAT_X8_D24_UNORM_PACK32;
                viewFormat = ImageFormat.kD24;
            } else {
                depthStencilFormat = VK_FORMAT_D32_SFLOAT;
                viewFormat = ImageFormat.kD32F;
            }
        }

        int usage = 0;
        if ((imageFlags & ISurface.FLAG_SAMPLED_IMAGE) != 0) {
            usage |= VK_IMAGE_USAGE_SAMPLED_BIT;
        }
        if ((imageFlags & ISurface.FLAG_STORAGE_IMAGE) != 0) {
            usage |= VK_IMAGE_USAGE_STORAGE_BIT;
        }
        if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0) {
            usage |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
            /*if ((imageFlags & ISurface.FLAG_MEMORYLESS) != 0) {
                usage |= VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
            }*/
        }

        return new VulkanImageDesc(
                0,
                VK_IMAGE_TYPE_2D,
                depthStencilFormat,
                VK_IMAGE_TILING_OPTIMAL,
                usage,
                VK_SHARING_MODE_EXCLUSIVE,
                Engine.ImageType.k2D, viewFormat,
                width, height, 1, 1,
                1, sampleCount, imageFlags
        );
    }

    @Nullable
    @Override
    protected BackendFormat onGetDefaultBackendFormat(int colorType) {
        return null;
    }

    @Nullable
    @Override
    public BackendFormat getCompressedBackendFormat(int compressionType) {
        return null;
    }

    @NonNull
    @Override
    public PipelineKey makeGraphicsPipelineKey(PipelineKey old, PipelineDesc pipelineDesc, RenderPassDesc renderPassDesc) {
        VulkanGraphicsPipelineKey pipelineKey;
        if (old instanceof VulkanGraphicsPipelineKey) {
            pipelineKey = (VulkanGraphicsPipelineKey) old;
        } else {
            pipelineKey = new VulkanGraphicsPipelineKey();
        }
        pipelineKey.mPipelineDesc = pipelineDesc;
        pipelineKey.mCompatibleRenderPassKey.update(renderPassDesc);
        return pipelineKey;
    }

    @Override
    public @Nullable ColorTypeInfo getColorTypeInfo(int colorType, @NonNull ImageDesc desc) {
        return getColorTypeInfo(colorType, desc.getViewFormat());
    }

    @Override
    public @Nullable ColorTypeInfo getColorTypeInfo(int colorType, int format) {
        final FormatInfo formatInfo = getFormatInfo(format);
        for (final ColorTypeInfo ctInfo : formatInfo.mColorTypeInfos) {
            if (ctInfo.mColorType == colorType) {
                return ctInfo;
            }
        }
        return null;
    }

    @Override
    public IResourceKey computeImageKey(ImageDesc desc, IResourceKey recycle) {
        if (desc instanceof VulkanImageDesc vulkanDesc) {
            return new VulkanImage.ResourceKey(vulkanDesc);
        }
        return null;
    }

    @Override
    public void dump(StringBuilder out, boolean includeFormatTable) {
        super.dump(out, includeFormatTable);

        out.append("APIVersion: ").append(apiVersionToString(mAPIVersion)).append('\n');
        out.append("DriverVersion: ").append(switch (mVendorID) {
            case kNVIDIA_VendorID -> String.format("%d.%d.%d.%d",
                    mDriverVersion >>> 22,
                    (mDriverVersion >>> 14) & 0xFF,
                    (mDriverVersion >> 6) & 0xFF,
                    mDriverVersion & 0x3F);
            default -> "0x" + Integer.toHexString(mDriverVersion);
        }).append('\n');
        out.append("VendorID: ").append(String.format("0x%X (%s)", mVendorID, getVendorIDName(mVendorID))).append('\n');
        out.append("DeviceID: ").append(String.format("0x%X", mDeviceID)).append('\n');
        out.append("DeviceType: ").append(getPhysicalDeviceTypeName(mDeviceType)).append('\n');
        out.append("DeviceName: ").append(mDeviceName).append('\n');
        out.append("PipelineCacheUUID: ");
        for (byte b : mPipelineCacheUUID) {
            out.append(String.format("%02X", b));
        }
        out.append('\n');

        out.append("MaxSamplerAnisotropy: ").append(mMaxSamplerAnisotropy).append('\n');

        out.append("ColorTypeToFormat:\n");
        for (int i = 0; i < mColorTypeToFormat.length; i++) {
            out.append('\t').append(ColorInfo.colorTypeToString(i))
                    .append("=>").append(ImageFormat.toString(mColorTypeToFormat[i])).append('\n');
        }

        if (includeFormatTable) {
            out.append("FormatTable:\n");
            for (int i = 1; i < mFormatTable.length; i++) {
                out.append('\t').append(ImageFormat.toString(i))
                        .append("=>\n");
                mFormatTable[i].dump("\t\t", out);
            }
        }
    }

    static int[] initSampleCounts(Logger logger,
                                  VkPhysicalDevice physDev,
                                  VkPhysicalDeviceProperties physProps,
                                  int format,
                                  int usage,
                                  MemoryStack stack) {
        stack.push();
        try {
            VkImageFormatProperties props = VkImageFormatProperties.malloc(stack);
            // when requesting MSAA support, we only consider 2D and Optimal
            int result = vkGetPhysicalDeviceImageFormatProperties(
                    physDev,
                    format,
                    VK_IMAGE_TYPE_2D,
                    VK_IMAGE_TILING_OPTIMAL,
                    usage,
                    0,
                    props
            );
            if (result != VK_SUCCESS) {
                logger.warn(MARKER, "Failed to vkGetPhysicalDeviceImageFormatProperties: {}",
                        VKUtil.getResultMessage(result));
                return IntArrays.EMPTY_ARRAY;
            }
            IntArrayList sampleCounts = new IntArrayList(5); // [1, 2, 4, 8, 16]
            int flags = props.sampleCounts();
            if ((flags & VK_SAMPLE_COUNT_1_BIT) != 0) {
                sampleCounts.add(1);
            }
            if ((flags & VK_SAMPLE_COUNT_2_BIT) != 0) {
                sampleCounts.add(2);
            }
            if ((flags & VK_SAMPLE_COUNT_4_BIT) != 0) {
                sampleCounts.add(4);
            }
            if ((flags & VK_SAMPLE_COUNT_8_BIT) != 0) {
                sampleCounts.add(8);
            }
            if ((flags & VK_SAMPLE_COUNT_16_BIT) != 0) {
                sampleCounts.add(16);
            }
            return sampleCounts.toIntArray();
        } finally {
            stack.pop();
        }
    }

    static class FormatInfo {

        /*VkFormatFeatureFlags*/ int mOptimalTilingFeatures = 0;
        /*VkFormatFeatureFlags*/ int mLinearTilingFeatures = 0;

        int[] mColorSampleCounts = IntArrays.EMPTY_ARRAY;

        ColorTypeInfo[] mColorTypeInfos = {};

        void init(Logger logger,
                  VkPhysicalDevice physDev,
                  VkPhysicalDeviceProperties physProps,
                  int format,
                  MemoryStack stack) {
            stack.push();
            try {
                VkFormatProperties props = VkFormatProperties.calloc(stack);
                vkGetPhysicalDeviceFormatProperties(
                        physDev,
                        format,
                        props
                );
                mOptimalTilingFeatures = props.optimalTilingFeatures();
                mLinearTilingFeatures = props.linearTilingFeatures();

                if ((mOptimalTilingFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT) != 0) {
                    // We make all renderable images support being used as input attachment
                    int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                            VK_IMAGE_USAGE_TRANSFER_DST_BIT |
                            VK_IMAGE_USAGE_SAMPLED_BIT |
                            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                            VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
                    mColorSampleCounts = initSampleCounts(logger,
                            physDev,
                            physProps,
                            format,
                            usage,
                            stack);
                }
            } finally {
                stack.pop();
            }
        }

        boolean isSampled(int imageTiling) {
            return switch (imageTiling) {
                case VK_IMAGE_TILING_OPTIMAL -> (mOptimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0;
                case VK_IMAGE_TILING_LINEAR -> (mLinearTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0;
                default -> false;
            };
        }

        boolean isStorage(int imageTiling) {
            return switch (imageTiling) {
                case VK_IMAGE_TILING_OPTIMAL -> (mOptimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0;
                case VK_IMAGE_TILING_LINEAR -> (mLinearTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0;
                default -> false;
            };
        }

        boolean isFilterable(int imageTiling) {
            return switch (imageTiling) {
                case VK_IMAGE_TILING_OPTIMAL ->
                        (mOptimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0;
                case VK_IMAGE_TILING_LINEAR ->
                        (mLinearTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0;
                default -> false;
            };
        }

        boolean isRenderable(int imageTiling, int sampleCount) {
            if (imageTiling == VK_IMAGE_TILING_OPTIMAL) {
                if (mColorSampleCounts.length == 0) {
                    return false;
                }
                return sampleCount <= mColorSampleCounts[mColorSampleCounts.length - 1];
            }
            return false;
        }

        boolean isTransferSrc(int imageTiling) {
            return switch (imageTiling) {
                case VK_IMAGE_TILING_OPTIMAL -> (mOptimalTilingFeatures & VK_FORMAT_FEATURE_TRANSFER_SRC_BIT) != 0;
                case VK_IMAGE_TILING_LINEAR -> (mLinearTilingFeatures & VK_FORMAT_FEATURE_TRANSFER_SRC_BIT) != 0;
                default -> false;
            };
        }

        boolean isTransferDst(int imageTiling) {
            return switch (imageTiling) {
                case VK_IMAGE_TILING_OPTIMAL -> (mOptimalTilingFeatures & VK_FORMAT_FEATURE_TRANSFER_DST_BIT) != 0;
                case VK_IMAGE_TILING_LINEAR -> (mLinearTilingFeatures & VK_FORMAT_FEATURE_TRANSFER_DST_BIT) != 0;
                default -> false;
            };
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("FormatInfo:\n");
            dump("", b);
            return b.toString();
        }

        void dump(String prefix, StringBuilder out) {
            out.append(prefix).append("OptimalTilingFeatures: 0x").append(Integer.toHexString(mOptimalTilingFeatures)).append('\n');
            out.append(prefix).append("LinearTilingFeatures: 0x").append(Integer.toHexString(mLinearTilingFeatures)).append('\n');
            out.append(prefix).append("ColorSampleCounts: ").append(Arrays.toString(mColorSampleCounts)).append('\n');
            for (int i = 0; i < mColorTypeInfos.length; i++) {
                out.append(prefix).append("ColorTypeInfo[").append(i).append("]:\n");
                mColorTypeInfos[i].dump(prefix + "\t", out);
            }
        }
    }
}

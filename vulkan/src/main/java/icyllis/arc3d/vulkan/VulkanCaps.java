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

import static org.lwjgl.vulkan.VK11.*;

public class VulkanCaps extends Caps {

    // the minimum value of 'maxBoundDescriptorSets' required by VkSpec
    public static final int MAX_BOUND_SETS = 4;

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

        logger.info("Physical device version: {}.{}.{}",
                VK_VERSION_MAJOR(physicalDeviceVersion),
                VK_VERSION_MINOR(physicalDeviceVersion),
                VK_VERSION_PATCH(physicalDeviceVersion));

        try (var stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties physProps = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(physDev, physProps);
            VkPhysicalDeviceLimits limits = physProps.limits();

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

            mMaxVertexAttributes = limits.maxVertexInputAttributes();
            mMaxVertexBindings = limits.maxVertexInputBindings();

            mMaxTextureSize = (int) Math.min(
                    Integer.toUnsignedLong(limits.maxImageDimension2D()), Integer.MAX_VALUE);
            assert mMaxTextureSize >= 4096;

            mMaxPushConstantsSize = limits.maxPushConstantsSize();
            // our attachment points are consistent with draw buffers
            mMaxColorAttachments = Math.min(Math.min(
                            limits.maxFragmentOutputAttachments(),
                            limits.maxColorAttachments()),
                    MAX_COLOR_TARGETS);
            assert mMaxColorAttachments >= 4;

            mMinUniformBufferOffsetAlignment = (int) limits.minUniformBufferOffsetAlignment();
            mMinStorageBufferOffsetAlignment = (int) limits.minStorageBufferOffsetAlignment();
            // many drivers report 1 but actually trigger slow path, use 4 at least.
            // actually used alignment is generally aligned up to a higher value.
            mOptimalBufferCopyOffsetAlignment = Math.max((int) limits.optimalBufferCopyOffsetAlignment(), 4);
            mOptimalBufferCopyRowBytesAlignment = Math.max((int) limits.optimalBufferCopyRowPitchAlignment(), 4);

            initFormatTable(logger, physDev, physProps, stack);

            initGLSL();
        }
    }

    void initFormatTable(Logger logger,
                         VkPhysicalDevice physDev,
                         VkPhysicalDeviceProperties physProps,
                         MemoryStack stack) {
        for (int i = 0; i < mFormatTable.length; i++) {
            mFormatTable[i] = new FormatInfo();
        }

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

        setColorTypeFormat(ColorInfo.CT_R_8, ImageFormat.kR8);
        setColorTypeFormat(ColorInfo.CT_RGBA_8888, ImageFormat.kRGBA8);
        setColorTypeFormat(ColorInfo.CT_RGBA_F16, ImageFormat.kRGBA16F);
    }

    private void initGLSL() {
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
                logger.warn("Failed to vkGetPhysicalDeviceImageFormatProperties: {}",
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
            return "FormatInfo{" +
                    "optimalTilingFeatures=0x" + Integer.toHexString(mOptimalTilingFeatures) +
                    ", linearTilingFeatures=0x" + Integer.toHexString(mLinearTilingFeatures) +
                    ", colorSampleCounts=" + Arrays.toString(mColorSampleCounts) +
                    ", colorTypeInfos=" + Arrays.toString(mColorTypeInfos) +
                    '}';
        }
    }
}

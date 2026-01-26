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

package icyllis.arc3d.opengl;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.engine.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import icyllis.arc3d.engine.Engine.ImageFormat;

import java.util.*;

import static org.lwjgl.opengl.EXTTextureCompressionS3TC.*;
import static org.lwjgl.opengl.GL43C.*;

/**
 * Stores some capabilities of an OpenGL device.
 * <p>
 * OpenGL 3.3 or OpenGL ES 3.0 is the minimum requirement.
 */
public abstract class GLCaps extends Caps {

    GLUtil.GLVendor mVendor;
    GLUtil.GLDriver mDriver;

    int mMaxFragmentUniformVectors;
    float mMaxTextureMaxAnisotropy = 1.f;
    boolean mSupportsProtected = false;
    boolean mSkipErrorChecks = false;
    int mMaxLabelLength;

    boolean mDebugSupport;

    boolean mBufferStorageSupport;

    boolean mDrawElementsBaseVertexSupport;
    boolean mBaseInstanceSupport;
    boolean mVertexAttribBindingSupport;
    boolean mProgramBinarySupport;
    boolean mProgramParameterSupport;
    boolean mCopyImageSupport;
    boolean mDSASupport;
    boolean mSPIRVSupport = false;
    boolean mViewCompatibilityClassSupport = false;
    boolean mTexStorageSupport;
    boolean mInvalidateFramebufferSupport;
    boolean mShaderBinarySupport;
    boolean mUseStagingBuffers = false;
    final boolean mVolatileContext;
    boolean mMustHaveFragmentShader;

    int[] mProgramBinaryFormats;

    public static final int
            INVALIDATE_BUFFER_TYPE_NULL_DATA = 1,
            INVALIDATE_BUFFER_TYPE_INVALIDATE = 2;
    int mInvalidateBufferType;
    int mGLSLVersion;

    /**
     * OpenGL texture format table.
     */
    //TODO finally move this to base class
    final FormatInfo[] mFormatTable =
            new FormatInfo[ImageFormat.kLastColor + 1];

    /**
     * Map {@link ColorInfo}.CT_XXX to {@link ImageFormat}.
     * May contain {@link ImageFormat#kUnsupported}.
     */
    private final int[] mColorTypeToFormat =
            new int[ColorInfo.CT_COUNT];
    private final GLBackendFormat[] mCompressionTypeToBackendFormat =
            new GLBackendFormat[ColorInfo.COMPRESSION_COUNT];

    GLCaps(ContextOptions options) {
        super(options);
        mVolatileContext = options.mVolatileContext;
        // we currently don't use ARB_clip_control
        //TODO we need to make this a context option,
        // zero to one and reversed-Z is helpful in 3D rendering
        mDepthClipNegativeOneToOne = true;
    }

    void initFormatTable(boolean texStorageSupported,
                         boolean ARB_ES2_compatibility,
                         boolean EXT_texture_norm16,
                         boolean EXT_texture_compression_s3tc) {
        final int nonMSAARenderFlags = FormatInfo.COLOR_ATTACHMENT_FLAG;
        final int msaaRenderFlags = nonMSAARenderFlags | FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG;
        final boolean isGLES = isGLES();

        for (int i = 0; i < mFormatTable.length; i++) {
            mFormatTable[i] = new FormatInfo();
        }

        // Format: R8
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR8);
            info.mInternalFormatForRenderbuffer = GL_R8;

            info.mDefaultExternalFormat = GL_RED;
            info.mDefaultExternalType = GL_UNSIGNED_BYTE;
            info.mExternalType = GL_UNSIGNED_BYTE;
            info.mExternalTexImageFormat = GL_RED;
            info.mExternalReadFormat = GL_RED;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_R_8;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_R8;

            info.mColorTypeInfos = new ColorTypeInfo[3];
            // Format: R8, Surface: kR_8
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_R_8;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }

            // Format: R8, Surface: kAlpha_8
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_ALPHA_8;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                ctInfo.mReadSwizzle = Swizzle.make("000r");
                ctInfo.mWriteSwizzle = Swizzle.make("a000");
            }

            // Format: R8, Surface: kGray_8
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[2] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_GRAY_8;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                ctInfo.mReadSwizzle = Swizzle.make("rrr1");
            }
        }

        // Format: RG8
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG8);
            info.mInternalFormatForRenderbuffer = GL_RG8;

            info.mDefaultExternalFormat = GL_RG;
            info.mDefaultExternalType = GL_UNSIGNED_BYTE;
            info.mExternalType = GL_UNSIGNED_BYTE;
            info.mExternalTexImageFormat = GL_RG;
            info.mExternalReadFormat = GL_RG;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RG_88;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RG8;

            info.mColorTypeInfos = new ColorTypeInfo[2];
            // Format: RG8, Surface: kRG_88
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RG_88;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }

            // Added by Arc3D, this is useful for grayscale PNG image rendering.
            // Format: RG8, Surface: kGrayAlpha_88
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_GRAY_ALPHA_88;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                ctInfo.mReadSwizzle = Swizzle.make("rrrg");
            }
        }

        // Format: RGB8
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8);
            info.mInternalFormatForRenderbuffer = GL_RGB8;

            info.mDefaultExternalFormat = GL_RGB;
            info.mDefaultExternalType = GL_UNSIGNED_BYTE;
            info.mExternalType = GL_UNSIGNED_BYTE;
            info.mExternalTexImageFormat = GL_RGB;
            info.mExternalReadFormat = GL_RGB;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RGB_888;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            // OpenGL ES requires RGB8 to be a color-renderable format,
            // but OpenGL does not require it to be a renderbuffer-compatible format
            // this flag only allows it to be used as a framebuffer attachment
            if (isGLES) {
                info.mFlags |= msaaRenderFlags;
            } else {
                info.mFlags |= nonMSAARenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGB8;

            info.mColorTypeInfos = new ColorTypeInfo[1];
            // Format: RGB8, Surface: kRGB_888
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RGB_888;
                // disallow rendering to this format
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
            }
        }

        // Format: RGBA8
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA8);
            info.mInternalFormatForRenderbuffer = GL_RGBA8;

            info.mDefaultExternalFormat = GL_RGBA;
            info.mDefaultExternalType = GL_UNSIGNED_BYTE;
            info.mExternalType = GL_UNSIGNED_BYTE;
            info.mExternalTexImageFormat = GL_RGBA;
            info.mExternalReadFormat = GL_RGBA;
            info.mRequiresImplementationReadQuery = false;

            info.mDefaultColorType = ColorInfo.CT_RGBA_8888;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGBA8;

            info.mColorTypeInfos = new ColorTypeInfo[2];
            // Format: RGBA8, Surface: kRGBA_8888
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RGBA_8888;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }

            // Format: RGBA8, Surface: kRGBX_8888
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RGBX_8888;
                // disallow rendering to this format
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                ctInfo.mReadSwizzle = Swizzle.RGB1;
            }
        }

        // Format: R16
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR16);
            info.mInternalFormatForRenderbuffer = GL_R16;

            info.mDefaultExternalFormat = GL_RED;
            info.mDefaultExternalType = GL_UNSIGNED_SHORT;
            info.mExternalType = GL_UNSIGNED_SHORT;
            info.mExternalTexImageFormat = GL_RED;
            info.mExternalReadFormat = GL_RED;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_R_16;
            if (EXT_texture_norm16) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
                info.mFlags |= msaaRenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_R16;

            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0) {
                info.mColorTypeInfos = new ColorTypeInfo[3];

                // Format: R16, Surface: kR_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_R_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Format: R16, Surface: kAlpha_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_ALPHA_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("000r");
                    ctInfo.mWriteSwizzle = Swizzle.make("a000");
                }

                // Format: R16, Surface: kGray_16
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[2] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_16;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrr1");
                }
            }
        }

        // Format: RG16
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG16);
            info.mInternalFormatForRenderbuffer = GL_RG16;

            info.mDefaultExternalFormat = GL_RG;
            info.mDefaultExternalType = GL_UNSIGNED_SHORT;
            info.mExternalType = GL_UNSIGNED_SHORT;
            info.mExternalTexImageFormat = GL_RG;
            info.mExternalReadFormat = GL_RG;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RG_1616;
            if (EXT_texture_norm16) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
                info.mFlags |= msaaRenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RG16;

            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0) {
                info.mColorTypeInfos = new ColorTypeInfo[2];
                // Format: RG16, Surface: kRG_1616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RG_1616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }

                // Added by Arc3D, this is useful for grayscale PNG image rendering.
                // Format: RG16, Surface: kGrayAlpha_1616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_GRAY_ALPHA_1616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                    ctInfo.mReadSwizzle = Swizzle.make("rrrg");
                }
            }
        }

        // Format: RGB16
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB16);
            info.mInternalFormatForRenderbuffer = GL_RGB16;

            info.mDefaultExternalFormat = GL_RGB;
            info.mDefaultExternalType = GL_UNSIGNED_SHORT;
            info.mExternalType = GL_UNSIGNED_SHORT;
            info.mExternalTexImageFormat = GL_RGB;
            info.mExternalReadFormat = GL_RGB;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RGB_161616;
            if (EXT_texture_norm16) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
                // Neither OpenGL nor OpenGL ES guarantees compatibility with the renderbuffer,
                // but textures in this format can be used as framebuffer attachments.
                info.mFlags |= nonMSAARenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGB16;

            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: RGB16, Surface: kRGB_161616
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGB_161616;
                    // disallow rendering to this format
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag;
                }
            }
        }

        // Format: RGBA16
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA16);
            info.mInternalFormatForRenderbuffer = GL_RGBA16;

            info.mDefaultExternalFormat = GL_RGBA;
            info.mDefaultExternalType = GL_UNSIGNED_SHORT;
            info.mExternalType = GL_UNSIGNED_SHORT;
            info.mExternalTexImageFormat = GL_RGBA;
            info.mExternalReadFormat = GL_RGBA;
            // supported by EXT_texture_norm16
            info.mRequiresImplementationReadQuery = false;

            info.mDefaultColorType = ColorInfo.CT_RGBA_16161616;
            if (EXT_texture_norm16) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
                info.mFlags |= msaaRenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGBA16;

            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0) {
                // Format: GL_RGBA16, Surface: kRGBA_16161616
                info.mColorTypeInfos = new ColorTypeInfo[1];
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_RGBA_16161616;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: R16F
        {
            FormatInfo info = getFormatInfo(ImageFormat.kR16F);
            info.mInternalFormatForRenderbuffer = GL_R16F;

            info.mDefaultExternalFormat = GL_RED;
            info.mDefaultExternalType = GL_HALF_FLOAT;
            info.mExternalType = GL_HALF_FLOAT;
            info.mExternalTexImageFormat = GL_RED;
            info.mExternalReadFormat = GL_RED;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_R_F16;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            // subclass sets color renderable info
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_R16F;

            info.mColorTypeInfos = new ColorTypeInfo[2];
            // Format: R16F, Surface: kR_F16
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_R_F16;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }
            // Format: R16F, Surface: kAlpha_F16
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[1] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_ALPHA_F16;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                ctInfo.mReadSwizzle = Swizzle.make("000r");
                ctInfo.mWriteSwizzle = Swizzle.make("a000");
            }
        }

        // Format:RG16F
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRG16F);
            info.mInternalFormatForRenderbuffer = GL_RG16F;

            info.mDefaultExternalFormat = GL_RG;
            info.mDefaultExternalType = GL_HALF_FLOAT;
            info.mExternalType = GL_HALF_FLOAT;
            info.mExternalTexImageFormat = GL_RG;
            info.mExternalReadFormat = GL_RG;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RG_F16;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            // subclass sets color renderable info
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RG16F;

            info.mColorTypeInfos = new ColorTypeInfo[1];
            // Format: GL_RG16F, Surface: kRG_F16
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RG_F16;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }
        }

        // Format: RGBA16F
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA16F);
            info.mInternalFormatForRenderbuffer = GL_RGBA16F;

            info.mDefaultExternalFormat = GL_RGBA;
            info.mDefaultExternalType = GL_HALF_FLOAT;
            info.mExternalType = GL_HALF_FLOAT;
            info.mExternalTexImageFormat = GL_RGBA;
            info.mExternalReadFormat = GL_RGBA;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_RGBA_F16;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            // subclass sets color renderable info
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGBA16F;

            info.mColorTypeInfos = new ColorTypeInfo[1];
            // Format: RGBA16F, Surface: kRGBA_F16
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RGBA_F16;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }
        }

        // Format: B5_G6_R5
        {
            FormatInfo info = getFormatInfo(ImageFormat.kB5_G6_R5);
            info.mInternalFormatForRenderbuffer = GL_RGB565;

            info.mDefaultExternalFormat = GL_RGB;
            info.mDefaultExternalType = GL_UNSIGNED_SHORT_5_6_5;
            info.mExternalType = GL_UNSIGNED_SHORT_5_6_5;
            info.mExternalTexImageFormat = GL_RGB;
            info.mExternalReadFormat = GL_RGB;
            // Not guaranteed by ES
            info.mRequiresImplementationReadQuery = isGLES;

            info.mDefaultColorType = ColorInfo.CT_BGR_565;
            if (ARB_ES2_compatibility) {
                // macOS supports this
                info.mFlags |= FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
                info.mFlags |= msaaRenderFlags;
            }
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGB565;

            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0) {
                info.mColorTypeInfos = new ColorTypeInfo[1];
                // Format: B5_G6_R5, Surface: kBGR_565
                {
                    ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                    ctInfo.mColorType = ColorInfo.CT_BGR_565;
                    ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
                }
            }
        }

        // Format: RGB10_A2
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB10_A2);
            info.mInternalFormatForRenderbuffer = GL_RGB10_A2;

            info.mDefaultExternalFormat = GL_RGBA;
            info.mDefaultExternalType = GL_UNSIGNED_INT_2_10_10_10_REV;
            info.mExternalType = GL_UNSIGNED_INT_2_10_10_10_REV;
            info.mExternalTexImageFormat = GL_RGBA;
            info.mExternalReadFormat = GL_RGBA;
            // this is supported by ES 3.0
            info.mRequiresImplementationReadQuery = false;

            info.mDefaultColorType = ColorInfo.CT_RGBA_1010102;
            info.mFlags = FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
            if (texStorageSupported) {
                info.mFlags |= FormatInfo.TEXTURE_STORAGE_FLAG;
            }
            info.mInternalFormatForTexture = GL_RGB10_A2;

            info.mColorTypeInfos = new ColorTypeInfo[1];
            // Format: RGB10_A2, Surface: kRGBA_1010102
            {
                ColorTypeInfo ctInfo = info.mColorTypeInfos[0] = new ColorTypeInfo();
                ctInfo.mColorType = ColorInfo.CT_RGBA_1010102;
                ctInfo.mFlags = ColorTypeInfo.kUploadData_Flag | ColorTypeInfo.kRenderable_Flag;
            }
        }

        // Format: COMPRESSED_RGB8_ETC2
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8_ETC2);
            info.mInternalFormatForTexture = GL_COMPRESSED_RGB8_ETC2;

            mCompressionTypeToBackendFormat[ColorInfo.COMPRESSION_ETC2_RGB8_UNORM] =
                    GLBackendFormat.make(GL_COMPRESSED_RGB8_ETC2);

            // There are no support ColorTypes for this format
        }

        // Format: COMPRESSED_RGB8_BC1
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGB8_BC1);
            info.mInternalFormatForTexture = GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
            if (EXT_texture_compression_s3tc) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG;

                mCompressionTypeToBackendFormat[ColorInfo.COMPRESSION_BC1_RGB8_UNORM] =
                        GLBackendFormat.make(GL_COMPRESSED_RGB_S3TC_DXT1_EXT);
            }

            // There are no support ColorTypes for this format
        }

        // Format: COMPRESSED_RGBA8_BC1
        {
            FormatInfo info = getFormatInfo(ImageFormat.kRGBA8_BC1);
            info.mInternalFormatForTexture = GL_COMPRESSED_RGBA_S3TC_DXT1_EXT;
            if (EXT_texture_compression_s3tc) {
                info.mFlags = FormatInfo.TEXTURABLE_FLAG;

                mCompressionTypeToBackendFormat[ColorInfo.COMPRESSION_BC1_RGBA8_UNORM] =
                        GLBackendFormat.make(GL_COMPRESSED_RGBA_S3TC_DXT1_EXT);
            }

            // There are no support ColorTypes for this format
        }
    }

    void initColorTypeFormatTable() {
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

        //TODO F32 RGBE
    }

    boolean validateFormatTable() {
        // Validate, skip UNKNOWN
        for (int index = 1; index < mFormatTable.length; ++index) {
            FormatInfo info = mFormatTable[index];
            // Make sure we didn't set fbo attachable with msaa and not fbo attachable
            if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG) != 0 &&
                    (info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) == 0) {
                assert false;
            }
            // Make sure all renderbuffer formats can also be texture formats
            if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0 &&
                    (info.mFlags & FormatInfo.TEXTURABLE_FLAG) == 0) {
                assert false;
            }

            // All texturable format should have their internal formats
            if ((info.mFlags & FormatInfo.TEXTURABLE_FLAG) != 0 &&
                    info.mInternalFormatForTexture == 0) {
                assert false;
            }

            // All renderable format should have their internal formats
            if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0 &&
                    info.mInternalFormatForRenderbuffer == 0) {
                assert false;
            }

            // Make sure if we added a ColorTypeInfo we filled it out
            for (ColorTypeInfo ctInfo : info.mColorTypeInfos) {
                if (ctInfo.mColorType == ColorInfo.CT_UNKNOWN) {
                    assert false;
                }
                // Seems silly to add a color type if we don't support any flags on it
                if (ctInfo.mFlags == 0) {
                    assert false;
                }
            }
        }
        return true;
    }

    void applyDriverWorkaround() {
        var workarounds = mDriverBugWorkarounds;
    }

    FormatInfo getFormatInfo(int format) {
        return mFormatTable[format];
    }

    private void setColorTypeFormat(int colorType, int format) {
        var info = getFormatInfo(format);
        for (var ctInfo : info.mColorTypeInfos) {
            if (ctInfo.mColorType == colorType) {
                mColorTypeToFormat[colorType] = format;
                return;
            }
        }
    }

    public GLUtil.GLVendor getVendor() {
        return mVendor;
    }

    public GLUtil.GLDriver getDriver() {
        return mDriver;
    }

    /**
     * Returns true if OpenGL ES (embedded system),
     * returns false if OpenGL (desktop, core profile).
     */
    public abstract boolean isGLES();

    /**
     * Modern OpenGL means OpenGL 4.5 is supported, so all of the following are supported:
     * <ul>
     *     <li>ARB_ES2_compatibility</li>
     *     <li>ARB_get_program_binary</li>
     *     <li>ARB_base_instance</li>
     *     <li>ARB_texture_storage</li>
     *     <li>ARB_internalformat_query</li>
     *     <li>ARB_shading_language_420pack</li>
     *     <li>ARB_invalidate_subdata</li>
     *     <li>ARB_explicit_uniform_location</li>
     *     <li>ARB_vertex_attrib_binding</li>
     *     <li>ARB_ES3_compatibility</li>
     *     <li>ARB_clear_texture</li>
     *     <li>ARB_buffer_storage</li>
     *     <li>ARB_enhanced_layouts</li>
     *     <li>ARB_texture_barrier</li>
     *     <li>ARB_direct_state_access</li>
     * </ul>
     * Arc3D requires OpenGL 3.3 at least.
     */
    public boolean hasDSASupport() {
        return mDSASupport;
    }

    public int getInvalidateBufferType() {
        return mInvalidateBufferType;
    }

    public boolean hasDebugSupport() {
        return mDebugSupport;
    }

    public boolean hasDrawElementsBaseVertexSupport() {
        return mDrawElementsBaseVertexSupport;
    }

    public boolean hasBaseInstanceSupport() {
        return mBaseInstanceSupport;
    }

    public boolean hasVertexAttribBindingSupport() {
        return mVertexAttribBindingSupport;
    }

    public boolean hasCopyImageSupport() {
        return mCopyImageSupport;
    }

    public boolean hasBufferStorageSupport() {
        return mBufferStorageSupport;
    }

    public boolean hasSPIRVSupport() {
        return mSPIRVSupport;
    }

    public boolean hasProgramBinarySupport() {
        return mProgramBinarySupport;
    }

    public boolean useStagingBuffers() {
        return mUseStagingBuffers;
    }

    public boolean mustHaveFragmentShader() {
        return mMustHaveFragmentShader;
    }

    public boolean hasVolatileContext() {
        return mVolatileContext;
    }

    public boolean hasInvalidateFramebufferSupport() {
        return mInvalidateFramebufferSupport;
    }

    public boolean hasShaderBinarySupport() {
        return mShaderBinarySupport;
    }

    public int @Nullable[] getProgramBinaryFormats() {
        return mProgramBinarySupport ? mProgramBinaryFormats.clone() : null;
    }

    /**
     * Returns the raw GLSL version that supported by the OpenGL device.
     * This value is derived from reported GL_SHADING_LANGUAGE_VERSION string.
     * May return 300, 310, 320 for es profile, 330 or above for core profile.
     * <p>
     * The effective GLSL version that used by our pipeline and shader builder
     * is {@link ShaderCaps#mGLSLVersion}.
     */
    public int getGLSLVersion() {
        return mGLSLVersion;
    }

    @Override
    public boolean isFormatTexturable(int format) {
        return (getFormatInfo(format).mFlags & FormatInfo.TEXTURABLE_FLAG) != 0;
    }

    public boolean isTextureStorageCompatible(int format) {
        return (getFormatInfo(format).mFlags & FormatInfo.TEXTURE_STORAGE_FLAG) != 0;
    }

    @Override
    public int getMaxRenderTargetSampleCount(int format, boolean sampled) {
        int[] table = getFormatInfo(format).mColorSampleCounts;
        if (table.length == 0) {
            return 0;
        }
        if (sampled) {
            //TODO MS Texture
            return 1;
        }
        return table[table.length - 1];
    }

    @Override
    public boolean isRenderableFormat(int format, int sampleCount, boolean sampled) {
        return sampleCount <= getMaxRenderTargetSampleCount(format, sampled);
    }

    @Override
    public int getRenderTargetSampleCount(int sampleCount, int format, boolean sampled) {
        FormatInfo formatInfo = getFormatInfo(format);
        if (formatInfo.mColorTypeInfos.length == 0) {
            return 0;
        }

        if (sampleCount <= 1) {
            return formatInfo.mColorSampleCounts[0] == 1 ? 1 : 0;
        }

        if (sampled) {
            //TODO MS Texture
            return 0;
        }

        for (int count : formatInfo.mColorSampleCounts) {
            if (count >= sampleCount) {
                return count;
            }
        }
        return 0;
    }

    @Nullable
    @Override
    public ImageDesc getDefaultColorImageDesc(int imageType,
                                              int colorType,
                                              int width, int height,
                                              int depthOrArraySize,
                                              int mipLevelCount, int sampleCount,
                                              int imageFlags) {
        //TODO depth and array size
        //TODO log errors
        if (width < 1 || height < 1 || depthOrArraySize < 1 ||
                mipLevelCount < 0 || sampleCount < 0) {
            return null;
        }
        //TODO make texture sample counts and renderbuffer sample counts
        int format = mColorTypeToFormat[colorType];
        FormatInfo formatInfo = getFormatInfo(format);
        if ((imageFlags & ISurface.FLAG_PROTECTED) != 0) {
            return null;
        }
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
        final int glTarget;
        final int glFormat;
        if ((imageFlags & (ISurface.FLAG_SAMPLED_IMAGE | ISurface.FLAG_STORAGE_IMAGE)) != 0) {
            final int maxSize = maxTextureSize();
            if (width > maxSize || height > maxSize || !formatInfo.isTexturable()) {
                return null;
            }
            glTarget = GL_TEXTURE_2D;
            glFormat = formatInfo.mInternalFormatForTexture;
            //TODO not only 2D, but
            // GL30C.GL_TEXTURE_2D_ARRAY
            // GL30C.GL_TEXTURE_3D
            // GL30C.GL_TEXTURE_CUBE_MAP
            // GL40C.GL_TEXTURE_CUBE_MAP_ARRAY
        } else if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0) {
            final int maxSize = maxRenderTargetSize();
            if (width > maxSize || height > maxSize) {
                return null;
            }
            //TODO if cannot make renderbuffer, create texture instead
            glTarget = GL_RENDERBUFFER;
            glFormat = formatInfo.mInternalFormatForRenderbuffer;
        } else {
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
        if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0) {
            if ((formatInfo.colorTypeFlags(colorType) & ColorTypeInfo.kRenderable_Flag) == 0) {
                return null;
            }
            sampleCount = getRenderTargetSampleCount(sampleCount, format,
                    (imageFlags & ISurface.FLAG_SAMPLED_IMAGE) != 0);
            if (sampleCount == 0) {
                return null;
            }
        } else {
            sampleCount = 1;
        }
        if (sampleCount > 1 && mipLevelCount > 1) {
            return null;
        }
        // ignore MEMORYLESS flag
        return new GLImageDesc(glTarget, glFormat,
                imageType, format,
                width, height,
                depth, arraySize,
                mipLevelCount, sampleCount,
                imageFlags);
    }

    @Nullable
    @Override
    public ImageDesc getDefaultDepthStencilImageDesc(int depthBits, int stencilBits,
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
                depthStencilFormat = GL_DEPTH24_STENCIL8;
                viewFormat = ImageFormat.kD24_S8;
            } else {
                depthStencilFormat = GL_DEPTH32F_STENCIL8;
                viewFormat = ImageFormat.kD32F_S8;
            }
        } else {
            if (depthBits <= 16) {
                depthStencilFormat = GL_DEPTH_COMPONENT16;
                viewFormat = ImageFormat.kD16;
            } else if (depthBits <= 24) {
                depthStencilFormat = GL_DEPTH_COMPONENT24;
                viewFormat = ImageFormat.kD24;
            } else {
                depthStencilFormat = GL_DEPTH_COMPONENT32F;
                viewFormat = ImageFormat.kD32F;
            }
        }
        // the above 5 formats are "Required format" in OpenGL 3.3 and OpenGL ES 3.0
        //TODO add STENCIL_INDEX_8 support?

        //TODO 2D texture version, 2D array texture version?
        return new GLImageDesc(GL_RENDERBUFFER, depthStencilFormat,
                Engine.ImageType.kNone, viewFormat, width, height,
                1, 1, 1, sampleCount, imageFlags);
    }

    @Nullable
    @Override
    public ImageDesc getImageDescForSampledCopy(ImageDesc src,
                                                int width, int height,
                                                int depthOrArraySize,
                                                int imageFlags) {
        if (!(src instanceof GLImageDesc glSrc)) {
            return null;
        }
        //TODO
        final int maxSize = maxTextureSize();
        if (width > maxSize || height > maxSize) {
            return null;
        }
        int mipLevelCount;
        int maxMipLevels = DataUtils.computeMipLevelCount(width, height, 1);
        mipLevelCount = (imageFlags & ISurface.FLAG_MIPMAPPED) != 0
                ? maxMipLevels
                : 1; // only base level
        return new GLImageDesc(
                GL_TEXTURE_2D, glSrc.getGLFormat(),
                Engine.ImageType.k2D, src.getViewFormat(),
                width, height, 1, 1,
                mipLevelCount, 1, imageFlags | ISurface.FLAG_SAMPLED_IMAGE
        );
    }

    @Nullable
    @Override
    protected BackendFormat onGetDefaultBackendFormat(int colorType) {
        return null;//mColorTypeToBackendFormat[colorType];
    }

    @Nullable
    @Override
    public BackendFormat getCompressedBackendFormat(int compressionType) {
        return mCompressionTypeToBackendFormat[compressionType];
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

    @NonNull
    @Override
    public PipelineKey makeGraphicsPipelineKey(PipelineKey old,
                                               PipelineDesc pipelineDesc,
                                               RenderPassDesc renderPassDesc) {
        if (old instanceof GLGraphicsPipelineKey pipelineKey) {
            pipelineKey.mPipelineDesc = pipelineDesc;
            return pipelineKey;
        } else {
            GLGraphicsPipelineKey pipelineKey = new GLGraphicsPipelineKey();
            pipelineKey.mPipelineDesc = pipelineDesc;
            return pipelineKey;
        }
    }

    @Override
    public IResourceKey computeImageKey(ImageDesc desc,
                                        IResourceKey recycle) {
        if (desc instanceof GLImageDesc glDesc) {
            return new GLImage.ResourceKey(glDesc);
        }
        return null;
    }

    @Override
    public int getSupportedWriteColorType(int surfaceColorType, ImageDesc dstDesc) {
        // We first try to find a supported write pixels ColorType that matches the data's
        // srcColorType. If that doesn't exist we will use any supported ColorType.
        final FormatInfo formatInfo = getFormatInfo(dstDesc.getViewFormat());
        if ((formatInfo.mFlags & FormatInfo.TRANSFERS_FLAG) == 0) {
            return ColorInfo.CT_UNKNOWN;
        }
        for (int i = 0; i < formatInfo.mColorTypeInfos.length; ++i) {
            final ColorTypeInfo ctInfo = formatInfo.mColorTypeInfos[i];
            if (ctInfo.mColorType == surfaceColorType) {
                if (formatInfo.mExternalTexImageFormat != 0) {
                    return ctInfo.mColorType;
                }
            }
        }
        return ColorInfo.CT_UNKNOWN;
    }

    public static int getExternalTypeAlignment(int type) {
        // This switch is derived from a table titled "Pixel data type parameter values and the
        // corresponding GL data types" in the OpenGL spec (Table 8.2 in OpenGL 4.5).
        return switch (type) {
            case GL_UNSIGNED_BYTE,
                    GL_BYTE,
                    GL_UNSIGNED_BYTE_2_3_3_REV,
                    GL_UNSIGNED_BYTE_3_3_2 -> 1;
            case GL_UNSIGNED_SHORT,
                    GL_SHORT,
                    GL_UNSIGNED_SHORT_1_5_5_5_REV,
                    GL_UNSIGNED_SHORT_4_4_4_4_REV,
                    GL_UNSIGNED_SHORT_5_6_5_REV,
                    GL_UNSIGNED_SHORT_5_5_5_1,
                    GL_UNSIGNED_SHORT_4_4_4_4,
                    GL_UNSIGNED_SHORT_5_6_5,
                    GL_HALF_FLOAT -> 2;
            case GL_UNSIGNED_INT,
                    GL_FLOAT_32_UNSIGNED_INT_24_8_REV,
                    GL_UNSIGNED_INT_5_9_9_9_REV,
                    GL_UNSIGNED_INT_10F_11F_11F_REV,
                    GL_UNSIGNED_INT_24_8,
                    GL_UNSIGNED_INT_10_10_10_2,
                    GL_UNSIGNED_INT_8_8_8_8_REV,
                    GL_UNSIGNED_INT_8_8_8_8,
                    GL_UNSIGNED_INT_2_10_10_10_REV,
                    GL_FLOAT,
                    GL_INT -> 4;
            default -> 0;
        };
    }

    //TODO
    @Override
    protected long onSupportedReadColorType(int srcColorType, BackendFormat srcFormat, int dstColorType) {
        int compression = srcFormat.getCompressionType();
        if (compression != ColorInfo.COMPRESSION_NONE) {
            return (DataUtils.compressionTypeIsOpaque(compression) ?
                    ColorInfo.CT_RGBX_8888 :
                    ColorInfo.CT_RGBA_8888); // alignment = 0
        }

        // We first try to find a supported read pixels ColorType that matches the requested
        // dstColorType. If that doesn't exist we will use any valid read pixels ColorType.
        int fallbackColorType = ColorInfo.CT_UNKNOWN;
        long fallbackTransferOffsetAlignment = 0;
        FormatInfo formatInfo = getFormatInfo(srcFormat.getGLFormat());
        /*for (ColorTypeInfo ctInfo : formatInfo.mColorTypeInfos) {
            if (ctInfo.mColorType == srcColorType) {
                for (ExternalIOFormat ioInfo : ctInfo.mExternalIOFormats) {
                    if (ioInfo.mExternalReadFormat != 0) {
                        long transferOffsetAlignment = 0;
                        if ((formatInfo.mFlags & FormatInfo.TRANSFERS_FLAG) != 0) {
                            transferOffsetAlignment = getExternalTypeAlignment(ioInfo.mExternalType);
                        }
                        if (ioInfo.mColorType == dstColorType) {
                            return dstColorType | (transferOffsetAlignment << 32);
                        }
                        // Currently, we just pick the first supported format that we find as our
                        // fallback.
                        if (fallbackColorType == ColorInfo.CT_UNKNOWN) {
                            fallbackColorType = ioInfo.mColorType;
                            fallbackTransferOffsetAlignment = transferOffsetAlignment;
                        }
                    }
                }
                break;
            }
        }*/
        return fallbackColorType | (fallbackTransferOffsetAlignment << 32);
    }

    @Override
    protected void onApplyOptionsOverrides(ContextOptions options) {
        super.onApplyOptionsOverrides(options);
        if (options.mSkipGLErrorChecks == Boolean.FALSE) {
            mSkipErrorChecks = false;
        } else if (options.mSkipGLErrorChecks == Boolean.TRUE) {
            mSkipErrorChecks = true;
        } else {
            // NVIDIA uses threaded driver then error checks can be very slow
            mSkipErrorChecks = (mDriver == GLUtil.GLDriver.NVIDIA);
        }
        mUseStagingBuffers = options.mUseStagingBuffers;
    }

    /**
     * Gets the internal format to use with glTexImage...() and glTexStorage...(). May be sized or
     * base depending upon the GL. Not applicable to compressed textures.
     */
    public int getTextureInternalFormat(int format) {
        return getFormatInfo(format).mInternalFormatForTexture;
    }

    /**
     * Gets the internal format to use with glRenderbufferStorageMultisample...(). May be sized or
     * base depending upon the GL. Not applicable to compressed textures.
     */
    public int getRenderbufferInternalFormat(int format) {
        return getFormatInfo(format).mInternalFormatForRenderbuffer;
    }

    /**
     * Gets the default external format to use with glTex[Sub]Image... when the data pointer is null.
     */
    public int getFormatDefaultExternalFormat(int format) {
        return getFormatInfo(format).mDefaultExternalFormat;
    }

    /**
     * Gets the default external type to use with glTex[Sub]Image... when the data pointer is null.
     */
    public int getFormatDefaultExternalType(int format) {
        return getFormatInfo(format).mDefaultExternalType;
    }

    public int getPixelsExternalFormat(int format, boolean write) {
        var formatInfo = getFormatInfo(format);
        return write ? formatInfo.mExternalTexImageFormat : formatInfo.mExternalReadFormat;
    }

    public int getPixelsExternalType(int format) {
        return getFormatInfo(format).mExternalType;
    }

    public boolean canCopyImage(int srcFormat, int srcSampleCount,
                                int dstFormat, int dstSampleCount) {
        if (!mCopyImageSupport) {
            return false;
        }
        if ((dstSampleCount > 1 || srcSampleCount > 1) &&
                dstSampleCount != srcSampleCount) {
            return false;
        }
        if (srcFormat == dstFormat) {
            return true;
        }
        if (mViewCompatibilityClassSupport) {
            return getFormatInfo(srcFormat).mViewCompatibilityClass ==
                    getFormatInfo(dstFormat).mViewCompatibilityClass;
        }
        return false;
    }

    public boolean canCopyTexSubImage(int srcFormat,
                                      int dstFormat) {
        // channels should be compatible
        if (getFormatDefaultExternalType(dstFormat) !=
                getFormatDefaultExternalType(srcFormat)) {
            return false;
        }
        if (Engine.ImageFormat.isSRGB(dstFormat) != Engine.ImageFormat.isSRGB(srcFormat)) {
            return false;
        }
        return (getFormatInfo(srcFormat).mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0;
    }

    /**
     * Skip checks for GL errors, shader compilation success, program link success.
     */
    public boolean skipErrorChecks() {
        return mSkipErrorChecks;
    }

    public int maxLabelLength() {
        return mMaxLabelLength;
    }

    public float maxTextureMaxAnisotropy() {
        return mMaxTextureMaxAnisotropy;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("GLCaps:\n");
        dump(b, true);
        return b.toString();
    }

    public void dump(StringBuilder out, boolean includeFormatTable) {
        out.append("AnisotropySupport: ").append(mAnisotropySupport).append('\n');
        out.append("GpuTracingSupport: ").append(mGpuTracingSupport).append('\n');
        out.append("ConservativeRasterSupport: ").append(mConservativeRasterSupport).append('\n');
        out.append("TextureBarrierSupport: ").append(mTextureBarrierSupport).append('\n');
        out.append("DepthClipNegativeOneToOne: ").append(mDepthClipNegativeOneToOne).append('\n');
        out.append("BlendEquationSupport: ").append(mBlendEquationSupport).append('\n');
        out.append("ClampToBorderSupport: ").append(mClampToBorderSupport).append('\n');
        out.append("MaxRenderTargetSize: ").append(mMaxRenderTargetSize).append('\n');
        out.append("MaxPreferredRenderTargetSize: ").append(mMaxPreferredRenderTargetSize).append('\n');
        out.append("MaxVertexAttributes: ").append(mMaxVertexAttributes).append('\n');
        out.append("MaxVertexBindings: ").append(mMaxVertexBindings).append('\n');
        out.append("MaxTextureSize: ").append(mMaxTextureSize).append('\n');
        out.append("MaxPushConstantsSize: ").append(mMaxPushConstantsSize).append('\n');
        out.append("MaxColorAttachments: ").append(mMaxColorAttachments).append('\n');
        out.append("MinUniformBufferOffsetAlignment: ").append(mMinUniformBufferOffsetAlignment).append('\n');
        out.append("MinStorageBufferOffsetAlignment: ").append(mMinStorageBufferOffsetAlignment).append('\n');

        out.append("ShaderCaps:\n");
        mShaderCaps.dump("\t", out);

        out.append("Vendor: ").append(mVendor).append('\n');
        out.append("Driver: ").append(mDriver).append('\n');
        out.append("MaxFragmentUniformVectors: ").append(mMaxFragmentUniformVectors).append('\n');
        out.append("MaxTextureMaxAnisotropy: ").append(mMaxTextureMaxAnisotropy).append('\n');
        out.append("SupportsProtected: ").append(mSupportsProtected).append('\n');
        out.append("SkipErrorChecks: ").append(mSkipErrorChecks).append('\n');
        out.append("MaxLabelLength: ").append(mMaxLabelLength).append('\n');
        out.append("DebugSupport: ").append(mDebugSupport).append('\n');
        out.append("BufferStorageSupport: ").append(mBufferStorageSupport).append('\n');
        out.append("DrawElementsBaseVertexSupport: ").append(mDrawElementsBaseVertexSupport).append('\n');
        out.append("BaseInstanceSupport: ").append(mBaseInstanceSupport).append('\n');
        out.append("DSASupport: ").append(mDSASupport).append('\n');
        out.append("InvalidateBufferType: ").append(mInvalidateBufferType).append('\n');
        out.append("InvalidateFramebufferSupport: ").append(mInvalidateFramebufferSupport).append('\n');
        out.append("ShaderBinarySupport: ").append(mShaderBinarySupport).append('\n');
        out.append("UseStagingBuffers: ").append(mUseStagingBuffers).append('\n');
        out.append("MustHaveFragmentShader: ").append(mMustHaveFragmentShader).append('\n');
        out.append("VertexAttribBindingSupport: ").append(mVertexAttribBindingSupport).append('\n');
        out.append("CopyImageSupport: ").append(mCopyImageSupport).append('\n');
        out.append("SPIRVSupport: ").append(mSPIRVSupport).append('\n');
        out.append("ViewCompatibilityClassSupport: ").append(mViewCompatibilityClassSupport).append('\n');
        out.append("TexStorageSupport: ").append(mTexStorageSupport).append('\n');
        out.append("ProgramBinarySupport: ").append(mProgramBinarySupport).append('\n');
        out.append("ProgramBinaryFormats: ").append(Arrays.toString(mProgramBinaryFormats)).append('\n');
        out.append("GLSLVersion: ").append(mGLSLVersion).append('\n');

        out.append("ColorTypeToFormat:\n");
        for (int i = 0; i < mColorTypeToFormat.length; i++) {
            out.append('\t').append(ColorInfo.colorTypeToString(i))
                    .append("=>").append(ImageFormat.toString(mColorTypeToFormat[i])).append('\n');
        }

        out.append("CompressionTypeToBackendFormat: ").append(Arrays.toString(mCompressionTypeToBackendFormat)).append('\n');

        if (includeFormatTable) {
            out.append("FormatTable:\n");
            for (int i = 1; i < mFormatTable.length; i++) {
                out.append('\t').append(ImageFormat.toString(i))
                        .append("=>\n");
                mFormatTable[i].dump("\t\t", out);
            }
        }
    }

    static class FormatInfo {

        /**
         * COLOR_ATTACHMENT_FLAG: even if the format cannot be a RenderTarget, we can still attach
         * it to a framebuffer for blitting or reading pixels.
         * <p>
         * TRANSFERS_FLAG: pixel buffer objects supported in/out of this format.
         */
        static final int
                TEXTURABLE_FLAG = 0x01,
                COLOR_ATTACHMENT_FLAG = 0x02,
                COLOR_ATTACHMENT_WITH_MSAA_FLAG = 0x04,
                TEXTURE_STORAGE_FLAG = 0x08,
                TRANSFERS_FLAG = 0x10;
        int mFlags = 0;

        // Value to use as the "internalformat" argument to glTexImage or glTexStorage. It is
        // initialized in coordination with the presence/absence of the UseTexStorage flag. In
        // other words, it is only guaranteed to be compatible with glTexImage if the flag is not
        // set and or with glTexStorage if the flag is set.
        int mInternalFormatForTexture = 0;

        // Value to use as the "internalformat" argument to glRenderbufferStorageMultisample...
        int mInternalFormatForRenderbuffer = 0;

        // Default values to use along with mInternalFormatForTexture for function
        // glTexImage2D when not input providing data (passing nullptr) or when clearing it by
        // uploading a block of solid color data.
        int mDefaultExternalFormat = 0;
        int mDefaultExternalType = 0;

        /**
         * The external format and type are to be used when uploading/downloading data using
         * data of mColorType and uploading to a texture of a given GLFormat and its
         * intended ColorType. The mExternalTexImageFormat is the format to use for TexImage
         * calls. The mExternalReadFormat is used when calling ReadPixels. If either is zero
         * that signals that either TexImage or ReadPixels is not supported for the combination
         * of format and color types. Not defined for compressed formats.
         */
        int mExternalType = 0;
        int mExternalTexImageFormat = 0;
        int mExternalReadFormat = 0;

        /**
         * For GLES, after queried, this sets to false, and sets ReadFormat to 0 is not supported.
         */
        boolean mRequiresImplementationReadQuery = false;

        // When the above two values are used to initialize a texture by uploading cleared data to
        // it the data should be of this color type.
        @ColorInfo.ColorType
        int mDefaultColorType = ColorInfo.CT_UNKNOWN;

        // OpenGL 4.3 ViewCompatibilityClass
        int mViewCompatibilityClass = 0;

        int[] mColorSampleCounts = {};

        ColorTypeInfo[] mColorTypeInfos = {};

        public boolean isTexturable() {
            return (mFlags & FormatInfo.TEXTURABLE_FLAG) != 0;
        }

        public int colorTypeFlags(int colorType) {
            for (ColorTypeInfo info : mColorTypeInfos) {
                if (info.mColorType == colorType) {
                    return info.mFlags;
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("FormatInfo:\n");
            dump("", b);
            return b.toString();
        }

        void dump(String prefix, StringBuilder out) {
            out.append(prefix).append("Flags: 0x").append(Integer.toHexString(mFlags)).append('\n');
            //out.append(prefix).append("FormatType: ").append(mFormatType).append('\n');
            out.append(prefix).append("InternalFormatForTexture: ").append(mInternalFormatForTexture).append('\n');
            out.append(prefix).append("InternalFormatForRenderbuffer: ").append(mInternalFormatForRenderbuffer).append('\n');
            out.append(prefix).append("DefaultExternalFormat: ").append(mDefaultExternalFormat).append('\n');
            out.append(prefix).append("DefaultExternalType: ").append(mDefaultExternalType).append('\n');
            out.append(prefix).append("DefaultColorType: ").append(ColorInfo.colorTypeToString(mDefaultColorType)).append('\n');
            out.append(prefix).append("ColorSampleCounts: ").append(Arrays.toString(mColorSampleCounts)).append('\n');
            for (int i = 0; i < mColorTypeInfos.length; i++) {
                out.append(prefix).append("ColorTypeInfo[").append(i).append("]:\n");
                mColorTypeInfos[i].dump(prefix + "\t", out);
            }
        }
    }
}

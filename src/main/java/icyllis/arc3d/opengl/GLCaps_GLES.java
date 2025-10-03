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

package icyllis.arc3d.opengl;

import icyllis.arc3d.compiler.GLSLVersion;
import icyllis.arc3d.compiler.TargetApi;
import icyllis.arc3d.engine.*;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.opengles.*;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.*;
import java.util.Objects;

import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES30.*;
import static org.lwjgl.opengles.GLES31.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengles.GLES32.GL_MAX_LABEL_LENGTH;

public final class GLCaps_GLES extends GLCaps {

    @VisibleForTesting
    public GLCaps_GLES(ContextOptions options, Object capabilities, GLInterface interf) {
        super(options);
        GLESCapabilities caps = (GLESCapabilities) capabilities;
        // OpenGL ES 3.0 is the minimum requirement
        if (!caps.GLES30) {
            throw new UnsupportedOperationException("OpenGL ES 3.0 is unavailable");
        }
        Logger logger = Objects.requireNonNullElse(options.mLogger, NOPLogger.NOP_LOGGER);
        initCommonFunctions(caps, interf);

        if (caps.GL_NV_texture_barrier) {
            mTextureBarrierSupport = true;
            interf.glTextureBarrier = caps.glTextureBarrierNV;
            logger.info("Use NV_texture_barrier");
        } else {
            mTextureBarrierSupport = false;
        }
        mDSASupport = false;

        mDebugSupport = caps.GLES32;
        if (caps.GLES32) {
            mDrawElementsBaseVertexSupport = true;
        } else if (caps.GL_EXT_draw_elements_base_vertex) {
            mDrawElementsBaseVertexSupport = true;
            interf.glDrawElementsBaseVertex = caps.glDrawElementsBaseVertexEXT;
            interf.glDrawElementsInstancedBaseVertex = caps.glDrawElementsInstancedBaseVertexEXT;
            logger.info("Use EXT_draw_elements_base_vertex");
        } else {
            mDrawElementsBaseVertexSupport = false;
        }
        mBaseInstanceSupport = caps.GL_EXT_base_instance;
        if (mBaseInstanceSupport) {
            interf.glDrawArraysInstancedBaseInstance = caps.glDrawArraysInstancedBaseInstanceEXT;
            interf.glDrawElementsInstancedBaseVertexBaseInstance = caps.glDrawElementsInstancedBaseVertexBaseInstanceEXT;
            logger.info("Use EXT_base_instance");
        }
        if (caps.GLES32) {
            mCopyImageSupport = true;
        } else if (caps.GL_EXT_copy_image) {
            mCopyImageSupport = true;
            interf.glCopyImageSubData = caps.glCopyImageSubDataEXT;
            logger.info("Use EXT_copy_image");
        } else {
            mCopyImageSupport = false;
        }
        // textureStorageSupported - OpenGL ES 3.0
        mTexStorageSupport = true;
        mViewCompatibilityClassSupport = false;
        // OpenGL ES 2.0
        mShaderBinarySupport = true;
        // OpenGL ES 3.0
        mProgramBinarySupport = true;
        mProgramParameterSupport = true;
        mVertexAttribBindingSupport = caps.GLES31;
        mBufferStorageSupport = caps.GL_EXT_buffer_storage;
        if (mBufferStorageSupport) {
            interf.glBufferStorage = caps.glBufferStorageEXT;
            logger.info("Use EXT_buffer_storage");
        }
        // our attachment points are consistent with draw buffers
        mMaxColorAttachments = Math.min(Math.min(
                        glGetInteger(GL_MAX_DRAW_BUFFERS),
                        glGetInteger(GL_MAX_COLOR_ATTACHMENTS)),
                MAX_COLOR_TARGETS);
        mMinUniformBufferOffsetAlignment = glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        mMinStorageBufferOffsetAlignment = glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);

        String versionString = GLES20.glGetString(GL_VERSION);
        String vendorString = GLES20.glGetString(GL_VENDOR);
        mVendor = GLUtil.findVendor(vendorString);
        mDriver = GLUtil.findDriver(mVendor, vendorString, versionString);
        logger.info("Identified OpenGL vendor: {}", mVendor);
        logger.info("Identified OpenGL driver: {}", mDriver);

        mMaxFragmentUniformVectors = GLES20.glGetInteger(GL_MAX_FRAGMENT_UNIFORM_VECTORS);
        mMaxVertexAttributes = Math.min(MAX_VERTEX_ATTRIBUTES, GLES20.glGetInteger(GL_MAX_VERTEX_ATTRIBS));

        mInvalidateBufferType = INVALIDATE_BUFFER_TYPE_NULL_DATA;
        mInvalidateFramebufferSupport = true;

        mTransferPixelsToRowBytesSupport = true;

        // When we are abandoning the context we cannot call into GL thus we should skip any sync work.
        mMustSyncGpuDuringDiscard = false;

        if (mDebugSupport) {
            mMaxLabelLength = GLES20.glGetInteger(GL_MAX_LABEL_LENGTH);
        } else {
            mMaxLabelLength = 0;
        }

        ShaderCaps shaderCaps = mShaderCaps;
        // target API is just for validation
        if (caps.GLES31) {
            shaderCaps.mTargetApi = TargetApi.OPENGL_ES_3_1;
        } else {
            shaderCaps.mTargetApi = TargetApi.OPENGL_ES_3_0;
        }
        final int glslVersion;
        if (caps.GLES32) {
            glslVersion = 320;
            logger.info("Using OpenGL ES 3.2 and GLSL 3.20");
        } else if (caps.GLES31) {
            glslVersion = 310;
            logger.info("Using OpenGL ES 3.1 and GLSL 3.10");
        } else {
            glslVersion = 300;
            logger.info("Using OpenGL ES 3.0 and GLSL 3.00");
        }
        mGLSLVersion = glslVersion;
        if (glslVersion == 320) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_320_ES;
        } else if (glslVersion == 310) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_310_ES;
        } else {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_300_ES;
        }
        initGLSL(caps, shaderCaps.mGLSLVersion);

        shaderCaps.mDualSourceBlendingSupport = caps.GL_EXT_blend_func_extended;
        if (shaderCaps.mDualSourceBlendingSupport) {
            logger.info("Use EXT_blend_func_extended");
        }

        if (caps.GL_NV_conservative_raster) {
            mConservativeRasterSupport = true;
            logger.info("Use NV_conservative_raster");
        }

        // Protect ourselves against tracking huge amounts of texture state.
        shaderCaps.mMaxFragmentSamplers = Math.min(32, GLES20.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS));

        if (caps.GL_NV_blend_equation_advanced_coherent) {
            mBlendEquationSupport = Caps.BlendEquationSupport.ADVANCED_COHERENT;
            shaderCaps.mAdvBlendEqInteraction = ShaderCaps.Automatic_AdvBlendEqInteraction;
            logger.info("Use NV_blend_equation_advanced_coherent");
        } else if (caps.GL_KHR_blend_equation_advanced_coherent) {
            mBlendEquationSupport = Caps.BlendEquationSupport.ADVANCED_COHERENT;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.GeneralEnable_AdvBlendEqInteraction;
            logger.info("Use KHR_blend_equation_advanced_coherent");
        } else if (caps.GL_NV_blend_equation_advanced) {
            mBlendEquationSupport = Caps.BlendEquationSupport.ADVANCED;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.Automatic_AdvBlendEqInteraction;
            logger.info("Use NV_blend_equation_advanced");
        } else if (caps.GL_KHR_blend_equation_advanced) {
            mBlendEquationSupport = Caps.BlendEquationSupport.ADVANCED;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.GeneralEnable_AdvBlendEqInteraction;
            logger.info("Use KHR_blend_equation_advanced");
        }

        mAnisotropySupport = caps.GL_EXT_texture_filter_anisotropic;
        if (mAnisotropySupport) {
            mMaxTextureMaxAnisotropy = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            logger.info("Use EXT_texture_filter_anisotropic");
        }

        mMaxTextureSize = GLES20.glGetInteger(GL_MAX_TEXTURE_SIZE);
        mMaxRenderTargetSize = GLES20.glGetInteger(GL_MAX_RENDERBUFFER_SIZE);
        mMaxPreferredRenderTargetSize = mMaxRenderTargetSize;

        if (!caps.GLES32 &&
                !caps.GL_EXT_texture_border_clamp &&
                !caps.GL_NV_texture_border_clamp &&
                !caps.GL_OES_texture_border_clamp) {
            mClampToBorderSupport = false;
        }

        mGpuTracingSupport = caps.GL_EXT_debug_marker;
        if (mGpuTracingSupport) {
            logger.info("Use EXT_debug_marker");
        }

        mDynamicStateArrayGeometryProcessorTextureSupport = true;

        if (mProgramBinarySupport) {
            int count = GLES20.glGetInteger(GL_NUM_PROGRAM_BINARY_FORMATS);
            if (count > 0) {
                mProgramBinaryFormats = new int[count];
                GLES20.glGetIntegerv(GL_PROGRAM_BINARY_FORMATS, mProgramBinaryFormats);
            } else {
                mProgramBinarySupport = false;
            }
        }
        mSPIRVSupport = false;

        initFormatTable(caps);
        assert validateFormatTable();

        applyDriverWorkaround();

        finishInitialization(options);
    }

    private void initGLSL(GLESCapabilities caps, GLSLVersion version) {
        ShaderCaps shaderCaps = mShaderCaps;

        shaderCaps.mPreferFlatInterpolation = mVendor != GLUtil.GLVendor.QUALCOMM;
        shaderCaps.mNoPerspectiveInterpolationSupport = false;
        // GLSL 300 ES
        shaderCaps.mVertexIDSupport = true;
        // GLSL 300 ES
        shaderCaps.mInfinitySupport = true;
        // GLSL 300 ES
        shaderCaps.mNonConstantArrayIndexSupport = true;
        // GLSL 310 ES
        shaderCaps.mBitManipulationSupport = version.isAtLeast(GLSLVersion.GLSL_310_ES);
        // GLSL 320 ES
        shaderCaps.mFMASupport = version.isAtLeast(GLSLVersion.GLSL_320_ES);

        shaderCaps.mUniformBindingSupport = caps.GLES31;
        shaderCaps.mVaryingLocationSupport = caps.GLES31;
        shaderCaps.mUseBlockMemberOffset = false; // Vulkan only
        shaderCaps.mUsePrecisionModifiers = true;
    }

    void initFormatTable(GLESCapabilities caps) {
        // textureStorageSupported - OpenGL ES 3.0
        super.initFormatTable(true, caps.GL_EXT_texture_compression_s3tc);

        final int nonMSAARenderFlags = FormatInfo.COLOR_ATTACHMENT_FLAG;
        final int msaaRenderFlags = nonMSAARenderFlags | FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG;

        // Format: RGB565
        {
            FormatInfo info = getFormatInfo(GL_RGB565);
            info.mFlags |= FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
        }

        // Format: RGB8
        {
            FormatInfo info = mFormatTable[6];
            info.mFlags |= msaaRenderFlags;
        }

        // Format: COMPRESSED_RGB8_ETC2
        {
            FormatInfo info = getFormatInfo(GL_COMPRESSED_RGB8_ETC2);
            info.mFlags |= FormatInfo.TEXTURABLE_FLAG;
        }

        // Init samples
        for (FormatInfo info : mFormatTable) {
            if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG) != 0) {
                // We assume that MSAA rendering is supported only if we support non-MSAA rendering.
                assert (info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0;
                int glFormat = info.mInternalFormatForRenderbuffer;
                // OpenGL ES 3.0
                int count = glGetInternalformati(GL_RENDERBUFFER, glFormat, GL_NUM_SAMPLE_COUNTS);
                if (count > 0) {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer temp = stack.mallocInt(count);
                        glGetInternalformativ(GL_RENDERBUFFER, glFormat, GL_SAMPLES, temp);
                        // GL has a concept of MSAA rasterization with a single sample, but we do not.
                        if (temp.get(count - 1) == 1) {
                            --count;
                            assert (count == 0 || temp.get(count - 1) > 1);
                        }
                        info.mColorSampleCounts = new int[count + 1];
                        // We initialize our supported values with 1 (no msaa) and reverse the order
                        // returned by GL so that the array is ascending.
                        info.mColorSampleCounts[0] = 1;
                        for (int j = 0; j < count; ++j) {
                            info.mColorSampleCounts[j + 1] = temp.get(count - j - 1);
                        }
                    }
                }
            } else if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0) {
                info.mColorSampleCounts = new int[1];
                info.mColorSampleCounts[0] = 1;
            }
        }
    }

    @Override
    public boolean isGLES() {
        return true;
    }

    public static void initCommonFunctions(GLESCapabilities caps, GLInterface interf) {
        interf.glEnable = caps.glEnable;
        interf.glDisable = caps.glDisable;
        interf.glFrontFace = caps.glFrontFace;
        interf.glLineWidth = caps.glLineWidth;
        interf.glGenTextures = caps.glGenTextures;
        interf.glTexParameteri = caps.glTexParameteri;
        interf.glTexParameteriv = caps.glTexParameteriv;
        interf.glTexImage2D = caps.glTexImage2D;
        interf.glTexSubImage2D = caps.glTexSubImage2D;
        interf.glCopyTexSubImage2D = caps.glCopyTexSubImage2D;
        interf.glDeleteTextures = caps.glDeleteTextures;
        interf.glBindTexture = caps.glBindTexture;
        interf.glPixelStorei = caps.glPixelStorei;
        interf.glBlendFunc = caps.glBlendFunc;
        interf.glColorMask = caps.glColorMask;
        interf.glDepthFunc = caps.glDepthFunc;
        interf.glDepthMask = caps.glDepthMask;
        interf.glStencilOp = caps.glStencilOp;
        interf.glStencilFunc = caps.glStencilFunc;
        interf.glStencilMask = caps.glStencilMask;
        interf.glDrawArrays = caps.glDrawArrays;
        interf.glDrawElements = caps.glDrawElements;
        interf.glFlush = caps.glFlush;
        interf.glFinish = caps.glFinish;
        interf.glGetError = caps.glGetError;
        interf.glGetString = caps.glGetString;
        interf.glGetIntegerv = caps.glGetIntegerv;
        interf.glScissor = caps.glScissor;
        interf.glViewport = caps.glViewport;
        interf.glActiveTexture = caps.glActiveTexture;
        interf.glBlendEquation = caps.glBlendEquation;
        interf.glGenBuffers = caps.glGenBuffers;
        interf.glDeleteBuffers = caps.glDeleteBuffers;
        interf.glBindBuffer = caps.glBindBuffer;
        interf.glBufferData = caps.glBufferData;
        interf.glBufferSubData = caps.glBufferSubData;
        interf.glUnmapBuffer = caps.glUnmapBuffer;
        interf.glDrawBuffers = caps.glDrawBuffers;
        interf.glStencilOpSeparate = caps.glStencilOpSeparate;
        interf.glStencilFuncSeparate = caps.glStencilFuncSeparate;
        interf.glStencilMaskSeparate = caps.glStencilMaskSeparate;
        interf.glCreateProgram = caps.glCreateProgram;
        interf.glDeleteProgram = caps.glDeleteProgram;
        interf.glCreateShader = caps.glCreateShader;
        interf.glDeleteShader = caps.glDeleteShader;
        interf.glAttachShader = caps.glAttachShader;
        interf.glDetachShader = caps.glDetachShader;
        interf.glShaderSource = caps.glShaderSource;
        interf.glCompileShader = caps.glCompileShader;
        interf.glLinkProgram = caps.glLinkProgram;
        interf.glUseProgram = caps.glUseProgram;
        interf.glGetShaderiv = caps.glGetShaderiv;
        interf.glGetProgramiv = caps.glGetProgramiv;
        interf.glGetShaderInfoLog = caps.glGetShaderInfoLog;
        interf.glGetProgramInfoLog = caps.glGetProgramInfoLog;
        interf.glGetUniformLocation = caps.glGetUniformLocation;
        interf.glUniform1i = caps.glUniform1i;
        interf.glEnableVertexAttribArray = caps.glEnableVertexAttribArray;
        interf.glVertexAttribPointer = caps.glVertexAttribPointer;
        interf.glVertexAttribIPointer = caps.glVertexAttribIPointer;
        interf.glGenVertexArrays = caps.glGenVertexArrays;
        interf.glDeleteVertexArrays = caps.glDeleteVertexArrays;
        interf.glBindVertexArray = caps.glBindVertexArray;
        interf.glGenFramebuffers = caps.glGenFramebuffers;
        interf.glDeleteFramebuffers = caps.glDeleteFramebuffers;
        interf.glBindFramebuffer = caps.glBindFramebuffer;
        interf.glCheckFramebufferStatus = caps.glCheckFramebufferStatus;
        interf.glFramebufferTexture2D = caps.glFramebufferTexture2D;
        interf.glFramebufferRenderbuffer = caps.glFramebufferRenderbuffer;
        interf.glBlitFramebuffer = caps.glBlitFramebuffer;
        interf.glClearBufferiv = caps.glClearBufferiv;
        interf.glClearBufferfv = caps.glClearBufferfv;
        interf.glClearBufferfi = caps.glClearBufferfi;
        interf.glBindBufferBase = caps.glBindBufferBase;
        interf.glBindBufferRange = caps.glBindBufferRange;
        interf.glGenRenderbuffers = caps.glGenRenderbuffers;
        interf.glDeleteRenderbuffers = caps.glDeleteRenderbuffers;
        interf.glBindRenderbuffer = caps.glBindRenderbuffer;
        interf.glRenderbufferStorage = caps.glRenderbufferStorage;
        interf.glRenderbufferStorageMultisample = caps.glRenderbufferStorageMultisample;
        interf.glMapBufferRange = caps.glMapBufferRange;
        interf.glDrawArraysInstanced = caps.glDrawArraysInstanced;
        interf.glDrawElementsInstanced = caps.glDrawElementsInstanced;
        interf.glCopyBufferSubData = caps.glCopyBufferSubData;
        interf.glGetUniformBlockIndex = caps.glGetUniformBlockIndex;
        interf.glUniformBlockBinding = caps.glUniformBlockBinding;
        interf.glFenceSync = caps.glFenceSync;
        interf.glDeleteSync = caps.glDeleteSync;
        interf.glClientWaitSync = caps.glClientWaitSync;
        interf.glGenSamplers = caps.glGenSamplers;
        interf.glDeleteSamplers = caps.glDeleteSamplers;
        interf.glBindSampler = caps.glBindSampler;
        interf.glSamplerParameteri = caps.glSamplerParameteri;
        interf.glSamplerParameterf = caps.glSamplerParameterf;
        interf.glVertexAttribDivisor = caps.glVertexAttribDivisor;

        interf.glDrawElementsBaseVertex = caps.glDrawElementsBaseVertex;
        interf.glDrawElementsInstancedBaseVertex = caps.glDrawElementsInstancedBaseVertex;
        interf.glShaderBinary = caps.glShaderBinary;
        interf.glTexStorage2D = caps.glTexStorage2D;
        interf.glInvalidateFramebuffer = caps.glInvalidateFramebuffer;
        interf.glCopyImageSubData = caps.glCopyImageSubData;
        interf.glObjectLabel = caps.glObjectLabel;
        interf.glBindVertexBuffer = caps.glBindVertexBuffer;
        interf.glVertexAttribFormat = caps.glVertexAttribFormat;
        interf.glVertexAttribIFormat = caps.glVertexAttribIFormat;
        interf.glVertexAttribBinding = caps.glVertexAttribBinding;
        interf.glVertexBindingDivisor = caps.glVertexBindingDivisor;
    }
}

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

import icyllis.arc3d.compiler.*;
import icyllis.arc3d.engine.ShaderCaps;
import icyllis.arc3d.engine.*;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.opengl.*;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.*;
import java.util.ArrayList;
import java.util.Objects;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.GL_MAX_RENDERBUFFER_SIZE;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.GL46C.GL_MAX_TEXTURE_MAX_ANISOTROPY;

/**
 * OpenGL desktop implementation of OpenGL.
 */
public final class GLCaps_GL extends GLCaps {

    @VisibleForTesting
    public GLCaps_GL(ContextOptions options, Object capabilities, GLInterface interf) {
        super(options);
        GLCapabilities caps = (GLCapabilities) capabilities;
        // OpenGL 3.3 is the minimum requirement
        // but we also allow OpenGL 3.2 with extensions
        if (!caps.OpenGL32) {
            throw new UnsupportedOperationException("OpenGL 3.2 is unavailable");
        }
        if (!caps.OpenGL33) {
            ArrayList<String> missingExtensions = new ArrayList<>();
            if (!caps.GL_ARB_sampler_objects) {
                missingExtensions.add("ARB_sampler_objects");
            }
            if (!caps.GL_ARB_explicit_attrib_location) {
                missingExtensions.add("ARB_explicit_attrib_location");
            }
            if (!caps.GL_ARB_instanced_arrays) {
                missingExtensions.add("ARB_instanced_arrays");
            }
            if (!caps.GL_ARB_texture_swizzle) {
                missingExtensions.add("ARB_texture_swizzle");
            }
            // OpenGL 3.3 is the minimum requirement
            // Note that having these extensions does not mean OpenGL 3.3 is available
            // But these are required and they are available in OpenGL ES 3.0
            if (!missingExtensions.isEmpty()) {
                throw new UnsupportedOperationException("Missing required extensions: " + missingExtensions);
            }
        }
        Logger logger = Objects.requireNonNullElse(options.mLogger, NOPLogger.NOP_LOGGER);
        initCommonFunctions(caps, interf);

        int glslVersion = 0;
        String glslVersionString = glGetString(GL_SHADING_LANGUAGE_VERSION);
        try {
            assert glslVersionString != null;
            var ver = APIUtil.apiParseVersion(glslVersionString);
            glslVersion = ver.major * 100 + ver.minor;
        } catch (Throwable e) {
            logger.error("Malformed GLSL version string", e);
        }
        {
            int glslVersionFromGL;
            if (caps.OpenGL46) {
                glslVersionFromGL = 460;
            } else if (caps.OpenGL45) {
                glslVersionFromGL = 450;
            } else if (caps.OpenGL44) {
                glslVersionFromGL = 440;
            } else if (caps.OpenGL43) {
                glslVersionFromGL = 430;
            } else if (caps.OpenGL42) {
                glslVersionFromGL = 420;
            } else if (caps.OpenGL41) {
                glslVersionFromGL = 410;
            } else if (caps.OpenGL40) {
                glslVersionFromGL = 400;
            } else if (caps.OpenGL33) {
                glslVersionFromGL = 330;
            } else {
                glslVersionFromGL = 150;
            }
            glslVersion = Math.max(glslVersion, glslVersionFromGL);
        }
        logger.debug("GLSL version string: {}, using {}", glslVersionString, glslVersion);

        /*if (!caps.GL_ARB_draw_elements_base_vertex) {
                    missingExtensions.add("ARB_draw_elements_base_vertex");
                }*/

        if (!caps.OpenGL41) {
            // macOS supports this
            if (!caps.GL_ARB_viewport_array) {
                //missingExtensions.add("ARB_viewport_array");
            }
        }
        if (!caps.OpenGL44) {
            if (!caps.GL_ARB_clear_texture) {
                //missingExtensions.add("ARB_clear_texture");
            }
        }
        if (caps.OpenGL45 || caps.GL_ARB_texture_barrier) {
            mTextureBarrierSupport = true;
        } else if (caps.GL_NV_texture_barrier) {
            // macOS supports this
            mTextureBarrierSupport = true;
            interf.glTextureBarrier = caps.glTextureBarrierNV;
            logger.debug("Use NV_texture_barrier");
        } else {
            mTextureBarrierSupport = false;
        }

        mDebugSupport = caps.OpenGL43 || caps.GL_KHR_debug;
        // OpenGL 3.2
        mDrawElementsBaseVertexSupport = true;
        mBaseInstanceSupport = caps.OpenGL42 || caps.GL_ARB_base_instance;
        mCopyImageSupport = caps.OpenGL43 || caps.GL_ARB_copy_image;
        // macOS supports this
        mTexStorageSupport = caps.OpenGL42 || caps.GL_ARB_texture_storage;
        mViewCompatibilityClassSupport = caps.OpenGL43 || caps.GL_ARB_internalformat_query2;
        mShaderBinarySupport = caps.OpenGL41 || caps.GL_ARB_ES2_compatibility;
        mProgramBinarySupport = caps.OpenGL41 || caps.GL_ARB_get_program_binary;
        mProgramParameterSupport = mProgramBinarySupport;
        mVertexAttribBindingSupport = caps.OpenGL43 || caps.GL_ARB_vertex_attrib_binding;
        mBufferStorageSupport = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        // our attachment points are consistent with draw buffers
        mMaxColorAttachments = Math.min(Math.min(
                        glGetInteger(GL_MAX_DRAW_BUFFERS),
                        glGetInteger(GL_MAX_COLOR_ATTACHMENTS)),
                MAX_COLOR_TARGETS);
        mMinUniformBufferOffsetAlignment = glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        mMinStorageBufferOffsetAlignment = glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);

        String versionString = glGetString(GL_VERSION);
        String vendorString = glGetString(GL_VENDOR);
        mVendor = GLUtil.findVendor(vendorString);
        mDriver = GLUtil.findDriver(mVendor, vendorString, versionString);

        // macOS supports this
        if (caps.OpenGL41 || caps.GL_ARB_ES2_compatibility) {
            mMaxFragmentUniformVectors = glGetInteger(GL_MAX_FRAGMENT_UNIFORM_VECTORS);
        } else {
            mMaxFragmentUniformVectors = glGetInteger(GL_MAX_FRAGMENT_UNIFORM_COMPONENTS) / 4;
        }
        mMaxVertexAttributes = Math.min(MAX_VERTEX_ATTRIBUTES, glGetInteger(GL_MAX_VERTEX_ATTRIBS));
        if (mVertexAttribBindingSupport) {
            mMaxVertexBindings = Math.min(MAX_VERTEX_BINDINGS, glGetInteger(GL_MAX_VERTEX_ATTRIB_BINDINGS));
        }

        if (caps.OpenGL43 || caps.GL_ARB_invalidate_subdata) {
            mInvalidateBufferType = INVALIDATE_BUFFER_TYPE_INVALIDATE;
            mInvalidateFramebufferSupport = true;
        } else {
            mInvalidateBufferType = INVALIDATE_BUFFER_TYPE_NULL_DATA;
            mInvalidateFramebufferSupport = false;
        }

        // DSA-like extensions must be supported as well
        mDSASupport = caps.OpenGL45 ||
                (caps.GL_ARB_direct_state_access && mInvalidateBufferType == INVALIDATE_BUFFER_TYPE_INVALIDATE);
        if (mDSASupport && mVendor == GLUtil.GLVendor.INTEL) {
            // many issues on Intel GPU, for example, using DSA method to create vertex array
            // may or may not work, but running program with RenderDoc goes well
            mDSASupport = false;
            logger.info("Intel GPU detected, disabling DSA");
        }

        mTransferPixelsToRowBytesSupport = true;

        // When we are abandoning the context we cannot call into GL thus we should skip any sync work.
        mMustSyncGpuDuringDiscard = false;

        if (mDebugSupport) {
            mMaxLabelLength = glGetInteger(GL_MAX_LABEL_LENGTH);
        } else {
            mMaxLabelLength = 0;
        }

        ShaderCaps shaderCaps = mShaderCaps;
        // target API is just for validation
        if (caps.OpenGL45) {
            shaderCaps.mTargetApi = TargetApi.OPENGL_4_5;
        } else if (caps.OpenGL43) {
            shaderCaps.mTargetApi = TargetApi.OPENGL_4_3;
        } else {
            shaderCaps.mTargetApi = TargetApi.OPENGL_3_3;
        }
        mGLSLVersion = glslVersion;
        // round down the version
        if (glslVersion >= 450) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_450;
        } else if (glslVersion >= 440) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_440;
        } else if (glslVersion >= 430) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_430;
        } else if (glslVersion >= 420) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_420;
        } else if (glslVersion >= 410) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_410;
        } else if (glslVersion >= 400) {
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_400;
        } else {
            // we just assume it supports GLSL 3.30
            // when requesting 3.2 context on some drivers, it only reports 1.50,
            // but it can actually compile 3.30 shaders
            shaderCaps.mGLSLVersion = GLSLVersion.GLSL_330;
        }
        initGLSL(caps, shaderCaps.mGLSLVersion);

        // OpenGL 3.3
        shaderCaps.mDualSourceBlendingSupport = caps.OpenGL33 || caps.GL_ARB_blend_func_extended;

        if (caps.GL_NV_conservative_raster) {
            mConservativeRasterSupport = true;
            logger.info("Use NV_conservative_raster");
        }

        // Protect ourselves against tracking huge amounts of texture state.
        shaderCaps.mMaxFragmentSamplers = Math.min(32, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS));

        if (caps.GL_NV_blend_equation_advanced_coherent) {
            mBlendEquationSupport = BlendEquationSupport.ADVANCED_COHERENT;
            shaderCaps.mAdvBlendEqInteraction = ShaderCaps.Automatic_AdvBlendEqInteraction;
            logger.info("Use NV_blend_equation_advanced_coherent");
        } else if (caps.GL_KHR_blend_equation_advanced_coherent) {
            mBlendEquationSupport = BlendEquationSupport.ADVANCED_COHERENT;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.GeneralEnable_AdvBlendEqInteraction;
            logger.info("Use KHR_blend_equation_advanced_coherent");
        } else if (caps.GL_NV_blend_equation_advanced) {
            mBlendEquationSupport = BlendEquationSupport.ADVANCED;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.Automatic_AdvBlendEqInteraction;
            logger.info("Use NV_blend_equation_advanced");
        } else if (caps.GL_KHR_blend_equation_advanced) {
            mBlendEquationSupport = BlendEquationSupport.ADVANCED;
            mShaderCaps.mAdvBlendEqInteraction = ShaderCaps.GeneralEnable_AdvBlendEqInteraction;
            logger.info("Use KHR_blend_equation_advanced");
        }

        if (caps.OpenGL46 || caps.GL_ARB_texture_filter_anisotropic) {
            mAnisotropySupport = true;
        } else if (caps.GL_EXT_texture_filter_anisotropic) {
            mAnisotropySupport = true;
            logger.info("Use EXT_texture_filter_anisotropic");
        } else {
            mAnisotropySupport = false;
        }
        if (mAnisotropySupport) {
            mMaxTextureMaxAnisotropy = GL11C.glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY);
        }

        mMaxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        mMaxRenderTargetSize = glGetInteger(GL_MAX_RENDERBUFFER_SIZE);
        mMaxPreferredRenderTargetSize = mMaxRenderTargetSize;

        mGpuTracingSupport = caps.GL_EXT_debug_marker;
        if (mGpuTracingSupport) {
            logger.info("Use EXT_debug_marker");
        }

        mDynamicStateArrayGeometryProcessorTextureSupport = true;

        if (mProgramBinarySupport) {
            int count = glGetInteger(GL_NUM_PROGRAM_BINARY_FORMATS);
            if (count > 0) {
                mProgramBinaryFormats = new int[count];
                GL11C.glGetIntegerv(GL_PROGRAM_BINARY_FORMATS, mProgramBinaryFormats);
            } else {
                mProgramBinarySupport = false;
            }
        }
        if (mShaderBinarySupport &&
                (caps.OpenGL46 || caps.GL_ARB_gl_spirv) &&
                options.mAllowGLSPIRV) {
            int count = GL11C.glGetInteger(GL_NUM_SHADER_BINARY_FORMATS);
            if (count > 0) {
                int[] shaderBinaryFormats = new int[count];
                GL11C.glGetIntegerv(GL_SHADER_BINARY_FORMATS, shaderBinaryFormats);
                for (int format : shaderBinaryFormats) {
                    if (format == GL46C.GL_SHADER_BINARY_FORMAT_SPIR_V) {
                        mSPIRVSupport = true;
                        if (!caps.OpenGL46) {
                            interf.glSpecializeShader = caps.glSpecializeShaderARB;
                        }
                        break;
                    }
                }
            }
        }
        if (mSPIRVSupport) {
            shaderCaps.mSPIRVVersion = SPIRVVersion.SPIRV_1_0;
        }

        initFormatTable(caps);
        assert validateFormatTable();

        applyDriverWorkaround();

        finishInitialization(options);
    }

    private void initGLSL(GLCapabilities caps, GLSLVersion version) {
        ShaderCaps shaderCaps = mShaderCaps;

        // Desktop
        shaderCaps.mPreferFlatInterpolation = true;
        // GLSL 130
        shaderCaps.mNoPerspectiveInterpolationSupport = true;
        // Desktop
        shaderCaps.mVertexIDSupport = true;
        // GLSL 330
        shaderCaps.mInfinitySupport = true;
        // Desktop
        shaderCaps.mNonConstantArrayIndexSupport = true;
        // GLSL 400
        if (version.isAtLeast(GLSLVersion.GLSL_400)) {
            shaderCaps.mBitManipulationSupport = true;
            shaderCaps.mFMASupport = true;
        } else if (caps.GL_ARB_gpu_shader5) {
            shaderCaps.mBitManipulationSupport = true;
            shaderCaps.mBitManipulationExtension = "GL_ARB_gpu_shader5";
            shaderCaps.mFMASupport = true;
            shaderCaps.mFMAExtension = "GL_ARB_gpu_shader5";
        } else {
            shaderCaps.mBitManipulationSupport = false;
            shaderCaps.mFMASupport = false;
        }
        // GLSL 400
        shaderCaps.mTextureQueryLod = version.isAtLeast(GLSLVersion.GLSL_400);

        // GLSL 410
        if (version.isAtLeast(GLSLVersion.GLSL_410)) {
            shaderCaps.mVaryingLocationSupport = true;
        } else if (caps.GL_ARB_separate_shader_objects) {
            shaderCaps.mVaryingLocationSupport = true;
            shaderCaps.mVaryingLocationExtension = "GL_ARB_separate_shader_objects";
        } else {
            shaderCaps.mVaryingLocationSupport = false;
        }

        // GLSL 420
        if (version.isAtLeast(GLSLVersion.GLSL_420)) {
            shaderCaps.mUniformBindingSupport = true;
        } else if (caps.GL_ARB_shading_language_420pack) {
            shaderCaps.mUniformBindingSupport = true;
            shaderCaps.mUniformBindingExtension = "GL_ARB_shading_language_420pack";
        } else {
            shaderCaps.mUniformBindingSupport = false;
        }
        // GLSL 440
        shaderCaps.mUseBlockMemberOffset = version.isAtLeast(GLSLVersion.GLSL_440);
        shaderCaps.mUsePrecisionModifiers = false;
    }

    void initFormatTable(GLCapabilities caps) {
        super.initFormatTable(mTexStorageSupport, caps.GL_EXT_texture_compression_s3tc);

        final int nonMSAARenderFlags = FormatInfo.COLOR_ATTACHMENT_FLAG;
        final int msaaRenderFlags = nonMSAARenderFlags | FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG;

        // Format: RGB565
        if (caps.OpenGL42 || caps.GL_ARB_ES2_compatibility) {
            // macOS supports this
            FormatInfo info = getFormatInfo(GL41C.GL_RGB565);
            info.mFlags |= FormatInfo.TEXTURABLE_FLAG | FormatInfo.TRANSFERS_FLAG;
            info.mFlags |= msaaRenderFlags;
        }

        // Format: RGB8
        {
            FormatInfo info = getFormatInfo(GL11C.GL_RGB8);
            // Even in OpenGL 4.6 GL_RGB8 is required to be color renderable but not required to be
            // a supported render buffer format. Since we usually use render buffers for MSAA on
            // we don't support MSAA for GL_RGB8.
            if ((caps.OpenGL43 || caps.GL_ARB_internalformat_query2) &&
                    GL42C.glGetInternalformati(GL30C.GL_RENDERBUFFER, GL11C.GL_RGB8,
                            GL43C.GL_INTERNALFORMAT_SUPPORTED) == GL11C.GL_TRUE) {
                info.mFlags |= msaaRenderFlags;
            } else {
                info.mFlags |= nonMSAARenderFlags;
            }
        }

        // Format: COMPRESSED_RGB8_ETC2
        if (caps.OpenGL43 || caps.GL_ARB_ES3_compatibility) {
            FormatInfo info = getFormatInfo(GL43C.GL_COMPRESSED_RGB8_ETC2);
            info.mFlags |= FormatInfo.TEXTURABLE_FLAG;
        }

        // Init samples
        for (FormatInfo info : mFormatTable) {
            if (mCopyImageSupport && mViewCompatibilityClassSupport && info.mInternalFormatForTexture != 0) {
                info.mViewCompatibilityClass = GL42C.glGetInternalformati(
                        GL11C.GL_TEXTURE_2D, info.mInternalFormatForTexture, GL43C.GL_VIEW_COMPATIBILITY_CLASS
                );
            }
            if ((info.mFlags & FormatInfo.COLOR_ATTACHMENT_WITH_MSAA_FLAG) != 0) {
                // We assume that MSAA rendering is supported only if we support non-MSAA rendering.
                assert (info.mFlags & FormatInfo.COLOR_ATTACHMENT_FLAG) != 0;
                // macOS supports this
                if (caps.OpenGL42 || caps.GL_ARB_internalformat_query) {
                    int glFormat = info.mInternalFormatForRenderbuffer;
                    int count = GL42C.glGetInternalformati(GL30C.GL_RENDERBUFFER, glFormat, GL42C.GL_NUM_SAMPLE_COUNTS);
                    if (count > 0) {
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer temp = stack.mallocInt(count);
                            GL42C.glGetInternalformativ(GL30C.GL_RENDERBUFFER, glFormat, GL13C.GL_SAMPLES, temp);
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
                } else {
                    int maxSampleCnt = Math.max(1, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES));
                    int count = 5; // [1, 2, 4, 8, 16]
                    for (; count > 0; --count) {
                        if ((1 << (count - 1)) <= maxSampleCnt) {
                            break;
                        }
                    }
                    if (count > 0) {
                        info.mColorSampleCounts = new int[count];
                        for (int i = 0; i < count; i++) {
                            info.mColorSampleCounts[i] = 1 << i;
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
        return false;
    }

    public static void initCommonFunctions(GLCapabilities caps, GLInterface interf) {
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
        interf.glDrawArraysInstancedBaseInstance = caps.glDrawArraysInstancedBaseInstance;
        interf.glDrawElementsInstancedBaseVertexBaseInstance = caps.glDrawElementsInstancedBaseVertexBaseInstance;
        interf.glTexStorage2D = caps.glTexStorage2D;
        interf.glInvalidateBufferSubData = caps.glInvalidateBufferSubData;
        interf.glInvalidateFramebuffer = caps.glInvalidateFramebuffer;
        interf.glCopyImageSubData = caps.glCopyImageSubData;
        interf.glObjectLabel = caps.glObjectLabel;
        interf.glBindVertexBuffer = caps.glBindVertexBuffer;
        interf.glVertexAttribFormat = caps.glVertexAttribFormat;
        interf.glVertexAttribIFormat = caps.glVertexAttribIFormat;
        interf.glVertexAttribBinding = caps.glVertexAttribBinding;
        interf.glVertexBindingDivisor = caps.glVertexBindingDivisor;
        interf.glBufferStorage = caps.glBufferStorage;
        interf.glTextureBarrier = caps.glTextureBarrier;
        interf.glCreateBuffers = caps.glCreateBuffers;
        interf.glNamedBufferData = caps.glNamedBufferData;
        interf.glNamedBufferSubData = caps.glNamedBufferSubData;
        interf.glMapNamedBufferRange = caps.glMapNamedBufferRange;
        interf.glUnmapNamedBuffer = caps.glUnmapNamedBuffer;
        interf.glNamedBufferStorage = caps.glNamedBufferStorage;
        interf.glCopyNamedBufferSubData = caps.glCopyNamedBufferSubData;
        interf.glCreateTextures = caps.glCreateTextures;
        interf.glTextureParameteri = caps.glTextureParameteri;
        interf.glTextureParameteriv = caps.glTextureParameteriv;
        interf.glTextureSubImage2D = caps.glTextureSubImage2D;
        interf.glTextureStorage2D = caps.glTextureStorage2D;
        interf.glCreateVertexArrays = caps.glCreateVertexArrays;
        interf.glEnableVertexArrayAttrib = caps.glEnableVertexArrayAttrib;
        interf.glVertexArrayAttribFormat = caps.glVertexArrayAttribFormat;
        interf.glVertexArrayAttribIFormat = caps.glVertexArrayAttribIFormat;
        interf.glVertexArrayAttribBinding = caps.glVertexArrayAttribBinding;
        interf.glVertexArrayBindingDivisor = caps.glVertexArrayBindingDivisor;
        interf.glBindTextureUnit = caps.glBindTextureUnit;
        interf.glSpecializeShader = caps.glSpecializeShader;
    }
}

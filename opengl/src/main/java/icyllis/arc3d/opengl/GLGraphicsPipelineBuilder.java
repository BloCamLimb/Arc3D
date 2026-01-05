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

import icyllis.arc3d.compiler.CompileOptions;
import icyllis.arc3d.compiler.ModuleLoader;
import icyllis.arc3d.compiler.ShaderCompiler;
import icyllis.arc3d.compiler.ShaderKind;
import icyllis.arc3d.compiler.TranslationUnit;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.BlendInfo;
import icyllis.arc3d.engine.DepthStencilSettings;
import icyllis.arc3d.engine.DescriptorSetLayout;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.PipelineDesc;
import icyllis.arc3d.engine.RenderPassDesc;
import icyllis.arc3d.engine.ShaderCaps;
import icyllis.arc3d.engine.VertexInputLayout;
import org.jspecify.annotations.NonNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.system.MemoryStack.stackGet;

public class GLGraphicsPipelineBuilder {

    private final GLDevice mDevice;
    private final PipelineDesc mPipelineDesc;
    private final RenderPassDesc mRenderPassDesc;

    private ByteBuffer mFinalizedVertGLSL;
    private ByteBuffer mFinalizedFragGLSL;
    private ByteBuffer mFinalizedVertSPIRV;
    private ByteBuffer mFinalizedFragSPIRV;

    private byte mPrimitiveType;
    private VertexInputLayout mInputLayout;
    private String mInputLayoutLabel;
    private BlendInfo mBlendInfo;
    private DepthStencilSettings mDepthStencilSettings;
    private DescriptorSetLayout[] mDescriptorSetLayouts;
    private String mPipelineLabel;

    private GLGraphicsPipelineBuilder(GLDevice device,
                                      PipelineDesc pipelineDesc,
                                      RenderPassDesc renderPassDesc) {
        mDevice = device;
        mPipelineDesc = pipelineDesc;
        mRenderPassDesc = renderPassDesc;
    }

    @NonNull
    @SharedPtr
    public static GLGraphicsPipeline createGraphicsPipeline(
            final GLDevice device,
            final PipelineDesc pipelineDesc,
            final RenderPassDesc renderPassDesc) {
        return new GLGraphicsPipeline(device,
                CompletableFuture.supplyAsync(() -> {
                    GLGraphicsPipelineBuilder builder = new GLGraphicsPipelineBuilder(device, pipelineDesc, renderPassDesc);
                    builder.build();
                    return builder;
                }));
    }

    private void build() {
        var info = mPipelineDesc.createGraphicsPipelineInfo(mDevice, mRenderPassDesc);
        mPrimitiveType = info.mPrimitiveType;
        mInputLayout = info.mInputLayout;
        mInputLayoutLabel = info.mInputLayoutLabel;
        mBlendInfo = info.mBlendInfo;
        mDepthStencilSettings = info.mDepthStencilSettings;
        mDescriptorSetLayouts = info.mDescriptorSetLayouts;
        /*mFinalizedVertSource = toUTF8(info.mVertSource);
        mFinalizedFragSource = toUTF8(info.mFragSource);*/
        mPipelineLabel = info.mPipelineLabel;
        ShaderCompiler compiler = new ShaderCompiler();
        CompileOptions options = new CompileOptions();
        ShaderCaps shaderCaps = mDevice.getCaps().shaderCaps();
        options.mUsePrecisionQualifiers = shaderCaps.mUsePrecisionModifiers;
        HashMap<String, String> extensions = new HashMap<>();
        if (shaderCaps.mBitManipulationExtension != null) {
            extensions.put(shaderCaps.mBitManipulationExtension, "require");
        }
        if (shaderCaps.mFMAExtension != null) {
            extensions.put(shaderCaps.mFMAExtension, "require");
        }
        if (shaderCaps.mUniformBindingExtension != null) {
            extensions.put(shaderCaps.mUniformBindingExtension, "require");
        }
        if (shaderCaps.mVaryingLocationExtension != null) {
            extensions.put(shaderCaps.mVaryingLocationExtension, "require");
        }
        options.mExtensions = extensions;

        boolean success = true;
        if (!info.mFragSource.isEmpty()) {
            TranslationUnit fragmentProgram = compiler.parse(info.mFragSource, ShaderKind.FRAGMENT,
                    options, ModuleLoader.getInstance().loadCommonModule(compiler));
            if (fragmentProgram == null) {
                GLUtil.handleCompileError(mDevice.getLogger(),
                        info.mFragSource, compiler.getErrorMessage());
                success = false;
            } else {
                if (mDevice.getCaps().hasSPIRVSupport()) {
                    mFinalizedFragSPIRV = compiler.generateSPIRV(fragmentProgram, shaderCaps);
                } else {
                    mFinalizedFragGLSL = compiler.generateGLSL(fragmentProgram, shaderCaps);
                }
                if (mFinalizedFragGLSL == null && mFinalizedFragSPIRV == null) {
                    GLUtil.handleCompileError(mDevice.getLogger(),
                            info.mFragSource, compiler.getErrorMessage());
                    success = false;
                }
            }
        } else if (mDevice.getCaps().isGLES()) {
            // OpenGL ES requires both a vertex shader and fragment shader, create a simple shader
            String trivialFragGLSL = shaderCaps.mGLSLVersion.mVersionDecl + """
                    void main() {
                    }
                    """;
            mFinalizedFragGLSL = BufferUtils.createByteBuffer(trivialFragGLSL.length());
            MemoryUtil.memUTF8(trivialFragGLSL, false, mFinalizedFragGLSL);
        }
        if (success) {
            TranslationUnit vertexProgram = compiler.parse(info.mVertSource, ShaderKind.VERTEX,
                    options, ModuleLoader.getInstance().loadCommonModule(compiler));
            if (vertexProgram == null) {
                GLUtil.handleCompileError(mDevice.getLogger(),
                        info.mVertSource, compiler.getErrorMessage());
            } else {
                if (mDevice.getCaps().hasSPIRVSupport()) {
                    mFinalizedVertSPIRV = compiler.generateSPIRV(vertexProgram, shaderCaps);
                } else {
                    mFinalizedVertGLSL = compiler.generateGLSL(vertexProgram, shaderCaps);
                }
                if (mFinalizedVertGLSL == null && mFinalizedVertSPIRV == null) {
                    GLUtil.handleCompileError(mDevice.getLogger(),
                            info.mVertSource, compiler.getErrorMessage());
                }
            }
        }
        /*CompletableFuture.runAsync(() -> {
            String filename = mPipelineLabel.replaceAll("/", ".");
            try {
                Files.writeString(Path.of(filename + ".vert"), info.mVertSource,
                        StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (var channel = FileChannel.open(Path.of(filename + ".vert.glsl"),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                var src = mFinalizedVertGLSL.slice();
                while (src.hasRemaining()) {
                    channel.write(src);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Files.writeString(Path.of(filename + ".frag"), info.mFragSource,
                        StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (var channel = FileChannel.open(Path.of(filename + ".frag.glsl"),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                var src = mFinalizedFragGLSL.slice();
                while (src.hasRemaining()) {
                    channel.write(src);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });*/
    }

    /*@NonNull
    public static ByteBuffer toUTF8(StringBuilder shaderString) {
        // we assume ASCII only, so 1 byte per char
        int len = shaderString.length();
        ByteBuffer buffer = BufferUtils.createByteBuffer(len);
        len = 0;
            len += MemoryUtil.memUTF8(shaderString, false, buffer, len);
        assert len == buffer.capacity() && len == buffer.remaining();
        return buffer;
    }*/

    boolean finish(GLGraphicsPipeline dest) {
        if (mFinalizedVertGLSL == null && mFinalizedVertSPIRV == null) {
            return false;
        }
        GLInterface gl = mDevice.getGL();
        int program = gl.glCreateProgram();
        if (program == 0) {
            return false;
        }

        // fragment shader is optional in OpenGL
        int frag = 0;
        if (mFinalizedFragGLSL != null || mFinalizedFragSPIRV != null) {
            if (mFinalizedFragSPIRV != null) {
                frag = GLUtil.glSpecializeShader(mDevice, GL_FRAGMENT_SHADER, mFinalizedFragSPIRV,
                        "main", mDevice.getDeviceBoundCache().getStats());
            } else {
                frag = GLUtil.glCompileShader(mDevice, GL_FRAGMENT_SHADER, mFinalizedFragGLSL,
                        mDevice.getDeviceBoundCache().getStats());
            }
            if (frag == 0) {
                gl.glDeleteProgram(program);
                return false;
            }
        }

        int vert;
        if (mFinalizedVertSPIRV != null) {
            vert = GLUtil.glSpecializeShader(mDevice, GL_VERTEX_SHADER, mFinalizedVertSPIRV,
                    "main", mDevice.getDeviceBoundCache().getStats());
        } else {
            vert = GLUtil.glCompileShader(mDevice, GL_VERTEX_SHADER, mFinalizedVertGLSL,
                    mDevice.getDeviceBoundCache().getStats());
        }
        if (vert == 0) {
            gl.glDeleteProgram(program);
            if (frag != 0) {
                gl.glDeleteShader(frag);
            }
            return false;
        }

        gl.glAttachShader(program, vert);
        if (frag != 0) {
            gl.glAttachShader(program, frag);
        }

        gl.glLinkProgram(program);

        String log = GLUtil.checkProgramLinked(gl, program);
        if (log != null) {
            try {
                if (mFinalizedVertGLSL != null) {
                    GLUtil.handleLinkError(mDevice.getLogger(),
                            new String[]{
                                    "Vertex GLSL",
                                    "Fragment GLSL"},
                            new String[]{
                                    MemoryUtil.memUTF8(mFinalizedVertGLSL),
                                    MemoryUtil.memUTF8Safe(mFinalizedFragGLSL)},
                            log);
                } else {
                    mDevice.getLogger().error("Program linking error: {}", log);
                }
                return false;
            } finally {
                Reference.reachabilityFence(mFinalizedVertGLSL);
                Reference.reachabilityFence(mFinalizedFragGLSL);
                gl.glDeleteProgram(program);
                if (frag != 0) {
                    gl.glDeleteShader(frag);
                }
                gl.glDeleteShader(vert);
            }
        }

        // the shaders can be detached after the linking
        gl.glDetachShader(program, vert);
        if (frag != 0) {
            gl.glDetachShader(program, frag);
            gl.glDeleteShader(frag);
        }
        // the shaders can be marked for deletion after the linking
        gl.glDeleteShader(vert);

        /*try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pLength = stack.mallocInt(1);
            IntBuffer pBinaryFormat = stack.mallocInt(1);
            glGetProgramiv(program, GL_PROGRAM_BINARY_LENGTH, pLength);
            int len = pLength.get(0);
            System.out.println(len);
            if (len > 0) {
                ByteBuffer pBinary = stack.malloc(len);
                glGetProgramBinary(program, pLength, pBinaryFormat, pBinary);
                System.out.println(pBinaryFormat.get(0));
                //System.out.println(MemoryUtil.memUTF8(pBinary));
                *//*for (int i = 0; i < len; ) {
                    System.out.print(pBinary.get(i++) & 0xFF);
                    System.out.print(", ");
                    if ((i & 31) == 0) {
                        System.out.println();
                    }
                }*//*
            }
        }*/

        @SharedPtr
        GLVertexArray vertexArray = mDevice.findOrCreateVertexArray(
                mInputLayout,
                mInputLayoutLabel);
        if (vertexArray == null) {
            gl.glDeleteProgram(program);
            return false;
        }

        if (mDevice.getCaps().hasDebugSupport()) {
            String label = mPipelineLabel;
            if (label != null && !label.isEmpty()) {
                label = "Arc3D_PIPE_" + label;
                label = label.substring(0, Math.min(label.length(),
                        mDevice.getCaps().maxLabelLength()));
                GLUtil.glObjectLabel(mDevice, GL43C.GL_PROGRAM, program, label);
            }
        }

        // Setup layout bindings if < OpenGL 4.2
        if (!mDevice.getCaps().shaderCaps().mUniformBindingSupport) {
            boolean boundProgram = false;
            //noinspection resource
            MemoryStack stack = stackGet();
            int stackPointer = stack.getPointer();
            for (var layout : mDescriptorSetLayouts) {
                for (int binding = 0; binding < layout.getBindingCount(); binding++) {
                    var info = layout.getDescriptorInfo(binding);
                    if (info.mVisibility == 0) {
                        continue;
                    }
                    switch (info.mType) {
                        case Engine.DescriptorType.kUniformBuffer -> {
                            try {
                                // block name
                                stack.nASCII(info.mName, true);
                                long uniformBlockNameEncoded = stack.getPointerAddress();
                                int index = gl.glGetUniformBlockIndex(program, uniformBlockNameEncoded);
                                assert index != GL_INVALID_INDEX;
                                gl.glUniformBlockBinding(program, index, binding);
                            } finally {
                                stack.setPointer(stackPointer);
                            }
                        }
                        case Engine.DescriptorType.kCombinedImageSampler,
                             Engine.DescriptorType.kSampledImage,
                             Engine.DescriptorType.kStorageImage,
                             Engine.DescriptorType.kUniformTexelBuffer,
                             Engine.DescriptorType.kStorageTexelBuffer -> {
                            // Assign texture units to sampler uniforms one time up front
                            if (!boundProgram) {
                                // We can bind program here, since we will bind this pipeline immediately
                                gl.glUseProgram(program);
                                boundProgram = true;
                            }
                            try {
                                stack.nASCII(info.mName, true);
                                long nameEncoded = stack.getPointerAddress();
                                int location = gl.glGetUniformLocation(program, nameEncoded);
                                assert location != -1;
                                gl.glUniform1i(location, binding); // <- binding is just the texture unit (index)
                            } finally {
                                stack.setPointer(stackPointer);
                            }
                        }
                        case Engine.DescriptorType.kStorageBuffer -> {
                            // ARB_shader_storage_buffer_object allows you to use layout(binding=N)
                            // even without ARB_shading_language_420pack
                        }
                        case Engine.DescriptorType.kInputAttachment,
                             Engine.DescriptorType.kAccelerationStructure -> {
                            assert false;
                        }
                    }
                }
            }
        }

        dest.init(new GLProgram(mDevice, program),
                vertexArray,
                mPrimitiveType,
                mBlendInfo,
                mDepthStencilSettings/*,
                mUniformHandler.mUniforms,
                mUniformHandler.mCurrentOffset,
                mUniformHandler.mSamplers,
                mGPImpl*/);
        return true;
    }
}

/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

import org.lwjgl.system.NativeType;

import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Interface for gl* function access between OpenGL 4.6 Core and OpenGL ES 3.2,
 * plus some extensions, depending on GLCapabilities or GLESCapabilities.
 * <p>
 * No javadoc here, please refer to LWJGL javadoc and OpenGL specification.
 *
 * @see GLCaps
 */
public class GLInterface extends GLInterfaceCommon {

    long
            glDrawElementsBaseVertex,
            glDrawElementsInstancedBaseVertex,
            glShaderBinary,
            glDrawArraysInstancedBaseInstance,
            glDrawElementsInstancedBaseVertexBaseInstance,
            glTexStorage2D,
            glInvalidateBufferSubData,
            glInvalidateFramebuffer,
            glCopyImageSubData,
            glObjectLabel,
            glBindVertexBuffer,
            glVertexAttribFormat,
            glVertexAttribIFormat,
            glVertexAttribBinding,
            glVertexBindingDivisor,
            glBufferStorage,
            glTextureBarrier,
            glCreateBuffers,
            glNamedBufferData,
            glNamedBufferSubData,
            glMapNamedBufferRange,
            glUnmapNamedBuffer,
            glNamedBufferStorage,
            glCopyNamedBufferSubData,
            glCreateTextures,
            glTextureParameteri,
            glTextureParameteriv,
            glTextureSubImage2D,
            glTextureStorage2D,
            glCreateVertexArrays,
            glEnableVertexArrayAttrib,
            glVertexArrayAttribFormat,
            glVertexArrayAttribIFormat,
            glVertexArrayAttribBinding,
            glVertexArrayBindingDivisor,
            glBindTextureUnit,
            glSpecializeShader;

    GLInterface() {
    }

    public void glDrawElementsBaseVertex(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                                         @NativeType("GLenum") int type, @NativeType("void const *") long indices,
                                         @NativeType("GLint") int basevertex) {
        long __functionAddress = glDrawElementsBaseVertex;
        assert __functionAddress != NULL;
        callPV(mode, count, type, indices, basevertex, __functionAddress);
    }

    public void glDrawElementsInstancedBaseVertex(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                                                  @NativeType("GLenum") int type,
                                                  @NativeType("void const *") long indices,
                                                  @NativeType("GLsizei") int instancecount,
                                                  @NativeType("GLint") int basevertex) {
        long __functionAddress = glDrawElementsInstancedBaseVertex;
        assert __functionAddress != NULL;
        callPV(mode, count, type, indices, instancecount, basevertex, __functionAddress);
    }

    public void glShaderBinary(@NativeType("GLsizei") int count, @NativeType("GLuint const *") long shaders,
                               @NativeType("GLenum") int binaryformat,
                               @NativeType("void const *") long binary, @NativeType("GLsizei") int length) {
        long __functionAddress = glShaderBinary;
        assert __functionAddress != NULL;
        callPPV(count, shaders, binaryformat, binary, length, __functionAddress);
    }

    public void glDrawArraysInstancedBaseInstance(@NativeType("GLenum") int mode, @NativeType("GLint") int first,
                                                  @NativeType("GLsizei") int count,
                                                  @NativeType("GLsizei") int instancecount,
                                                  @NativeType("GLuint") int baseinstance) {
        long __functionAddress = glDrawArraysInstancedBaseInstance;
        assert __functionAddress != NULL;
        callV(mode, first, count, instancecount, baseinstance, __functionAddress);
    }

    public void glDrawElementsInstancedBaseVertexBaseInstance(@NativeType("GLenum") int mode,
                                                              @NativeType("GLsizei") int count,
                                                              @NativeType("GLenum") int type,
                                                              @NativeType("void const *") long indices,
                                                              @NativeType("GLsizei") int instancecount,
                                                              @NativeType("GLint") int basevertex,
                                                              @NativeType("GLuint") int baseinstance) {
        long __functionAddress = glDrawElementsInstancedBaseVertexBaseInstance;
        assert __functionAddress != NULL;
        callPV(mode, count, type, indices, instancecount, basevertex, baseinstance, __functionAddress);
    }

    public void glTexStorage2D(@NativeType("GLenum") int target, @NativeType("GLsizei") int levels,
                               @NativeType("GLenum") int internalformat, @NativeType("GLsizei") int width,
                               @NativeType("GLsizei") int height) {
        long __functionAddress = glTexStorage2D;
        assert __functionAddress != NULL;
        callV(target, levels, internalformat, width, height, __functionAddress);
    }

    public void glInvalidateBufferSubData(@NativeType("GLuint") int buffer, @NativeType("GLintptr") long offset,
                                          @NativeType("GLsizeiptr") long length) {
        long __functionAddress = glInvalidateBufferSubData;
        assert __functionAddress != NULL;
        callPPV(buffer, offset, length, __functionAddress);
    }

    public void glInvalidateFramebuffer(@NativeType("GLenum") int target,
                                        @NativeType("GLsizei") int numAttachments,
                                        @NativeType("GLenum const *") long attachments) {
        long __functionAddress = glInvalidateFramebuffer;
        assert __functionAddress != NULL;
        callPV(target, numAttachments, attachments, __functionAddress);
    }

    public void glCopyImageSubData(@NativeType("GLuint") int srcName, @NativeType("GLenum") int srcTarget,
                                   @NativeType("GLint") int srcLevel, @NativeType("GLint") int srcX,
                                   @NativeType("GLint") int srcY, @NativeType("GLint") int srcZ,
                                   @NativeType("GLuint") int dstName, @NativeType("GLenum") int dstTarget,
                                   @NativeType("GLint") int dstLevel, @NativeType("GLint") int dstX,
                                   @NativeType("GLint") int dstY, @NativeType("GLint") int dstZ,
                                   @NativeType("GLsizei") int srcWidth, @NativeType("GLsizei") int srcHeight,
                                   @NativeType("GLsizei") int srcDepth) {
        long __functionAddress = glCopyImageSubData;
        assert __functionAddress != NULL;
        callV(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
                srcWidth, srcHeight, srcDepth, __functionAddress);
    }

    public void glObjectLabel(@NativeType("GLenum") int identifier, @NativeType("GLuint") int name,
                              @NativeType("GLsizei") int length, @NativeType("GLchar const *") long label) {
        long __functionAddress = glObjectLabel;
        assert __functionAddress != NULL;
        callPV(identifier, name, length, label, __functionAddress);
    }

    public void glBindVertexBuffer(@NativeType("GLuint") int bindingindex, @NativeType("GLuint") int buffer,
                                   @NativeType("GLintptr") long offset, @NativeType("GLsizei") int stride) {
        long __functionAddress = glBindVertexBuffer;
        assert __functionAddress != NULL;
        callPV(bindingindex, buffer, offset, stride, __functionAddress);
    }

    public void glVertexAttribFormat(@NativeType("GLuint") int attribindex, @NativeType("GLint") int size,
                                     @NativeType("GLenum") int type, @NativeType("GLboolean") boolean normalized,
                                     @NativeType("GLuint") int relativeoffset) {
        long __functionAddress = glVertexAttribFormat;
        assert __functionAddress != NULL;
        callV(attribindex, size, type, normalized, relativeoffset, __functionAddress);
    }

    public void glVertexAttribIFormat(@NativeType("GLuint") int attribindex, @NativeType("GLint") int size,
                                      @NativeType("GLenum") int type, @NativeType("GLuint") int relativeoffset) {
        long __functionAddress = glVertexAttribIFormat;
        assert __functionAddress != NULL;
        callV(attribindex, size, type, relativeoffset, __functionAddress);
    }

    public void glVertexAttribBinding(@NativeType("GLuint") int attribindex, @NativeType("GLuint") int bindingindex) {
        long __functionAddress = glVertexAttribBinding;
        assert __functionAddress != NULL;
        callV(attribindex, bindingindex, __functionAddress);
    }

    public void glVertexBindingDivisor(@NativeType("GLuint") int bindingindex, @NativeType("GLuint") int divisor) {
        long __functionAddress = glVertexBindingDivisor;
        assert __functionAddress != NULL;
        callV(bindingindex, divisor, __functionAddress);
    }

    public void glBufferStorage(@NativeType("GLenum") int target, @NativeType("GLsizeiptr") long size,
                                @NativeType("void const *") long data, @NativeType("GLbitfield") int flags) {
        long __functionAddress = glBufferStorage;
        assert __functionAddress != NULL;
        callPPV(target, size, data, flags, __functionAddress);
    }

    public void glTextureBarrier() {
        long __functionAddress = glTextureBarrier;
        assert __functionAddress != NULL;
        callV(__functionAddress);
    }

    public void glCreateBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") long buffers) {
        long __functionAddress = glCreateBuffers;
        assert __functionAddress != NULL;
        callPV(n, buffers, __functionAddress);
    }

    public void glCreateBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] buffers) {
        long __functionAddress = glCreateBuffers;
        assert __functionAddress != NULL;
        callPV(n, buffers, __functionAddress);
    }

    public void glNamedBufferData(@NativeType("GLuint") int buffer, @NativeType("GLsizeiptr") long size,
                                  @NativeType("void const *") long data, @NativeType("GLenum") int usage) {
        long __functionAddress = glNamedBufferData;
        assert __functionAddress != NULL;
        callPPV(buffer, size, data, usage, __functionAddress);
    }

    public void glNamedBufferSubData(@NativeType("GLuint") int buffer, @NativeType("GLintptr") long offset,
                                     @NativeType("GLsizeiptr") long size, @NativeType("void const *") long data) {
        long __functionAddress = glNamedBufferSubData;
        assert __functionAddress != NULL;
        callPPPV(buffer, offset, size, data, __functionAddress);
    }

    @NativeType("void *")
    public long glMapNamedBufferRange(@NativeType("GLuint") int buffer, @NativeType("GLintptr") long offset,
                                      @NativeType("GLsizeiptr") long length, @NativeType("GLbitfield") int access) {
        long __functionAddress = glMapNamedBufferRange;
        assert __functionAddress != NULL;
        return callPPP(buffer, offset, length, access, __functionAddress);
    }

    @NativeType("GLboolean")
    public boolean glUnmapNamedBuffer(@NativeType("GLuint") int buffer) {
        long __functionAddress = glUnmapNamedBuffer;
        assert __functionAddress != NULL;
        return callZ(buffer, __functionAddress);
    }

    public void glNamedBufferStorage(@NativeType("GLuint") int buffer, @NativeType("GLsizeiptr") long size,
                                     @NativeType("void const *") long data, @NativeType("GLbitfield") int flags) {
        long __functionAddress = glNamedBufferStorage;
        assert __functionAddress != NULL;
        callPPV(buffer, size, data, flags, __functionAddress);
    }

    public void glCopyNamedBufferSubData(@NativeType("GLuint") int readBuffer, @NativeType("GLuint") int writeBuffer,
                                         @NativeType("GLintptr") long readOffset,
                                         @NativeType("GLintptr") long writeOffset,
                                         @NativeType("GLsizeiptr") long size) {
        long __functionAddress = glCopyNamedBufferSubData;
        assert __functionAddress != NULL;
        callPPPV(readBuffer, writeBuffer, readOffset, writeOffset, size, __functionAddress);
    }

    public void glCreateTextures(@NativeType("GLenum") int target,
                                 @NativeType("GLsizei") int n, @NativeType("GLuint *") long textures) {
        long __functionAddress = glCreateTextures;
        assert __functionAddress != NULL;
        callPV(target, n, textures, __functionAddress);
    }

    public void glCreateTextures(@NativeType("GLenum") int target,
                                 @NativeType("GLsizei") int n, @NativeType("GLuint *") int[] textures) {
        long __functionAddress = glCreateTextures;
        assert __functionAddress != NULL;
        callPV(target, n, textures, __functionAddress);
    }

    public void glTextureParameteri(@NativeType("GLuint") int texture, @NativeType("GLenum") int pname,
                                    @NativeType("GLint") int param) {
        long __functionAddress = glTextureParameteri;
        assert __functionAddress != NULL;
        callV(texture, pname, param, __functionAddress);
    }

    public void glTextureParameteriv(@NativeType("GLuint") int texture, @NativeType("GLenum") int pname,
                                     @NativeType("GLint const *") long params) {
        long __functionAddress = glTextureParameteriv;
        assert __functionAddress != NULL;
        callPV(texture, pname, params, __functionAddress);
    }

    public void glTextureSubImage2D(@NativeType("GLuint") int texture, @NativeType("GLint") int level,
                                    @NativeType("GLint") int xoffset, @NativeType("GLint") int yoffset,
                                    @NativeType("GLsizei") int width, @NativeType("GLsizei") int height,
                                    @NativeType("GLenum") int format, @NativeType("GLenum") int type,
                                    @NativeType("void const *") long pixels) {
        long __functionAddress = glTextureSubImage2D;
        assert __functionAddress != NULL;
        callPV(texture, level, xoffset, yoffset, width, height, format, type, pixels, __functionAddress);
    }

    public void glTextureStorage2D(@NativeType("GLuint") int texture, @NativeType("GLsizei") int levels,
                                   @NativeType("GLenum") int internalformat, @NativeType("GLsizei") int width,
                                   @NativeType("GLsizei") int height) {
        long __functionAddress = glTextureStorage2D;
        assert __functionAddress != NULL;
        callV(texture, levels, internalformat, width, height, __functionAddress);
    }

    public void glCreateVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint *") long arrays) {
        long __functionAddress = glCreateVertexArrays;
        assert __functionAddress != NULL;
        callPV(n, arrays, __functionAddress);
    }

    public void glCreateVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] arrays) {
        long __functionAddress = glCreateVertexArrays;
        assert __functionAddress != NULL;
        callPV(n, arrays, __functionAddress);
    }

    public void glEnableVertexArrayAttrib(@NativeType("GLuint") int vaobj, @NativeType("GLuint") int index) {
        long __functionAddress = glEnableVertexArrayAttrib;
        assert __functionAddress != NULL;
        callV(vaobj, index, __functionAddress);
    }

    public void glVertexArrayAttribFormat(@NativeType("GLuint") int vaobj, @NativeType("GLuint") int attribindex,
                                          @NativeType("GLint") int size, @NativeType("GLenum") int type,
                                          @NativeType("GLboolean") boolean normalized,
                                          @NativeType("GLuint") int relativeoffset) {
        long __functionAddress = glVertexArrayAttribFormat;
        assert __functionAddress != NULL;
        callV(vaobj, attribindex, size, type, normalized, relativeoffset, __functionAddress);
    }

    public void glVertexArrayAttribIFormat(@NativeType("GLuint") int vaobj, @NativeType("GLuint") int attribindex,
                                           @NativeType("GLint") int size, @NativeType("GLenum") int type,
                                           @NativeType("GLuint") int relativeoffset) {
        long __functionAddress = glVertexArrayAttribIFormat;
        assert __functionAddress != NULL;
        callV(vaobj, attribindex, size, type, relativeoffset, __functionAddress);
    }

    public void glVertexArrayAttribBinding(@NativeType("GLuint") int vaobj, @NativeType("GLuint") int attribindex,
                                           @NativeType("GLuint") int bindingindex) {
        long __functionAddress = glVertexArrayAttribBinding;
        assert __functionAddress != NULL;
        callV(vaobj, attribindex, bindingindex, __functionAddress);
    }

    public void glVertexArrayBindingDivisor(@NativeType("GLuint") int vaobj, @NativeType("GLuint") int bindingindex,
                                            @NativeType("GLuint") int divisor) {
        long __functionAddress = glVertexArrayBindingDivisor;
        assert __functionAddress != NULL;
        callV(vaobj, bindingindex, divisor, __functionAddress);
    }

    public void glBindTextureUnit(@NativeType("GLuint") int unit, @NativeType("GLuint") int texture) {
        long __functionAddress = glBindTextureUnit;
        assert __functionAddress != NULL;
        callV(unit, texture, __functionAddress);
    }

    public void glSpecializeShader(@NativeType("GLuint") int shader, @NativeType("GLchar const *") long pEntryPoint,
                                   @NativeType("GLuint") int numSpecializationConstants,
                                   @NativeType("GLuint const *") long pConstantIndex,
                                   @NativeType("GLuint const *") long pConstantValue) {
        long __functionAddress = glSpecializeShader;
        assert __functionAddress != NULL;
        callPPPV(shader, pEntryPoint, numSpecializationConstants, pConstantIndex, pConstantValue, __functionAddress);
    }
}

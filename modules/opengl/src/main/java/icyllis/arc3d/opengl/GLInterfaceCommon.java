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

import org.jspecify.annotations.Nullable;
import org.lwjgl.system.NativeType;

import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.MemoryUtil.memUTF8Safe;

/**
 * OpenGL 3.1 Core and OpenGL ES 3.0 have a common subset.
 * <p>
 * No javadoc here, please refer to LWJGL javadoc and OpenGL specification.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class GLInterfaceCommon {

    // JNI since LWJGL 3.2.2

    long
            glEnable,
            glDisable,
            glFrontFace,
            glLineWidth,
            glGenTextures,
            glTexParameteri,
            glTexParameteriv,
            glTexImage2D,
            glTexSubImage2D,
            glCopyTexSubImage2D,
            glDeleteTextures,
            glBindTexture,
            glPixelStorei,
            glBlendFunc,
            glColorMask,
            glDepthFunc,
            glDepthMask,
            glStencilOp,
            glStencilFunc,
            glStencilMask,
            glDrawArrays,
            glDrawElements,
            glFlush,
            glFinish,
            glGetError,
            glGetString,
            glGetIntegerv,
            glScissor,
            glViewport,
            glActiveTexture,
            glBlendEquation,
            glGenBuffers,
            glDeleteBuffers,
            glBindBuffer,
            glBufferData,
            glBufferSubData,
            glUnmapBuffer,
            glDrawBuffers,
            glStencilOpSeparate,
            glStencilFuncSeparate,
            glStencilMaskSeparate,
            glCreateProgram,
            glDeleteProgram,
            glCreateShader,
            glDeleteShader,
            glAttachShader,
            glDetachShader,
            glShaderSource,
            glCompileShader,
            glLinkProgram,
            glUseProgram,
            glGetShaderiv,
            glGetProgramiv,
            glGetShaderInfoLog,
            glGetProgramInfoLog,
            glGetUniformLocation,
            glUniform1i,
            glEnableVertexAttribArray,
            glVertexAttribPointer,
            glVertexAttribIPointer,
            glGenVertexArrays,
            glDeleteVertexArrays,
            glBindVertexArray,
            glGenFramebuffers,
            glDeleteFramebuffers,
            glBindFramebuffer,
            glCheckFramebufferStatus,
            glFramebufferTexture2D,
            glFramebufferRenderbuffer,
            glBlitFramebuffer,
            glClearBufferiv,
            glClearBufferfv,
            glClearBufferfi,
            glBindBufferBase,
            glBindBufferRange,
            glGenRenderbuffers,
            glDeleteRenderbuffers,
            glBindRenderbuffer,
            glRenderbufferStorage,
            glRenderbufferStorageMultisample,
            glMapBufferRange,
            glDrawArraysInstanced,
            glDrawElementsInstanced,
            glCopyBufferSubData,
            glGetUniformBlockIndex,
            glUniformBlockBinding,
            glFenceSync,
            glDeleteSync,
            glClientWaitSync,
            glGenSamplers,
            glDeleteSamplers,
            glBindSampler,
            glSamplerParameteri,
            glSamplerParameterf,
            glVertexAttribDivisor;

    GLInterfaceCommon() {
    }

    public final void glEnable(@NativeType("GLenum") int target) {
        long __functionAddress = glEnable;
        callV(target, __functionAddress);
    }

    public final void glDisable(@NativeType("GLenum") int target) {
        long __functionAddress = glDisable;
        callV(target, __functionAddress);
    }

    public final void glFrontFace(@NativeType("GLenum") int dir) {
        long __functionAddress = glFrontFace;
        callV(dir, __functionAddress);
    }

    public final void glLineWidth(@NativeType("GLfloat") float width) {
        long __functionAddress = glLineWidth;
        callV(width, __functionAddress);
    }

    public final void glGenTextures(@NativeType("GLsizei") int n, @NativeType("GLuint *") long textures) {
        long __functionAddress = glGenTextures;
        callPV(n, textures, __functionAddress);
    }

    public final void glGenTextures(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] textures) {
        long __functionAddress = glGenTextures;
        callPV(n, textures, __functionAddress);
    }

    public final void glTexParameteri(@NativeType("GLenum") int target, @NativeType("GLenum") int pname,
                                      @NativeType("GLint") int param) {
        long __functionAddress = glTexParameteri;
        callV(target, pname, param, __functionAddress);
    }

    public final void glTexParameteriv(@NativeType("GLenum") int target, @NativeType("GLenum") int pname,
                                       @NativeType("GLint const *") long params) {
        long __functionAddress = glTexParameteriv;
        callPV(target, pname, params, __functionAddress);
    }

    public final void glTexImage2D(@NativeType("GLenum") int target, @NativeType("GLint") int level,
                                   @NativeType("GLint") int internalformat, @NativeType("GLsizei") int width,
                                   @NativeType("GLsizei") int height, @NativeType("GLint") int border,
                                   @NativeType("GLenum") int format, @NativeType("GLenum") int type,
                                   @NativeType("void const *") long pixels) {
        long __functionAddress = glTexImage2D;
        callPV(target, level, internalformat, width, height, border, format, type, pixels, __functionAddress);
    }

    public final void glTexSubImage2D(@NativeType("GLenum") int target, @NativeType("GLint") int level,
                                      @NativeType("GLint") int xoffset, @NativeType("GLint") int yoffset,
                                      @NativeType("GLsizei") int width, @NativeType("GLsizei") int height,
                                      @NativeType("GLenum") int format, @NativeType("GLenum") int type,
                                      @NativeType("void const *") long pixels) {
        long __functionAddress = glTexSubImage2D;
        callPV(target, level, xoffset, yoffset, width, height, format, type, pixels, __functionAddress);
    }

    public final void glCopyTexSubImage2D(@NativeType("GLenum") int target, @NativeType("GLint") int level,
                                          @NativeType("GLint") int xoffset, @NativeType("GLint") int yoffset,
                                          @NativeType("GLint") int x, @NativeType("GLint") int y,
                                          @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
        long __functionAddress = glCopyTexSubImage2D;
        callV(target, level, xoffset, yoffset, x, y, width, height, __functionAddress);
    }

    public final void glDeleteTextures(@NativeType("GLsizei") int n, @NativeType("GLuint *") long textures) {
        long __functionAddress = glDeleteTextures;
        callPV(n, textures, __functionAddress);
    }

    public final void glDeleteTextures(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] textures) {
        long __functionAddress = glDeleteTextures;
        callPV(n, textures, __functionAddress);
    }

    public final void glBindTexture(@NativeType("GLenum") int target, @NativeType("GLuint") int texture) {
        long __functionAddress = glBindTexture;
        callV(target, texture, __functionAddress);
    }

    public final void glPixelStorei(@NativeType("GLenum") int pname, @NativeType("GLint") int param) {
        long __functionAddress = glPixelStorei;
        callV(pname, param, __functionAddress);
    }

    public final void glBlendFunc(@NativeType("GLenum") int sfactor, @NativeType("GLenum") int dfactor) {
        long __functionAddress = glBlendFunc;
        callV(sfactor, dfactor, __functionAddress);
    }

    public final void glColorMask(@NativeType("GLboolean") boolean red, @NativeType("GLboolean") boolean green,
                                  @NativeType("GLboolean") boolean blue, @NativeType("GLboolean") boolean alpha) {
        long __functionAddress = glColorMask;
        callV(red, green, blue, alpha, __functionAddress);
    }

    public final void glDepthFunc(@NativeType("GLenum") int func) {
        long __functionAddress = glDepthFunc;
        callV(func, __functionAddress);
    }

    public final void glDepthMask(@NativeType("GLboolean") boolean flag) {
        long __functionAddress = glDepthMask;
        callV(flag, __functionAddress);
    }

    public final void glStencilOp(@NativeType("GLenum") int sfail, @NativeType("GLenum") int dpfail,
                                  @NativeType("GLenum") int dppass) {
        long __functionAddress = glStencilOp;
        callV(sfail, dpfail, dppass, __functionAddress);
    }

    public final void glStencilFunc(@NativeType("GLenum") int func, @NativeType("GLint") int ref,
                                    @NativeType("GLuint") int mask) {
        long __functionAddress = glStencilFunc;
        callV(func, ref, mask, __functionAddress);
    }

    public final void glStencilMask(@NativeType("GLuint") int mask) {
        long __functionAddress = glStencilMask;
        callV(mask, __functionAddress);
    }

    public final void glDrawArrays(@NativeType("GLenum") int mode, @NativeType("GLint") int first,
                                   @NativeType("GLsizei") int count) {
        long __functionAddress = glDrawArrays;
        callV(mode, first, count, __functionAddress);
    }

    public final void glDrawElements(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                                     @NativeType("GLenum") int type, @NativeType("void const *") long indices) {
        long __functionAddress = glDrawElements;
        callPV(mode, count, type, indices, __functionAddress);
    }

    public final void glFlush() {
        long __functionAddress = glFlush;
        callV(__functionAddress);
    }

    public final void glFinish() {
        long __functionAddress = glFinish;
        callV(__functionAddress);
    }

    @NativeType("GLenum")
    public final int glGetError() {
        long __functionAddress = glGetError;
        return callI(__functionAddress);
    }

    @Nullable
    @NativeType("GLubyte const *")
    public final String glGetString(@NativeType("GLenum") int name) {
        long __functionAddress = glGetString;
        long __result = callP(name, __functionAddress);
        return memUTF8Safe(__result);
    }

    public final void glGetIntegerv(@NativeType("GLenum") int pname, @NativeType("GLint *") long params) {
        long __functionAddress = glGetIntegerv;
        callPV(pname, params, __functionAddress);
    }

    public final void glGetIntegerv(@NativeType("GLenum") int pname, @NativeType("GLint *") int[] params) {
        long __functionAddress = glGetIntegerv;
        callPV(pname, params, __functionAddress);
    }

    public final void glScissor(@NativeType("GLint") int x, @NativeType("GLint") int y,
                                @NativeType("GLsizei") int width,
                                @NativeType("GLsizei") int height) {
        long __functionAddress = glScissor;
        callV(x, y, width, height, __functionAddress);
    }

    public final void glViewport(@NativeType("GLint") int x, @NativeType("GLint") int y,
                                 @NativeType("GLsizei") int width,
                                 @NativeType("GLsizei") int height) {
        long __functionAddress = glViewport;
        callV(x, y, width, height, __functionAddress);
    }

    public final void glActiveTexture(@NativeType("GLenum") int texture) {
        long __functionAddress = glActiveTexture;
        callV(texture, __functionAddress);
    }

    public final void glBlendEquation(@NativeType("GLenum") int mode) {
        long __functionAddress = glBlendEquation;
        callV(mode, __functionAddress);
    }

    public final void glGenBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") long buffers) {
        long __functionAddress = glGenBuffers;
        callPV(n, buffers, __functionAddress);
    }

    public final void glGenBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] buffers) {
        long __functionAddress = glGenBuffers;
        callPV(n, buffers, __functionAddress);
    }

    public final void glDeleteBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") long buffers) {
        long __functionAddress = glDeleteBuffers;
        callPV(n, buffers, __functionAddress);
    }

    public final void glDeleteBuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] buffers) {
        long __functionAddress = glDeleteBuffers;
        callPV(n, buffers, __functionAddress);
    }

    public final void glBindBuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int buffer) {
        long __functionAddress = glBindBuffer;
        callV(target, buffer, __functionAddress);
    }

    public final void glBufferData(@NativeType("GLenum") int target, @NativeType("GLsizeiptr") long size,
                                   @NativeType("void const *") long data, @NativeType("GLenum") int usage) {
        long __functionAddress = glBufferData;
        callPPV(target, size, data, usage, __functionAddress);
    }

    public final void glBufferSubData(@NativeType("GLenum") int target, @NativeType("GLintptr") long offset,
                                      @NativeType("GLsizeiptr") long size, @NativeType("void const *") long data) {
        long __functionAddress = glBufferSubData;
        callPPPV(target, offset, size, data, __functionAddress);
    }

    @NativeType("GLboolean")
    public final boolean glUnmapBuffer(@NativeType("GLenum") int target) {
        long __functionAddress = glUnmapBuffer;
        return callZ(target, __functionAddress);
    }

    public final void glDrawBuffers(@NativeType("GLsizei") int n, @NativeType("GLenum const *") long bufs) {
        long __functionAddress = glDrawBuffers;
        callPV(n, bufs, __functionAddress);
    }

    public final void glDrawBuffers(@NativeType("GLsizei") int n, @NativeType("GLenum const *") int[] bufs) {
        long __functionAddress = glDrawBuffers;
        callPV(n, bufs, __functionAddress);
    }

    public final void glStencilOpSeparate(@NativeType("GLenum") int face, @NativeType("GLenum") int sfail,
                                          @NativeType("GLenum") int dpfail, @NativeType("GLenum") int dppass) {
        long __functionAddress = glStencilOpSeparate;
        callV(face, sfail, dpfail, dppass, __functionAddress);
    }

    public final void glStencilFuncSeparate(@NativeType("GLenum") int face, @NativeType("GLenum") int func,
                                            @NativeType("GLint") int ref, @NativeType("GLuint") int mask) {
        long __functionAddress = glStencilFuncSeparate;
        callV(face, func, ref, mask, __functionAddress);
    }

    public final void glStencilMaskSeparate(@NativeType("GLenum") int face, @NativeType("GLuint") int mask) {
        long __functionAddress = glStencilMaskSeparate;
        callV(face, mask, __functionAddress);
    }

    @NativeType("GLuint")
    public final int glCreateProgram() {
        long __functionAddress = glCreateProgram;
        return callI(__functionAddress);
    }

    public final void glDeleteProgram(@NativeType("GLuint") int program) {
        long __functionAddress = glDeleteProgram;
        callV(program, __functionAddress);
    }

    @NativeType("GLuint")
    public final int glCreateShader(@NativeType("GLenum") int type) {
        long __functionAddress = glCreateShader;
        return callI(type, __functionAddress);
    }

    public final void glDeleteShader(@NativeType("GLuint") int shader) {
        long __functionAddress = glDeleteShader;
        callV(shader, __functionAddress);
    }

    public final void glAttachShader(@NativeType("GLuint") int program, @NativeType("GLuint") int shader) {
        long __functionAddress = glAttachShader;
        callV(program, shader, __functionAddress);
    }

    public final void glDetachShader(@NativeType("GLuint") int program, @NativeType("GLuint") int shader) {
        long __functionAddress = glDetachShader;
        callV(program, shader, __functionAddress);
    }

    public final void glShaderSource(@NativeType("GLuint") int shader, @NativeType("GLsizei") int count,
                                     @NativeType("GLchar const * const *") long strings,
                                     @NativeType("GLint const *") long length) {
        long __functionAddress = glShaderSource;
        callPPV(shader, count, strings, length, __functionAddress);
    }

    public final void glCompileShader(@NativeType("GLuint") int shader) {
        long __functionAddress = glCompileShader;
        callV(shader, __functionAddress);
    }

    public final void glLinkProgram(@NativeType("GLuint") int program) {
        long __functionAddress = glLinkProgram;
        callV(program, __functionAddress);
    }

    public final void glUseProgram(@NativeType("GLuint") int program) {
        long __functionAddress = glUseProgram;
        callV(program, __functionAddress);
    }

    public final void glGetShaderiv(@NativeType("GLuint") int shader, @NativeType("GLenum") int pname,
                                    @NativeType("GLint *") long params) {
        long __functionAddress = glGetShaderiv;
        callPV(shader, pname, params, __functionAddress);
    }

    public final void glGetShaderiv(@NativeType("GLuint") int shader, @NativeType("GLenum") int pname,
                                    @NativeType("GLint *") int[] params) {
        long __functionAddress = glGetShaderiv;
        callPV(shader, pname, params, __functionAddress);
    }

    public final void glGetProgramiv(@NativeType("GLuint") int program, @NativeType("GLenum") int pname,
                                     @NativeType("GLint *") long params) {
        long __functionAddress = glGetProgramiv;
        callPV(program, pname, params, __functionAddress);
    }

    public final void glGetProgramiv(@NativeType("GLuint") int program, @NativeType("GLenum") int pname,
                                     @NativeType("GLint *") int[] params) {
        long __functionAddress = glGetProgramiv;
        callPV(program, pname, params, __functionAddress);
    }

    public final void glGetShaderInfoLog(@NativeType("GLuint") int shader, @NativeType("GLsizei") int bufSize,
                                         @NativeType("GLsizei *") long length, @NativeType("GLchar *") long infoLog) {
        long __functionAddress = glGetShaderInfoLog;
        callPPV(shader, bufSize, length, infoLog, __functionAddress);
    }

    public final void glGetProgramInfoLog(@NativeType("GLuint") int program, @NativeType("GLsizei") int bufSize,
                                          @NativeType("GLsizei *") long length, @NativeType("GLchar *") long infoLog) {
        long __functionAddress = glGetProgramInfoLog;
        callPPV(program, bufSize, length, infoLog, __functionAddress);
    }

    @NativeType("GLint")
    public final int glGetUniformLocation(@NativeType("GLuint") int program, @NativeType("GLchar const *") long name) {
        long __functionAddress = glGetUniformLocation;
        return callPI(program, name, __functionAddress);
    }

    public final void glUniform1i(@NativeType("GLint") int location, @NativeType("GLint") int v0) {
        long __functionAddress = glUniform1i;
        callV(location, v0, __functionAddress);
    }

    public final void glEnableVertexAttribArray(@NativeType("GLuint") int index) {
        long __functionAddress = glEnableVertexAttribArray;
        callV(index, __functionAddress);
    }

    public final void glVertexAttribPointer(@NativeType("GLuint") int index, @NativeType("GLint") int size,
                                            @NativeType("GLenum") int type, @NativeType("GLboolean") boolean normalized,
                                            @NativeType("GLsizei") int stride,
                                            @NativeType("void const *") long pointer) {
        long __functionAddress = glVertexAttribPointer;
        callPV(index, size, type, normalized, stride, pointer, __functionAddress);
    }

    public final void glVertexAttribIPointer(@NativeType("GLuint") int index, @NativeType("GLint") int size,
                                             @NativeType("GLenum") int type, @NativeType("GLsizei") int stride,
                                             @NativeType("void const *") long pointer) {
        long __functionAddress = glVertexAttribIPointer;
        callPV(index, size, type, stride, pointer, __functionAddress);
    }

    public final void glGenVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint *") long arrays) {
        long __functionAddress = glGenVertexArrays;
        callPV(n, arrays, __functionAddress);
    }

    public final void glGenVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] arrays) {
        long __functionAddress = glGenVertexArrays;
        callPV(n, arrays, __functionAddress);
    }

    public final void glDeleteVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint const *") long arrays) {
        long __functionAddress = glDeleteVertexArrays;
        callPV(n, arrays, __functionAddress);
    }

    public final void glDeleteVertexArrays(@NativeType("GLsizei") int n, @NativeType("GLuint const *") int[] arrays) {
        long __functionAddress = glDeleteVertexArrays;
        callPV(n, arrays, __functionAddress);
    }

    public final void glBindVertexArray(@NativeType("GLuint") int array) {
        long __functionAddress = glBindVertexArray;
        callV(array, __functionAddress);
    }

    public final void glGenFramebuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") long framebuffers) {
        long __functionAddress = glGenFramebuffers;
        callPV(n, framebuffers, __functionAddress);
    }

    public final void glGenFramebuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] framebuffers) {
        long __functionAddress = glGenFramebuffers;
        callPV(n, framebuffers, __functionAddress);
    }

    public final void glDeleteFramebuffers(@NativeType("GLsizei") int n,
                                           @NativeType("GLuint const *") long framebuffers) {
        long __functionAddress = glDeleteFramebuffers;
        callPV(n, framebuffers, __functionAddress);
    }

    public final void glDeleteFramebuffers(@NativeType("GLsizei") int n,
                                           @NativeType("GLuint const *") int[] framebuffers) {
        long __functionAddress = glDeleteFramebuffers;
        callPV(n, framebuffers, __functionAddress);
    }

    public final void glBindFramebuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int framebuffer) {
        long __functionAddress = glBindFramebuffer;
        callV(target, framebuffer, __functionAddress);
    }

    @NativeType("GLenum")
    public final int glCheckFramebufferStatus(@NativeType("GLenum") int target) {
        long __functionAddress = glCheckFramebufferStatus;
        return callI(target, __functionAddress);
    }

    public final void glFramebufferTexture2D(@NativeType("GLenum") int target, @NativeType("GLenum") int attachment,
                                             @NativeType("GLenum") int textarget, @NativeType("GLuint") int texture,
                                             @NativeType("GLint") int level) {
        long __functionAddress = glFramebufferTexture2D;
        callV(target, attachment, textarget, texture, level, __functionAddress);
    }

    public final void glFramebufferRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLenum") int attachment,
                                                @NativeType("GLenum") int renderbuffertarget,
                                                @NativeType("GLuint") int renderbuffer) {
        long __functionAddress = glFramebufferRenderbuffer;
        callV(target, attachment, renderbuffertarget, renderbuffer, __functionAddress);
    }

    public final void glBlitFramebuffer(@NativeType("GLint") int srcX0, @NativeType("GLint") int srcY0,
                                        @NativeType("GLint") int srcX1, @NativeType("GLint") int srcY1,
                                        @NativeType("GLint") int dstX0, @NativeType("GLint") int dstY0,
                                        @NativeType("GLint") int dstX1, @NativeType("GLint") int dstY1,
                                        @NativeType("GLbitfield") int mask, @NativeType("GLenum") int filter) {
        long __functionAddress = glBlitFramebuffer;
        callV(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter, __functionAddress);
    }

    public final void glClearBufferiv(@NativeType("GLenum") int buffer, @NativeType("GLint") int drawbuffer,
                                      @NativeType("GLint const *") long value) {
        long __functionAddress = glClearBufferiv;
        callPV(buffer, drawbuffer, value, __functionAddress);
    }

    public final void glClearBufferfv(@NativeType("GLenum") int buffer, @NativeType("GLint") int drawbuffer,
                                      @NativeType("GLfloat const *") long value) {
        long __functionAddress = glClearBufferfv;
        callPV(buffer, drawbuffer, value, __functionAddress);
    }

    public final void glClearBufferfi(@NativeType("GLenum") int buffer, @NativeType("GLint") int drawbuffer,
                                      @NativeType("GLfloat") float depth, @NativeType("GLint") int stencil) {
        long __functionAddress = glClearBufferfi;
        callV(buffer, drawbuffer, depth, stencil, __functionAddress);
    }

    public final void glBindBufferBase(@NativeType("GLenum") int target, @NativeType("GLuint") int index,
                                       @NativeType("GLuint") int buffer) {
        long __functionAddress = glBindBufferBase;
        callV(target, index, buffer, __functionAddress);
    }

    public final void glBindBufferRange(@NativeType("GLenum") int target, @NativeType("GLuint") int index,
                                        @NativeType("GLuint") int buffer, @NativeType("GLintptr") long offset,
                                        @NativeType("GLsizeiptr") long size) {
        long __functionAddress = glBindBufferRange;
        callPPV(target, index, buffer, offset, size, __functionAddress);
    }

    public final void glGenRenderbuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") long renderbuffers) {
        long __functionAddress = glGenRenderbuffers;
        callPV(n, renderbuffers, __functionAddress);
    }

    public final void glGenRenderbuffers(@NativeType("GLsizei") int n, @NativeType("GLuint *") int[] renderbuffers) {
        long __functionAddress = glGenRenderbuffers;
        callPV(n, renderbuffers, __functionAddress);
    }

    public final void glDeleteRenderbuffers(@NativeType("GLsizei") int n,
                                            @NativeType("GLuint const *") long renderbuffer) {
        long __functionAddress = glDeleteRenderbuffers;
        callPV(n, renderbuffer, __functionAddress);
    }

    public final void glDeleteRenderbuffers(@NativeType("GLsizei") int n,
                                            @NativeType("GLuint const *") int[] renderbuffers) {
        long __functionAddress = glDeleteRenderbuffers;
        callPV(n, renderbuffers, __functionAddress);
    }

    public final void glBindRenderbuffer(@NativeType("GLenum") int target, @NativeType("GLuint") int renderbuffer) {
        long __functionAddress = glBindRenderbuffer;
        callV(target, renderbuffer, __functionAddress);
    }

    public final void glRenderbufferStorage(@NativeType("GLenum") int target, @NativeType("GLenum") int internalformat,
                                            @NativeType("GLsizei") int width, @NativeType("GLsizei") int height) {
        long __functionAddress = glRenderbufferStorage;
        callV(target, internalformat, width, height, __functionAddress);
    }

    public final void glRenderbufferStorageMultisample(@NativeType("GLenum") int target,
                                                       @NativeType("GLsizei") int samples,
                                                       @NativeType("GLenum") int internalformat,
                                                       @NativeType("GLsizei") int width,
                                                       @NativeType("GLsizei") int height) {
        long __functionAddress = glRenderbufferStorageMultisample;
        callV(target, samples, internalformat, width, height, __functionAddress);
    }

    @NativeType("void *")
    public final long glMapBufferRange(@NativeType("GLenum") int target, @NativeType("GLintptr") long offset,
                                       @NativeType("GLsizeiptr") long length, @NativeType("GLbitfield") int access) {
        long __functionAddress = glMapBufferRange;
        return callPPP(target, offset, length, access, __functionAddress);
    }

    public final void glDrawArraysInstanced(@NativeType("GLenum") int mode, @NativeType("GLint") int first,
                                            @NativeType("GLsizei") int count,
                                            @NativeType("GLsizei") int instancecount) {
        long __functionAddress = glDrawArraysInstanced;
        callV(mode, first, count, instancecount, __functionAddress);
    }

    public final void glDrawElementsInstanced(@NativeType("GLenum") int mode, @NativeType("GLsizei") int count,
                                              @NativeType("GLenum") int type, @NativeType("void const *") long indices,
                                              @NativeType("GLsizei") int instancecount) {
        long __functionAddress = glDrawElementsInstanced;
        callPV(mode, count, type, indices, instancecount, __functionAddress);
    }

    public final void glCopyBufferSubData(@NativeType("GLenum") int readTarget, @NativeType("GLenum") int writeTarget,
                                          @NativeType("GLintptr") long readOffset,
                                          @NativeType("GLintptr") long writeOffset,
                                          @NativeType("GLsizeiptr") long size) {
        long __functionAddress = glCopyBufferSubData;
        callPPPV(readTarget, writeTarget, readOffset, writeOffset, size, __functionAddress);
    }

    @NativeType("GLuint")
    public final int glGetUniformBlockIndex(@NativeType("GLuint") int program,
                                            @NativeType("GLchar const *") long uniformBlockName) {
        long __functionAddress = glGetUniformBlockIndex;
        return callPI(program, uniformBlockName, __functionAddress);
    }

    public final void glUniformBlockBinding(@NativeType("GLuint") int program,
                                            @NativeType("GLuint") int uniformBlockIndex,
                                            @NativeType("GLuint") int uniformBlockBinding) {
        long __functionAddress = glUniformBlockBinding;
        callV(program, uniformBlockIndex, uniformBlockBinding, __functionAddress);
    }

    @NativeType("GLsync")
    public final long glFenceSync(@NativeType("GLenum") int condition, @NativeType("GLbitfield") int flags) {
        long __functionAddress = glFenceSync;
        return callP(condition, flags, __functionAddress);
    }

    public final void glDeleteSync(@NativeType("GLsync") long sync) {
        long __functionAddress = glDeleteSync;
        callPV(sync, __functionAddress);
    }

    @NativeType("GLenum")
    public final int glClientWaitSync(@NativeType("GLsync") long sync, @NativeType("GLbitfield") int flags,
                                      @NativeType("GLuint64") long timeout) {
        long __functionAddress = glClientWaitSync;
        return callPJI(sync, flags, timeout, __functionAddress);
    }

    public final void glGenSamplers(@NativeType("GLsizei") int count, @NativeType("GLuint *") long samplers) {
        long __functionAddress = glGenSamplers;
        callPV(count, samplers, __functionAddress);
    }

    public final void glGenSamplers(@NativeType("GLsizei") int count, @NativeType("GLuint *") int[] samplers) {
        long __functionAddress = glGenSamplers;
        callPV(count, samplers, __functionAddress);
    }

    public final void glDeleteSamplers(@NativeType("GLsizei") int count, @NativeType("GLuint *") long samplers) {
        long __functionAddress = glDeleteSamplers;
        callPV(count, samplers, __functionAddress);
    }

    public final void glDeleteSamplers(@NativeType("GLsizei") int count, @NativeType("GLuint *") int[] samplers) {
        long __functionAddress = glDeleteSamplers;
        callPV(count, samplers, __functionAddress);
    }

    public final void glBindSampler(@NativeType("GLuint") int unit, @NativeType("GLuint") int sampler) {
        long __functionAddress = glBindSampler;
        callV(unit, sampler, __functionAddress);
    }

    public final void glSamplerParameteri(@NativeType("GLuint") int sampler, @NativeType("GLenum") int pname,
                                          @NativeType("GLint") int param) {
        long __functionAddress = glSamplerParameteri;
        callV(sampler, pname, param, __functionAddress);
    }

    public final void glSamplerParameterf(@NativeType("GLuint") int sampler, @NativeType("GLenum") int pname,
                                          @NativeType("GLfloat") float param) {
        long __functionAddress = glSamplerParameterf;
        callV(sampler, pname, param, __functionAddress);
    }

    public final void glVertexAttribDivisor(@NativeType("GLuint") int index, @NativeType("GLuint") int divisor) {
        long __functionAddress = glVertexAttribDivisor;
        callV(index, divisor, __functionAddress);
    }
}

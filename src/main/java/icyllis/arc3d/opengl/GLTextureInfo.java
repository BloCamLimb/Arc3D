/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
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

/**
 * Types for interacting with GL resources created externally to pipeline. BackendObjects for GL
 * textures are really const GLTexture*. The {@link #format} here should be a sized, internal format
 * for the texture. We use the sized format since the base internal formats are deprecated.
 * <p>
 * Note the target is always {@link GLCore#GL_TEXTURE_2D}. When importing external memory,
 * {@link #memoryHandle} is POSIX file descriptor or Win32 NT handle (though <code>HANDLE</code>
 * is defined as <code>void*</code>, we can safely truncate it because Win32 handles are
 * 32-bit significant). {@link #memoryObject} is OpenGL memory object. If it is an NT handle,
 * it must be released manually by the memory exporter (e.g. Vulkan).
 * <p>
 * We only provide single-sample textures, multisample can be only renderbuffers.
 */
public final class GLTextureInfo {

    /**
     * <code>GLenum</code> - image namespace
     */
    public int target = GLCore.GL_TEXTURE_2D;
    /**
     * <code>GLuint</code> - image name
     */
    public int handle;
    /**
     * <code>GLenum</code> - sized internal format
     */
    public int format;
    /**
     * <code>GLsizei</code> - number of mip levels
     */
    public int levels = 0;
    /**
     * <code>GLsizei</code> - number of samples
     */
    public int samples = 0;
    /**
     * <code>GLuint</code> - memory
     */
    public int memoryObject;
    /**
     * <pre>{@code
     * union {
     *     int fd; // file descriptor
     *     HANDLE handle; // win32 handle
     * };
     * }</pre>
     */
    public int memoryHandle = -1;

    public void set(GLTextureInfo info) {
        target = info.target;
        handle = info.handle;
        format = info.format;
        levels = info.levels;
        samples = info.samples;
        memoryObject = info.memoryObject;
        memoryHandle = info.memoryHandle;
    }

    @Override
    public int hashCode() {
        int h = target;
        h = 31 * h + handle;
        h = 31 * h + format;
        h = 31 * h + levels;
        h = 31 * h + samples;
        h = 31 * h + memoryObject;
        h = 31 * h + memoryHandle;
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof GLTextureInfo info)
            return target == info.target &&
                    handle == info.handle &&
                    format == info.format &&
                    levels == info.levels &&
                    samples == info.samples &&
                    memoryObject == info.memoryObject &&
                    memoryHandle == info.memoryHandle;
        return false;
    }

    @Override
    public String toString() {
        return '{' +
                "target=" + target +
                ", handle=" + handle +
                ", format=" + GLCore.glFormatName(format) +
                ", levels=" + levels +
                ", samples=" + samples +
                ", memoryObject=" + memoryObject +
                ", memoryHandle=" + memoryHandle +
                '}';
    }
}

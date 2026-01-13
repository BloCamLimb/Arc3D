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

import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImageDesc;
import org.lwjgl.opengl.GL30C;

import javax.annotation.concurrent.Immutable;

/**
 * Descriptor to create OpenGL images (textures and renderbuffers). The {@link #mGLFormat} here
 * should be a sized, internal format for the texture. We use the sized format since the
 * base internal formats are deprecated.
 * <p>
 * Note the target can be {@link GL30C#GL_RENDERBUFFER}.
 */
@Immutable
public final class GLImageDesc extends ImageDesc {

    /**
     * <code>GLenum</code> - image namespace
     */
    public final int mTarget;
    /**
     * <code>GLenum</code> - sized internal format
     */
    public final int mGLFormat;

    public GLImageDesc(int target, int glFormat,
                       int viewType, int viewFormat,
                       int width, int height,
                       int depth, int arraySize,
                       int mipLevelCount, int sampleCount,
                       int flags) {
        super(viewType, viewFormat, width, height, depth, arraySize, mipLevelCount, sampleCount, flags);
        mTarget = target;
        mGLFormat = glFormat;
    }

    @Override
    public int getBackend() {
        return Engine.BackendApi.kOpenGL;
    }

    @Override
    public int getGLFormat() {
        return mGLFormat;
    }

    @Override
    public int getChannelFlags() {
        return GLUtil.glFormatChannels(mGLFormat);
    }

    @Override
    public boolean isSRGB() {
        return GLUtil.glFormatIsSRGB(mGLFormat);
    }

    @Override
    public int getCompressionType() {
        return GLUtil.glFormatCompressionType(mGLFormat);
    }

    @Override
    public int getBytesPerBlock() {
        return GLUtil.glFormatBytesPerBlock(mGLFormat);
    }

    @Override
    public int getDepthBits() {
        return GLUtil.glFormatDepthBits(mGLFormat);
    }

    @Override
    public int getStencilBits() {
        return GLUtil.glFormatStencilBits(mGLFormat);
    }

    @Override
    public int hashCode() {
        int result = mTarget;
        result = 31 * result + mGLFormat;
        result = 31 * result + mWidth;
        result = 31 * result + mHeight;
        result = 31 * result + mDepth;
        result = 31 * result + mArraySize;
        result = 31 * result + mMipLevelCount;
        result = 31 * result + mSampleCount;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof GLImageDesc desc) {
            return mTarget == desc.mTarget &&
                    mGLFormat == desc.mGLFormat &&
                    mWidth == desc.mWidth &&
                    mHeight == desc.mHeight &&
                    mDepth == desc.mDepth &&
                    mArraySize == desc.mArraySize &&
                    mMipLevelCount == desc.mMipLevelCount &&
                    mSampleCount == desc.mSampleCount;
        }
        return false;
    }

    @Override
    public String toString() {
        return '{' +
                "target=" + mTarget +
                ", format=" + GLUtil.glFormatName(mGLFormat) +
                ", width=" + mWidth +
                ", height=" + mHeight +
                ", depth=" + mDepth +
                ", arraySize=" + mArraySize +
                ", mipLevelCount=" + mMipLevelCount +
                ", sampleCount=" + mSampleCount +
                '}';
    }
}

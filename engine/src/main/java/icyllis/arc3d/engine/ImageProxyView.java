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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.RefCounted;
import icyllis.arc3d.core.SharedPtr;

import java.util.Objects;

import static icyllis.arc3d.engine.Engine.SurfaceOrigin;

/**
 * Image views contain additional metadata for pipeline operations on images.
 * This class is a tuple of {@link ImageProxy}, SurfaceOrigin and Swizzle.
 */
public final class ImageProxyView implements RefCounted {

    @SharedPtr
    private final ImageProxy mProxy;
    private final int mOrigin;
    private final short mSwizzle;

    public ImageProxyView(@SharedPtr ImageProxy proxy) {
        mProxy = proxy; // std::move()
        mOrigin = SurfaceOrigin.kUpperLeft;
        mSwizzle = Swizzle.RGBA;
    }

    public ImageProxyView(@SharedPtr ImageProxy proxy, int origin, short swizzle) {
        mProxy = proxy; // std::move()
        mOrigin = origin;
        mSwizzle = swizzle;
    }

    public int getWidth() {
        return mProxy.getWidth();
    }

    public int getHeight() {
        return mProxy.getHeight();
    }

    public boolean isMipmapped() {
        return mProxy.isMipmapped();
    }

    /**
     * Returns smart pointer value (raw ptr).
     */
    @RawPtr
    public ImageProxy getProxy() {
        return mProxy;
    }

    /**
     * Returns a smart pointer (as if on the stack).
     */
    @SharedPtr
    public ImageProxy refProxy() {
        mProxy.ref();
        return mProxy;
    }

    /**
     * @see SurfaceOrigin
     */
    public int getOrigin() {
        return mOrigin;
    }

    /**
     * @see Swizzle
     */
    public short getSwizzle() {
        return mSwizzle;
    }

    /**
     * Same as {@link #refProxy()}.
     */
    // We can leak the ref countability to the underlying object in this scenario
    @Override
    public void ref() {
        mProxy.ref();
    }

    @Override
    public void unref() {
        mProxy.unref();
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(mProxy);
        result = 31 * result + mOrigin;
        result = 31 * result + mSwizzle;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImageProxyView that = (ImageProxyView) o;
        return mOrigin == that.mOrigin &&
                mSwizzle == that.mSwizzle &&
                Objects.equals(mProxy, that.mProxy);
    }

    @Override
    public String toString() {
        return "ImageProxyView{" +
                "mProxy=" + mProxy +
                ", mOrigin=" + mOrigin +
                ", mSwizzle=" + Swizzle.toString(mSwizzle) +
                '}';
    }
}

/*
 * The Arctic Graphics Engine.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arctic is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arctic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arctic. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcticgi.engine;

import icyllis.arcticgi.core.SharedPtr;

/**
 * Views a {@link SurfaceProxy} on client side.
 */
public final class SurfaceProxyView implements AutoCloseable {

    @SharedPtr
    TextureProxy mProxy;
    int mOrigin;
    short mSwizzle;

    public SurfaceProxyView(@SharedPtr TextureProxy proxy) {
        mProxy = proxy; // std::move()
        mOrigin = EngineTypes.SurfaceOrigin_TopLeft;
        mSwizzle = Swizzle.RGBA;
    }

    public SurfaceProxyView(@SharedPtr TextureProxy proxy, int origin, short swizzle) {
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
    public TextureProxy getProxy() {
        return mProxy;
    }

    /**
     * Returns a smart pointer (as if on the stack).
     */
    @SharedPtr
    public TextureProxy refProxy() {
        mProxy.ref();
        return mProxy;
    }

    /**
     * This does not reset the origin or swizzle, so the view can still be used to access those
     * properties associated with the detached proxy.
     */
    @SharedPtr
    public TextureProxy detachProxy() {
        // just like std::move(), R-value reference
        TextureProxy proxy = mProxy;
        mProxy = null;
        return proxy;
    }

    /**
     * @see EngineTypes#SurfaceOrigin_TopLeft
     * @see EngineTypes#SurfaceOrigin_BottomLeft
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
     * Merge swizzle.
     */
    public void merge(short swizzle) {
        mSwizzle = Swizzle.merge(mSwizzle, swizzle);
    }

    /**
     * Recycle this view.
     */
    public void reset() {
        if (mProxy != null) {
            mProxy.unref();
        }
        mProxy = null;
        mOrigin = EngineTypes.SurfaceOrigin_TopLeft;
        mSwizzle = Swizzle.RGBA;
    }

    /**
     * Destructs this view.
     */
    @Override
    public void close() {
        if (mProxy != null) {
            mProxy.unref();
        }
        mProxy = null;
    }
}

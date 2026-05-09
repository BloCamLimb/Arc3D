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

import icyllis.arc3d.core.RefCounted;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * The ManagedResource variant that supports recycling.
 */
public abstract class RecycledResource implements RefCounted {

    private static final VarHandle USAGE_CNT;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            USAGE_CNT = lookup.findVarHandle(RecycledResource.class, "mUsageCnt", int.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int mUsageCnt = 1;

    private final Device mDevice;

    public RecycledResource(Device device) {
        mDevice = device;
    }

    public final boolean unique() {
        // std::memory_order_acquire
        return (int) USAGE_CNT.getAcquire(this) == 1;
    }

    @Override
    public final void ref() {
        // stronger than std::memory_order_relaxed
        var refCnt = (int) USAGE_CNT.getAndAddAcquire(this, 1);
        assert refCnt > 0 : "Reference count has reached zero " + this;
    }

    /**
     * When recycle is called and there is only one ref left on the resource, we will signal that
     * the resource can be recycled for reuse. If the subclass (or whoever is managing this resource)
     * decides not to recycle the objects, it is their responsibility to release the object.
     */
    @Override
    public final void unref() {
        // stronger than std::memory_order_acq_rel
        var refCnt = (int) USAGE_CNT.getAndAdd(this, -1);
        assert refCnt > 0 : "Reference count has reached zero " + this;
        if (refCnt == 1) {
            onRecycle();
        }
    }

    /**
     * Privileged method that allows going from ref count = 0 to ref count = 1 for reuse.
     */
    public final void addInitialUsageRef() {
        // stronger than std::memory_order_relaxed
        USAGE_CNT.getAndAddRelease(this, 1);
    }

    protected Device getDevice() {
        return mDevice;
    }

    /**
     * Override this method to invoke recycling of the underlying resource.
     */
    protected abstract void onRecycle();
}

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

package icyllis.arc3d.engine;

/**
 * Represents GPU sampler objects.
 * <p>
 * Samplers are managed by {@link DeviceBoundCache}, no need to track resources.
 * Use {@link ResourceProvider} to obtain instances.
 */
public abstract class Sampler {

    private final Device mDevice;
    protected final SamplerDesc mDesc;

    protected Sampler(Device device, SamplerDesc desc) {
        mDevice = device;
        mDesc = desc;
    }

    protected abstract void destroy();

    protected Device getDevice() {
        return mDevice;
    }

    /**
     * Returns the description used to create this sampler object.
     */
    public final SamplerDesc getDesc() {
        return mDesc;
    }
}

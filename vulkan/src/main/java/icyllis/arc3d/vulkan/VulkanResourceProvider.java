/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.vulkan;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Buffer;
import icyllis.arc3d.engine.Context;
import icyllis.arc3d.engine.Image;
import icyllis.arc3d.engine.ImageDesc;
import icyllis.arc3d.engine.ResourceProvider;
import icyllis.arc3d.engine.Sampler;
import icyllis.arc3d.engine.SamplerDesc;
import org.jspecify.annotations.Nullable;

public class VulkanResourceProvider extends ResourceProvider {

    private final VulkanDevice mDevice;

    private final VulkanDescriptorPool.ResourceKey mDescriptorPoolKey = new VulkanDescriptorPool.ResourceKey();

    protected VulkanResourceProvider(VulkanDevice device, Context context,
                                     long maxResourceBudget) {
        super(device, context, maxResourceBudget);
        mDevice = device;
    }

    @Nullable
    @Override
    protected Image onCreateNewImage(ImageDesc desc) {
        if (!(desc instanceof VulkanImageDesc vulkanImageDesc)) {
            return null;
        }
        return VulkanImage.make(mDevice, vulkanImageDesc);
    }

    @Nullable
    @Override
    protected Sampler createSampler(SamplerDesc desc) {
        return null;
    }

    @Nullable
    @Override
    protected Buffer onCreateNewBuffer(long size, int usage) {
        return null;
    }

    @Nullable
    @SharedPtr
    public VulkanDescriptorPool findOrCreateDescriptorPool(@RawPtr VulkanDescriptorSetLayout setLayout,
                                                           int maxSets) {
        if (mDevice.isDeviceLost()) {
            return null;
        }

        @SharedPtr
        VulkanDescriptorPool pool = (VulkanDescriptorPool) mResourceCache.findAndRefResource(
                mDescriptorPoolKey.set(setLayout, maxSets),
                /*budgeted*/true, /*shareable*/false
        );
        if (pool != null) {
            return pool;
        }
        pool = VulkanDescriptorPool.create(mDevice, RefCnt.create(setLayout), maxSets);
        if (pool == null) {
            return null;
        }
        mResourceCache.insertResource(pool, mDescriptorPoolKey.copy(), /*budgeted*/true, /*shareable*/false);
        return pool;
    }
}

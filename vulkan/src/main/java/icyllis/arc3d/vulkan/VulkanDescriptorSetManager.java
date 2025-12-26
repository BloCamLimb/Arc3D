/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

/**
 * Manages descriptor sets for a single {@link DescriptorSetLayout}, actually
 * a descriptor pool manager. Our descriptor set grouping strategy is based on
 * update frequency, so each set has its own manager.
 * <p>
 * Persistable descriptor sets and dynamic descriptor sets are both allowed.
 * Whether persisting is enabled is determined at creation time.
 */
public class VulkanDescriptorSetManager extends RefCnt {

    @RawPtr
    final VulkanResourceProvider mResourceProvider;

    public VulkanDescriptorSetManager(@RawPtr VulkanResourceProvider resourceProvider,
                                      @SharedPtr VulkanDescriptorSetLayout setLayout) {
        mResourceProvider = resourceProvider;
    }

    @Override
    protected void deallocate() {

    }
}

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
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ManagedResource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDescriptorPool extends ManagedResource {

    // since a pool allocates with single DS layout, 256 may be reasonable.
    // resource buffers use dynamic offsets
    public static final int DEFAULT_MAX_SETS_PER_POOL = 256;

    private final long mDescPool;
    @SharedPtr
    private final VulkanDescriptorSetLayout mLayout;

    private VulkanDescriptorPool(VulkanDevice device,
                                 @SharedPtr VulkanDescriptorSetLayout layout,
                                 long descPool) {
        super(device);
        mDescPool = descPool;
        mLayout = layout;
        assert descPool != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    public static VulkanDescriptorPool make(@NonNull VulkanDevice device,
                                            @SharedPtr VulkanDescriptorSetLayout layout,
                                            int maxSets) {

        if (layout == null || maxSets <= 0) {
            return null;
        }
        if (layout.getDesc().getBindingCount() == 0) {
            layout.unref();
            return null;
        }
        try (var stack = MemoryStack.stackPush()) {
            var pPoolSizes = layout.getDesc().toVkPoolSizes(
                    stack, maxSets
            );

            var pCreateInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .maxSets(maxSets)
                    .pPoolSizes(pPoolSizes);
            var pPool = stack.mallocLong(1);
            var result = vkCreateDescriptorPool(device.vkDevice(),
                    pCreateInfo, null, pPool);

            device.checkResult(result);
            if (result != VK_SUCCESS) {
                layout.unref();
                device.getLogger().error("Failed to create VulkanDescriptorPool: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanDescriptorPool(device, layout, // move
                    pPool.get(0));
        }
    }

    public @RawPtr VulkanDescriptorSetLayout getLayout() {
        return mLayout;
    }

    public long vkDescriptorPool() {
        return mDescPool;
    }

    @Override
    protected void deallocate() {
        mLayout.unref();
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyDescriptorPool(device.vkDevice(),
                mDescPool, null);
    }
}

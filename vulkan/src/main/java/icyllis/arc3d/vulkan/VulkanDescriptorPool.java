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
import icyllis.arc3d.engine.IResourceKey;
import icyllis.arc3d.engine.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;

import java.util.ArrayDeque;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDescriptorPool extends Resource {

    // since a pool allocates with single DS layout, 256 may be reasonable.
    // resource buffers use dynamic offsets
    public static final int DEFAULT_MAX_SETS_PER_POOL = 256;

    private final long mDescPool;
    @SharedPtr
    private final VulkanDescriptorSetLayout mLayout;

    /*
     * In ResourceCache, the values stored in the multimap are organized as an LRU list (stack).
     * However, VulkanDescriptorPool is different.
     *
     * We want the dynamic DescriptorSets used within a single submission to be contiguous, so we
     * use a ring buffer. All DescriptorSets are allocated upfront when the pool is created,
     * in order to avoid ending up with a pool where DescriptorSets are randomly distributed.
     */
    private final ArrayDeque<VulkanDescriptorSet> mFreePool;

    private VulkanDescriptorPool(VulkanDevice device,
                                 @SharedPtr VulkanDescriptorSetLayout layout,
                                 int maxSets,
                                 long descPool) {
        super(device, /*wrapped*/ false, /*memorySize*/ 0);
        mDescPool = descPool;
        mLayout = layout;
        assert descPool != VK_NULL_HANDLE;
        mFreePool = new ArrayDeque<>(maxSets);
    }

    @Nullable
    @SharedPtr
    public static VulkanDescriptorPool create(@NonNull VulkanDevice device,
                                              @SharedPtr VulkanDescriptorSetLayout layout,
                                              int maxSets) {

        if (layout == null) {
            return null;
        }
        if (maxSets <= 0 || layout.getLayoutDesc().getBindingCount() == 0) {
            layout.unref();
            return null;
        }
        try (var stack = MemoryStack.stackPush()) {
            var pPoolSizes = layout.getLayoutDesc().toVkPoolSizes(
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

            @SharedPtr
            var resultPool = new VulkanDescriptorPool(device, layout, // move
                    maxSets, pPool.get(0));

            // allocate and exhaust the pool, to full up our ring buffer
            @SharedPtr
            var descriptorSets = VulkanDescriptorSet.allocate(
                    device, resultPool, // <- raw ptr
                    maxSets
            );

            // check if the first DS is successfully created, otherwise the whole array is empty
            if (descriptorSets[0] == null) {
                resultPool.unref();
                device.getLogger().error("Failed to allocate any descriptor sets from pool");
                return null;
            }

            for (VulkanDescriptorSet descriptorSet : descriptorSets) {
                if (descriptorSet == null) {
                    // this is allowed if continuous allocation failed, and the rest are all null
                    break;
                }

                // not needed for now, return everything to free pool
                descriptorSet.unref();
            }

            return resultPool; // move ownership to caller
        }
    }

    public @RawPtr VulkanDescriptorSetLayout getLayout() {
        return mLayout;
    }

    public long vkDescriptorPool() {
        return mDescPool;
    }

    @Nullable
    @SharedPtr
    public VulkanDescriptorSet obtainDescriptorSet() {
        var next = mFreePool.poll();
        if (next != null) {
            next.onReuse();
            return next;
        }
        return null;
    }

    void recycleDescriptorSet(@NonNull VulkanDescriptorSet set) {
        mFreePool.offer(set);
    }

    @Override
    protected void onRelease() {
        mLayout.unref();
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyDescriptorPool(device.vkDevice(),
                mDescPool, null);
    }

    public static class ResourceKey implements IResourceKey {

        public VulkanDescriptorSetLayout.LayoutDesc layoutDesc;
        public int maxSets;

        public ResourceKey() {
        }

        public ResourceKey(ResourceKey other) {
            this.layoutDesc = other.layoutDesc;
            this.maxSets = other.maxSets;
        }

        public ResourceKey set(@RawPtr VulkanDescriptorSetLayout setLayout, int maxSets) {
            this.layoutDesc = setLayout.getLayoutDesc();
            this.maxSets  = maxSets;
            return this;
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ResourceKey key)
                return maxSets == key.maxSets &&
                        layoutDesc.equals(key.layoutDesc);
            return false;
        }

        @Override
        public ResourceKey copy() {
            return new ResourceKey(this);
        }

        @Override
        public int hashCode() {
            int result = layoutDesc.hashCode();
            result = 31 * result + maxSets;
            return result;
        }
    }
}

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
import icyllis.arc3d.engine.DescriptorSetLayout;
import icyllis.arc3d.engine.Engine.DescriptorType;
import icyllis.arc3d.engine.IResourceKey;
import icyllis.arc3d.engine.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.util.ArrayDeque;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDescriptorPool extends Resource {

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
                                 long memorySize,
                                 @SharedPtr VulkanDescriptorSetLayout layout,
                                 int maxSets,
                                 long descPool) {
        super(device, /*wrapped*/ false, memorySize);
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
        if (maxSets <= 0 || layout.getLayoutInfo().getBindingCount() == 0) {
            layout.unref();
            return null;
        }
        try (var stack = MemoryStack.stackPush()) {

            // Vulkan 1.0 spec says: If multiple pool size structures contain the same descriptor type,
            // the pool will be created with enough storage for the total number of descriptors of each type.
            // However, here we still want to calculate the cumulative number of DS for each type.
            int[] perTypeSizes = new int[DescriptorType.kCount];
            for (int i = 0; i < layout.getBindingCount(); i++) {
                var entry = layout.getLayoutInfo().getDescriptorInfo(i);

                int type = entry.mType;
                int count = 1;

                // Since a pool only allocates DS with the same layout, we know the exact size for each type
                perTypeSizes[type] += count * maxSets;
            }

            // overestimate a pool's memory size
            long memorySize = 4096;

            var pPoolSizes = VkDescriptorPoolSize.calloc(perTypeSizes.length, stack);
            for (int type = 0; type < perTypeSizes.length; type++) {
                int size = perTypeSizes[type];
                if (size != 0) {
                    pPoolSizes.type(VKUtil.toVkDescriptorType(type))
                            .descriptorCount(size);

                    pPoolSizes.position(pPoolSizes.position() + 1);

                    if (type == DescriptorType.kCombinedImageSampler) {
                        memorySize += size * 64L;
                    } else {
                        memorySize += size * 32L;
                    }
                }
            }

            pPoolSizes.flip();

            var pCreateInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
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
            var resultPool = new VulkanDescriptorPool(device, memorySize, layout, // move
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

        public DescriptorSetLayout layoutInfo;
        public int maxSets;

        public ResourceKey() {
        }

        public ResourceKey(ResourceKey other) {
            this.layoutInfo = other.layoutInfo;
            this.maxSets = other.maxSets;
        }

        public ResourceKey set(@RawPtr VulkanDescriptorSetLayout setLayout, int maxSets) {
            this.layoutInfo = setLayout.getLayoutInfo();
            this.maxSets  = maxSets;
            return this;
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ResourceKey key)
                return maxSets == key.maxSets &&
                        layoutInfo.equals(key.layoutInfo);
            return false;
        }

        @Override
        public ResourceKey copy() {
            return new ResourceKey(this);
        }

        @Override
        public int hashCode() {
            int result = layoutInfo.hashCode();
            result = 31 * result + maxSets;
            return result;
        }
    }
}

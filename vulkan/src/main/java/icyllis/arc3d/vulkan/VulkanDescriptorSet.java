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

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.RecycledResource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDescriptorSet extends RecycledResource {

    private final long mDescriptorSet;
    @SharedPtr
    private final VulkanDescriptorPool mPool;

    private VulkanDescriptorSet(VulkanDevice device,
                                @SharedPtr VulkanDescriptorPool pool,
                                long descriptorSet) {
        super(device);
        mDescriptorSet = descriptorSet;
        mPool = pool;
        assert descriptorSet != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    static VulkanDescriptorSet @NonNull [] allocate(
            @NonNull VulkanDevice device,
            @NonNull @RawPtr VulkanDescriptorPool pool,
            int count) {
        assert count > 0;
        var resultSets = new VulkanDescriptorSet[count];
        // we're trying to allocate 1/64 requested count for each call, and
        // clamp to a reasonable range to reduce calls while allowing soft fails
        int maxAllocateCountEachRun = MathUtil.clamp(count >>> 6, 1, 8);
        try (var stack = MemoryStack.stackPush()) {
            var pSetLayouts = stack.mallocLong(maxAllocateCountEachRun);
            var pDescriptorSets = stack.mallocLong(maxAllocateCountEachRun);
            long setLayout = pool.getLayout().vkSetLayout();
            for (int i = 0; i < maxAllocateCountEachRun; i++) {
                pSetLayouts.put(i, setLayout);
            }

            var pAllocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(pSetLayouts);

            for (int ix = 0; ix < count; ) {
                int allocateCountThisRun = Math.min(maxAllocateCountEachRun, count - ix);

                VkDescriptorSetAllocateInfo.ndescriptorSetCount(pAllocateInfo.address(),
                        allocateCountThisRun);

                var result = vkAllocateDescriptorSets(device.vkDevice(),
                        pAllocateInfo,
                        pDescriptorSets);
                if (result != VK_SUCCESS) {
                    device.getLogger().warn(
                            "Failed to allocate {} descriptor sets from pool; " +
                                    "allocated: {}, total requested: {}; " +
                                    "no more sets will be allocated from this pool.",
                            allocateCountThisRun, ix, count
                    );
                    break;
                }
                for (int i = 0; i < allocateCountThisRun; i++) {
                    assert resultSets[i + ix] == null;
                    // each DS holds a ref to pool
                    resultSets[i + ix] = new VulkanDescriptorSet(
                            device, RefCnt.create(pool), pDescriptorSets.get(i)
                    );
                }

                ix += allocateCountThisRun;
            }

            return resultSets;
        }
    }

    public long vkDescriptorSet() {
        return mDescriptorSet;
    }

    void onReuse() {
        // on reuse, usage count from 0 to 1, and
        // ref the pool again to make not purgeable
        mPool.ref();
        addInitialUsageRef();
    }

    @Override
    protected void onRecycle() {
        // once all DS from the pool are recycled, the pool will be returned to the cache
        // if there's no active DS manager
        mPool.recycleDescriptorSet(this);
        mPool.unref();
    }
}

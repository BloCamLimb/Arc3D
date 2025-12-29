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

import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ManagedResource;
import icyllis.arc3d.engine.PipelineDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The layout is also used to lookup in
 *      * resource cache for descriptor pool and descriptor sets. Our pool is based on layout,
 *      * each pool only allocates descriptor sets for certain layout. This can be also unpacked
 *      * to create actual VkDescriptorSetLayout object when needed.
 */
public final class VulkanDescriptorSetLayout extends ManagedResource {

    private final LayoutDesc mLayoutDesc;
    private final long mSetLayout;

    private VulkanDescriptorSetLayout(VulkanDevice device,
                                      LayoutDesc layoutDesc,
                                      long setLayout) {
        super(device);
        mLayoutDesc = layoutDesc;
        mSetLayout = setLayout;

        assert setLayout != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    public static VulkanDescriptorSetLayout make(@NonNull VulkanDevice device,
                                                 @NonNull LayoutDesc layoutDesc) {
        try (var stack = MemoryStack.stackPush()) {
            var pBindings = layoutDesc.toVkBindings(stack);
            var pCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .pBindings(pBindings);
            var pSetLayout = stack.mallocLong(1);

            var result = vkCreateDescriptorSetLayout(device.vkDevice(),
                    pCreateInfo, null, pSetLayout);
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanDescriptorSetLayout: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanDescriptorSetLayout(device, layoutDesc,
                    pSetLayout.get(0));
        }
    }

    @NonNull
    public LayoutDesc getLayoutDesc() {
        return mLayoutDesc;
    }

    public int getType(int binding) {
        return mLayoutDesc.getDescriptorInfo(binding).mType;
    }

    public long vkSetLayout() {
        return mSetLayout;
    }

    @Override
    protected void deallocate() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyDescriptorSetLayout(device.vkDevice(),
                mSetLayout, null);

    }

    public static final class LayoutDesc {

        private final PipelineDesc.@NonNull DescriptorInfo @NonNull [] mDescriptorInfos;

        public LayoutDesc(PipelineDesc.@NonNull DescriptorInfo @NonNull [] descriptorInfos) {
            // inner struct is already immutable, just shallow copy the array
            mDescriptorInfos = descriptorInfos.clone();
        }

        public PipelineDesc.@NonNull DescriptorInfo getDescriptorInfo(int binding) {
            return mDescriptorInfos[binding];
        }

        public int getBindingCount() {
            return mDescriptorInfos.length;
        }

        public VkDescriptorSetLayoutBinding.@NonNull Buffer toVkBindings(@NonNull MemoryStack stack) {
            var bindings = VkDescriptorSetLayoutBinding.calloc(getBindingCount(), stack);

            for (int binding = 0; binding < mDescriptorInfos.length; binding++) {
                var entry = mDescriptorInfos[binding];

                int type = VKUtil.toVkDescriptorType(entry.mType);
                int count = 1;
                int stageFlags = VKUtil.toVkPipelineStageFlags(entry.mVisibility);
                boolean useImmutableSampler = entry.mImmutableSampler != null;

                bindings.binding(binding)
                        .descriptorType(type)
                        .descriptorCount(count)
                        .stageFlags(stageFlags);

                if (useImmutableSampler) {

                    // TODO set this
                }

                bindings.position(bindings.position() + 1);
            }

            assert !bindings.hasRemaining();
            return bindings.rewind();
        }

        public VkDescriptorPoolSize. @NonNull Buffer toVkPoolSizes(@NonNull MemoryStack stack, int maxSets) {

            // Vulkan 1.0 spec says: If multiple pool size structures contain the same descriptor type,
            // the pool will be created with enough storage for the total number of descriptors of each type.
            // However, here we still want to calculate the cumulative number of DS for each type.
            int[] perTypeSizes = new int[Engine.DescriptorType.kCount];
            for (int i = 0; i < mDescriptorInfos.length; i++) {
                var entry = mDescriptorInfos[i];

                int type = entry.mType;
                int count = 1;

                // Since a pool only allocates DS with the same layout, we know the exact size for each type
                perTypeSizes[type] += count * maxSets;
            }

            var poolSizes = VkDescriptorPoolSize.calloc(perTypeSizes.length, stack);
            for (int type = 0; type < perTypeSizes.length; type++) {
                int size = perTypeSizes[type];
                if (size != 0) {
                    poolSizes.type(VKUtil.toVkDescriptorType(type))
                            .descriptorCount(size);

                    poolSizes.position(poolSizes.position() + 1);
                }
            }

            return poolSizes.flip();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LayoutDesc that)) return false;

            return Arrays.equals(mDescriptorInfos, that.mDescriptorInfos);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mDescriptorInfos);
        }
    }
}

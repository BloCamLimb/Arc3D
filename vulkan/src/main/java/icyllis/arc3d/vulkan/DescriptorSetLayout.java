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

import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.IResourceKey;
import icyllis.arc3d.engine.Key;
import icyllis.arc3d.engine.KeyBuilder;
import icyllis.arc3d.engine.SamplerDesc;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Engine-level descriptor set layout, holding packed layout data, used to lookup in
 * resource cache for descriptor pool and descriptor sets. Our pool is based on layout,
 * each pool only allocates descriptor sets for certain layout. This can be also unpacked
 * to create actual VkDescriptorSetLayout object when needed.
 */
public final class DescriptorSetLayout implements IResourceKey {

    /**
     * Types of descriptors.
     */
    public static final int
            COMBINED_IMAGE_SAMPLER = 0,
            SAMPLED_IMAGE = 1,
            STORAGE_IMAGE = 2,
            UNIFORM_TEXEL_BUFFER = 3,
            STORAGE_TEXEL_BUFFER = 4,
            UNIFORM_BUFFER = 5,
            STORAGE_BUFFER = 6,
            INPUT_ATTACHMENT = 7,
            ACCELERATION_STRUCTURE = 8;
    private static final int TYPE_COUNT = 9;

    public static final int
            kBindingBits = 8,
            kVisibilityBits = Engine.ShaderFlags.kCount,
            kCountBits = 10,
            kTypeBits = 4,
            kUseImmutableSamplerBits = 1;
    public static final int
            kBindingShift = 0,
            kVisibilityShift = kBindingShift + kBindingBits,
            kCountShift = kVisibilityShift + kVisibilityBits,
            kTypeShift = kCountShift + kCountBits,
            kUseImmutableSamplerShift = kTypeShift + kTypeBits; // variable-length key must have one flag
    public static final int
            kBindingMask = ((1 << kBindingBits) - 1) << kBindingShift,
            kVisibilityMask = ((1 << kVisibilityBits) - 1) << kVisibilityShift,
            kCountMask = ((1 << kCountBits) - 1) << kCountShift,
            kTypeMask = ((1 << kTypeBits) - 1) << kTypeShift,
            kUseImmutableSamplerMask = ((1 << kUseImmutableSamplerBits) - 1) << kUseImmutableSamplerShift;

    static {
        //noinspection ConstantValue
        assert TYPE_COUNT <= (1 << kTypeBits);
        //noinspection ConstantValue
        assert (kUseImmutableSamplerShift + kUseImmutableSamplerBits) <= 32;
    }

    private final Key mLayoutKey;
    private final transient int mBindingCount;

    DescriptorSetLayout(Key key, int bindingCount) {
        mLayoutKey = key;
        mBindingCount = bindingCount;
    }

    public int getBindingCount() {
        return mBindingCount;
    }

    @NonNull
    public Key getKey() {
        return mLayoutKey;
    }

    public VkDescriptorSetLayoutBinding.@NonNull Buffer toVkBindings( @NonNull MemoryStack stack) {
        var bindings = VkDescriptorSetLayoutBinding.calloc(getBindingCount(), stack);
        var key = getKey();
        int[] currentIndex = {0};
        while (currentIndex[0] < key.size()) {
            int bits = key.get(currentIndex[0]++);
            int binding = (bits & kBindingMask) >>> kBindingShift;
            int type = (bits & kTypeMask) >>> kTypeShift;
            int count = (bits & kCountMask) >>> kCountShift;
            int visibility = (bits & kVisibilityMask) >>> kVisibilityShift;
            boolean useImmutableSampler = (bits & kUseImmutableSamplerMask) != 0;

            bindings.binding(binding)
                    .descriptorType(toVkDescriptorType(type))
                    .descriptorCount(count)
                    .stageFlags(VKUtil.toVkPipelineStageFlags(visibility));

            if (useImmutableSampler) {
                assert count == 1;
                SamplerDesc immutableSamplerDesc = SamplerDesc.makeFromKey(key, currentIndex);
                // TODO set this
            }

            bindings.position(bindings.position() + 1);
        }

        assert !bindings.hasRemaining();
        return bindings.rewind();
    }


    public VkDescriptorPoolSize. @NonNull Buffer toVkPoolSizes(  @NonNull MemoryStack stack, int maxSets) {

        // Vulkan 1.0 spec says: If multiple pool size structures contain the same descriptor type,
        // the pool will be created with enough storage for the total number of descriptors of each type.
        // However, here we still want to calculate the cumulative number of DS for each type.
        int[] perTypeSizes = new int[TYPE_COUNT];
        var key = getKey();
        int[] currentIndex = {0};
        while (currentIndex[0] < key.size()) {
            int bits = key.get(currentIndex[0]++);
            int type = (bits & kTypeMask) >>> kTypeShift;
            int count = (bits & kCountMask) >>> kCountShift;
            boolean useImmutableSampler = (bits & kUseImmutableSamplerMask) != 0;

            // Since a pool only allocates DS with the same layout, we know the exact size for each type
            perTypeSizes[type] += count * maxSets;

            if (useImmutableSampler) {
                var ignored = SamplerDesc.makeFromKey(key, currentIndex);
            }
        }

        var poolSizes = VkDescriptorPoolSize.calloc(perTypeSizes.length, stack);
        for (int type = 0; type < perTypeSizes.length; type++) {
            int size = perTypeSizes[type];
            if (size != 0) {
                poolSizes.type(toVkDescriptorType(type))
                        .descriptorCount(size);

                poolSizes.position(poolSizes.position() + 1);
            }
        }

        return poolSizes.flip();
    }

    public static int toVkDescriptorType(int type) {
        //TODO currently we only use dynamic offsets, but backend is guaranteed to support only a
        // small number of dynamic bindings,
        return switch (type) {
            case DescriptorSetLayout.COMBINED_IMAGE_SAMPLER ->
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case DescriptorSetLayout.SAMPLED_IMAGE ->
                    VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case DescriptorSetLayout.STORAGE_IMAGE ->
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case DescriptorSetLayout.UNIFORM_TEXEL_BUFFER ->
                    VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            case DescriptorSetLayout.STORAGE_TEXEL_BUFFER ->
                    VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER;
            case DescriptorSetLayout.UNIFORM_BUFFER ->
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            case DescriptorSetLayout.STORAGE_BUFFER ->
                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
            case DescriptorSetLayout.INPUT_ATTACHMENT ->
                    VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT;
            case DescriptorSetLayout.ACCELERATION_STRUCTURE ->
                    KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            default -> throw new AssertionError(type);
        };
    }

    @Override
    public IResourceKey copy() {
        return this;
    }

    @Override
    public int hashCode() {
        return mLayoutKey.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mLayoutKey.equals(((DescriptorSetLayout) o).mLayoutKey);
    }

    public static final class Builder {

        KeyBuilder keyBuilder;
        int bindingCount;

        public Builder() {
            keyBuilder = new KeyBuilder();
        }

        public Builder(int bindingCount) {
            // without immutable samplers, the number of int32s is the number of bindings,
            // preallocate enough data
            keyBuilder = new KeyBuilder(bindingCount);
        }

        /**
         * Note that resource array is only supported in Vulkan, because declaring array
         * in OpenGL means use multiple consecutive bindings instead of one binding multiple resources,
         * so renderer usually specifies 1 (non-array).
         *
         * @param binding          binding index, 0-255
         * @param type             type as described above
         * @param count            the array count of the resource, 0-1023
         * @param visibility       which stages can access the resource, see {@link Engine.ShaderFlags}
         * @param immutableSampler non-null if it's immutable sampler
         * @return this
         */
        public Builder addBinding(int binding, int type, int count, int visibility,
                                  @Nullable SamplerDesc immutableSampler) {
            assert binding >= 0 && binding < (1 << kBindingBits);
            assert count > 0 && count < (1 << kCountBits);

            // if immutable sampler is used, resource count must be 1 and type must be COMBINED_IMAGE_SAMPLER
            assert immutableSampler == null || (count == 1 && type == COMBINED_IMAGE_SAMPLER);

            //TODO resource array is not allowed in OpenGL,
            // but allowed in Vulkan (VkDescriptorSetLayoutBinding.descriptorCount)
            // our compiler doesn't support resource array yet... see SPIRVCodeGenerator
            assert count == 1;

            keyBuilder.add(
                    (binding    << kBindingShift) |
                    (visibility << kVisibilityShift) |
                    (count      << kCountShift) |
                    (type       << kTypeShift) |
                    (immutableSampler != null ? 1 << kUseImmutableSamplerShift : 0)
            );
            if (immutableSampler != null) {
                immutableSampler.appendToKey(keyBuilder);
            }
            bindingCount++;

            return this;
        }

        @Contract(" -> new")
        public @NonNull DescriptorSetLayout build() {
            return new DescriptorSetLayout(keyBuilder.toStorageKey(), bindingCount);
        }
    }
}

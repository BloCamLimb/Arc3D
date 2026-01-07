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
import icyllis.arc3d.core.WeakIdentityKey;
import icyllis.arc3d.engine.DescriptorSetLayout;
import icyllis.arc3d.engine.Engine.DescriptorType;
import icyllis.arc3d.engine.Sampler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages descriptor sets for a single {@link VulkanDescriptorSetLayout}, actually
 * a descriptor pool manager. Our descriptor set grouping strategy is based on
 * update frequency, so each set has its own manager.
 * <p>
 * Persistable descriptor sets and dynamic descriptor sets are both allowed.
 * Whether persisting is enabled is determined at creation time.
 */
public final class VulkanDescriptorSetManager extends RefCnt {

    @RawPtr
    private final VulkanResourceProvider mResourceProvider;
    @SharedPtr
    private final VulkanDescriptorSetLayout mSetLayout;

    static final class CacheKey {

        static final int kMaxCost = 4;

        static final int kCostPerSampler = 2;
        static final int kCostPerOther = 1;

        /**
         * Holds {@link WeakIdentityKey} of ImageView (sampled image / storage image / input attachment),
         * Buffer (uniform buffer / storage buffer), BufferView (uniform texel buffer / storage texel buffer).
         * <p>
         * For combined image sampler, holds repeated {@link Sampler} and {@link WeakIdentityKey} of ImageView.
         * For immutable sampler, keep Sampler to null.
         */
        final Object[] mResources;

        /**
         * Holds binding range for uniform buffer / storage buffer. Otherwise 0.
         */
        final int[] mBindingSizes;

        public CacheKey() {
            mResources = new Object[kMaxCost];
            mBindingSizes = new int[kMaxCost];
        }

        @SuppressWarnings("IncompleteCopyConstructor")
        public CacheKey(@NonNull CacheKey other) {
            // elements are pointers, shallow copy the array
            mResources = other.mResources.clone();
            mBindingSizes = other.mBindingSizes.clone();
        }



        public void update(@RawPtr VulkanDescriptorSetLayout layout,
                           VulkanCommandBuffer.SetBindingState bindingState) {
            int j = 0;
            int bindingCount = layout.getBindingCount();

            for (int binding = 0; binding < bindingCount; binding++) {
                int type = layout.getType(binding);
                boolean used = layout.isUsed(binding);

                switch (type) {
                    case DescriptorType.kCombinedImageSampler:
                        mResources[j] = bindingState.getSampler(binding);
                        mBindingSizes[j] = 0;
                        j++;
                        // fallthrough

                    case DescriptorType.kSampledImage:
                    case DescriptorType.kStorageImage:
                    case DescriptorType.kInputAttachment:
                        if (used) {
                            mResources[j] = bindingState.getImageView(binding).getUniqueID();
                        } else {
                            mResources[j] = null;
                        }
                        mBindingSizes[j] = 0;
                        break;

                    case DescriptorType.kUniformBuffer:
                    case DescriptorType.kStorageBuffer:
                        if (used) {
                            mResources[j] = bindingState.getBuffer(binding).getUniqueID();
                            mBindingSizes[j] = bindingState.mBindingSizes[binding];
                        } else {
                            mResources[j] = null;
                            mBindingSizes[j] = 0;
                        }
                        break;

                    case DescriptorType.kUniformTexelBuffer:
                    case DescriptorType.kStorageTexelBuffer:
                        //TODO VulkanBufferView
                        mBindingSizes[j] = 0;
                        break;

                    case DescriptorType.kAccelerationStructure:
                        mBindingSizes[j] = 0;
                        break;
                }
                j++;
            }

            for (; j < kMaxCost; j++) {
                mResources[j] = null;
                mBindingSizes[j] = 0;
            }

            assert j == kMaxCost;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey that)) return false;

            for (int i = 0; i < kMaxCost; i++) {
                if (!Objects.equals(mResources[i], that.mResources[i]))
                    return false;
            }

            for (int i = 0; i < kMaxCost; i++) {
                if (mBindingSizes[i] != that.mBindingSizes[i])
                    return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(mResources);
            result = 31 * result + Arrays.hashCode(mBindingSizes);
            return result;
        }
    }

    // Once the Manager switches the current DescriptorPool, the old pool can only be
    // available in ResourceCache after all DescriptorSets allocated from it are no longer in use
    // (by both the client and any submitted command buffers).
    //
    // This avoids persistent DescriptorSets from different pools where those pools are no longer
    // referenced by the Manager, yet the Manager still holds references to those DescriptorSets,
    // causing the pools to never be available (memory fragmentation).

    static class DescriptorSetCache extends LinkedHashMap<CacheKey, @SharedPtr VulkanDescriptorSet> {

        private final int maxCachedEntries;

        // LRU cache with loadFactor of 0.75
        DescriptorSetCache(int maxCachedEntries) {
            super(/*initialCapacity*/   (int) Math.ceil(maxCachedEntries / 0.75f),
                    /*loadFactor*/      0.75f,
                    /*accessOrder*/     true);
            this.maxCachedEntries = maxCachedEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, @SharedPtr VulkanDescriptorSet> eldest) {
            if (size() > maxCachedEntries) {
                // allow the descriptor set to return the mCachedPool as soon as possible
                eldest.getValue().unref();
                return true;
            }
            return false;
        }
    }

    private final DescriptorSetCache mCache;
    private final CacheKey mLookupCacheKey;
    // Perhaps we could make CachedPool grow like CurrentPool, but this would
    // require moving DescriptorSets using VkCopyDescriptorSet.
    // The current solution is to switch to a per-frame update scheme if CachedPool is full.
    @SharedPtr
    private final VulkanDescriptorPool mCachedPool;

    // Initially, current pool is the same as cached pool. This is OK, because for the same Manager,
    // we either always prioritize using cache or never use cache

    @SharedPtr
    private VulkanDescriptorPool mCurrentPool;
    private int mCurrentPoolSize;
    private final int mMaxPoolSize;

    public VulkanDescriptorSetManager(@RawPtr VulkanResourceProvider resourceProvider,
                                      @SharedPtr VulkanDescriptorSetLayout setLayout,
                                      @SharedPtr VulkanDescriptorPool initialPool,
                                      int initialPoolSize, int maxPoolSize, boolean useCache) {
        mResourceProvider = resourceProvider;
        mSetLayout = setLayout;

        mCurrentPool = initialPool;
        if (useCache) {
            mLookupCacheKey = new CacheKey();
            mCachedPool = RefCnt.create(initialPool);

            // We expect cached and free descriptor sets to each account for half, considering
            // cache hit rate and in-flight descriptor sets.
            final int maxCachedEntries = initialPoolSize / 2;
            mCache = new DescriptorSetCache(maxCachedEntries);
        } else {
            mLookupCacheKey = null;
            mCachedPool = null;
            mCache = null;
        }

        mCurrentPoolSize = Math.min(initialPoolSize, maxPoolSize);
        mMaxPoolSize = maxPoolSize;
    }

    @Nullable
    @SharedPtr
    public static VulkanDescriptorSetManager make(@RawPtr @NonNull VulkanDevice device,
                                                  @RawPtr @NonNull VulkanResourceProvider resourceProvider,
                                                  @RawPtr @NonNull DescriptorSetLayout layoutInfo) {
        @SharedPtr
        VulkanDescriptorSetLayout layout = VulkanDescriptorSetLayout.make(
                device,
                layoutInfo
        );
        if (layout == null) {
            return null;
        }

        int cost = 0;
        for (int i = 0; i < layoutInfo.getBindingCount(); i++) {
            var entry = layoutInfo.getDescriptorInfo(i);
            cost += entry.mType == DescriptorType.kCombinedImageSampler
                    ? CacheKey.kCostPerSampler : CacheKey.kCostPerOther;
        }
        boolean useCache = cost <= CacheKey.kMaxCost;

        // may need to be fine-tuned based on actual usage
        int initialPoolSize;
        int maxPoolSize;
        if (useCache) {
            // maxCachedSets = initialPoolSize / 2
            switch (layoutInfo.getBindingCount()) {
                case 1:
                    if (layoutInfo.getDescriptorInfo(0).mType == DescriptorType.kCombinedImageSampler) {
                        initialPoolSize = 1024;
                    } else {
                        initialPoolSize = 512;
                    }
                    break;
                case 2:
                    initialPoolSize = 256;
                    break;
                default:
                    initialPoolSize = 128;
                    break;
            }
            maxPoolSize = 256;
        } else {
            initialPoolSize = 16;
            maxPoolSize = 128;
        }

        @SharedPtr
        VulkanDescriptorPool initialPool = resourceProvider.findOrCreateDescriptorPool(layout, initialPoolSize);
        if (initialPool == null) {
            layout.unref();
            return null;
        }

        return new VulkanDescriptorSetManager(
                resourceProvider, layout, initialPool,
                initialPoolSize, maxPoolSize, useCache
        );
    }

    @Nullable
    @SharedPtr
    public VulkanDescriptorSet findOrCreateDescriptorSet(
            @NonNull @RawPtr VulkanDevice device,
            VulkanCommandBuffer.@NonNull @RawPtr SetBindingState bindingState
    ) {
        if (mCache != null) {

            mLookupCacheKey.update(mSetLayout, bindingState);

            VulkanDescriptorSet descriptorSet = mCache.get(mLookupCacheKey);
            if (descriptorSet != null) {
                descriptorSet.ref();
                return descriptorSet;
            }

            descriptorSet = mCachedPool.obtainDescriptorSet();
            if (descriptorSet != null) {

                updateDescriptorSet(device, descriptorSet, bindingState);

                var copiedKey = new CacheKey(mLookupCacheKey);

                mCache.put(copiedKey, descriptorSet); // move

                descriptorSet.ref();
                return descriptorSet;
            }

            // fallback to uncached case
        }

        @SharedPtr
        VulkanDescriptorSet descriptorSet = mCurrentPool.obtainDescriptorSet();
        if (descriptorSet == null) {
            descriptorSet = nextPool();

            if (descriptorSet == null) {
                return null;
            }
        }

        updateDescriptorSet(device, descriptorSet, bindingState);

        return descriptorSet;
    }

    @Nullable
    @SharedPtr
    private VulkanDescriptorSet nextPool() {
        mCurrentPoolSize = Math.min(mCurrentPoolSize * 2, mMaxPoolSize);
        @SharedPtr
        VulkanDescriptorPool newPool = mResourceProvider.findOrCreateDescriptorPool(
                mSetLayout, // <- raw ptr
                mCurrentPoolSize);
        if (newPool == null) {
            return null;
        }
        mCurrentPool = RefCnt.move(mCurrentPool, newPool);
        return newPool.obtainDescriptorSet();
    }

    private void updateDescriptorSet(
            @NonNull @RawPtr VulkanDevice device,
            @NonNull @RawPtr VulkanDescriptorSet descriptorSet,
            VulkanCommandBuffer.@NonNull @RawPtr SetBindingState bindingState
    ) {
        @RawPtr
        var layout = mSetLayout;
        try (var stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo
                    .malloc(1, stack);
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo
                    .malloc(1, stack);

            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet
                    .calloc(1, stack)
                    .sType$Default()
                    .dstSet(descriptorSet.vkDescriptorSet())
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
                    .pBufferInfo(bufferInfo);

            int bindingCount = layout.getBindingCount();
            for (int binding = 0; binding < bindingCount; binding++) {
                if (!layout.isUsed(binding)) {
                    continue;
                }
                int type = layout.getType(binding);
                write
                        .dstBinding(binding)
                        .descriptorType(VKUtil.toVkDescriptorType(type));

                boolean shaderReadOnly = false;
                switch (type) {
                    case DescriptorType.kCombinedImageSampler:
                    case DescriptorType.kSampledImage:
                        shaderReadOnly = true;
                        // fallthrough
                    case DescriptorType.kStorageImage:
                    case DescriptorType.kInputAttachment:

                        // StorageImage and InputAttachment should transition to GENERAL when read/write,
                        // so tell the driver that we assume this layout

                        imageInfo
                                .sampler(type == DescriptorType.kCombinedImageSampler
                                        ? bindingState.getSampler(binding).vkSampler()
                                        : VK_NULL_HANDLE)
                                .imageView(bindingState.getImageView(binding).vkImageView())
                                .imageLayout(shaderReadOnly
                                        ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                        : VK_IMAGE_LAYOUT_GENERAL);
                        break;

                    case DescriptorType.kUniformBuffer:
                    case DescriptorType.kStorageBuffer:

                        bufferInfo
                                .buffer(bindingState.getBuffer(binding).vkBuffer())
                                .offset(0) // will use dynamic offsets
                                .range(Integer.toUnsignedLong(bindingState.mBindingSizes[binding]));

                        break;

                    case DescriptorType.kUniformTexelBuffer:
                    case DescriptorType.kStorageTexelBuffer:

                        //TODO VulkanBufferView

                        break;

                    case DescriptorType.kAccelerationStructure:
                        // VkWriteDescriptorSetAccelerationStructureKHR ???
                        // One day we'll use ray tracing pipeline
                        break;
                }

                vkUpdateDescriptorSets(
                        device.vkDevice(),
                        write,
                        null
                );
            }
        }
    }

    public @RawPtr VulkanDescriptorSetLayout getLayout() {
        return mSetLayout;
    }

    @Override
    protected void deallocate() {
        if (mCache != null) {
            mCache.values().forEach(VulkanDescriptorSet::unref);
            mCache.clear();
        }
        if (mCachedPool != null) {
            mCachedPool.unref();
        }
        mCurrentPool = RefCnt.move(mCurrentPool);
        mSetLayout.unref();
    }
}

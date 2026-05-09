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

package icyllis.arc3d.engine;

import icyllis.arc3d.core.RawPtr;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;

/**
 * Engine-level class that represents a descriptor set layout.
 * Binding index is the same as array index. The array index of the set array represents
 * the set number.
 * <p>
 * In OpenGL, CombinedImageSampler/SampledImage of same image view type must appear in the
 * same set. StorageImage/StorageTexelBuffer must appear in the same set.
 * In Vulkan, descriptor sets are essential and must be assigned carefully to reduce
 * the number of resource binding operations: resources that are updated less frequently
 * should use smaller set numbers, and resources that are updated together should be
 * grouped into the same set.
 * <p>
 * Note that resource array is not supported, because resource array is only supported in Vulkan,
 * and declaring array in OpenGL means use multiple consecutive bindings instead of one binding
 * multiple resources, so renderer usually specifies 1 (non-array).
 * <p>
 * This class is hashable and can be used as hash key.
 */
@Immutable
public final class DescriptorSetLayout {

    /**
     * Represents a single resource binding info.
     */
    @Immutable
    public static final class DescriptorInfo {
        /**
         * OpenGL-only. For interface block, this is block name; otherwise, this is variable name.
         */
        public final transient String mName;
        /**
         * See {@link Engine.DescriptorType}
         */
        public final int mType;
        /**
         * Which shader stages can access the resource, see {@link Engine.ShaderFlags}
         */
        public final int mVisibility;
        /**
         * Non-null if it's immutable sampler.
         */
        @Nullable
        @RawPtr
        public final Sampler mImmutableSampler;

        //TODO if resource array is useful, we can allow it in Vulkan backend, but
        // our compiler doesn't support resource array yet... see SPIRVCodeGenerator

        public DescriptorInfo(String name, int type, int visibility,
                              @Nullable @RawPtr Sampler immutableSampler) {
            mName = name;
            mType = type;
            mVisibility = visibility;
            mImmutableSampler = immutableSampler;

            assert immutableSampler == null || (type == Engine.DescriptorType.kCombinedImageSampler);
        }

        public DescriptorInfo(String name, int type, int visibility) {
            this(name, type, visibility, null);
        }

        public DescriptorInfo(int type, int visibility,
                              @Nullable @RawPtr Sampler immutableSampler) {
            this("", type, visibility, immutableSampler);
        }

        public DescriptorInfo(int type, int visibility) {
            this("", type, visibility, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DescriptorInfo that)) return false;

            return mType == that.mType &&
                    mVisibility == that.mVisibility &&
                    mImmutableSampler == that.mImmutableSampler;
        }

        @Override
        public int hashCode() {
            int result = mType;
            result = 31 * result + mVisibility;
            result = 31 * result + System.identityHashCode(mImmutableSampler);
            return result;
        }
    }

    private final @NonNull DescriptorInfo @NonNull [] mDescriptorInfos;
    private final transient int mHash;

    public DescriptorSetLayout(@NonNull DescriptorInfo @NonNull ... bindings) {
        // inner struct is already immutable, just shallow copy the array
        mDescriptorInfos = bindings.clone();
        mHash = Arrays.hashCode(mDescriptorInfos);
    }

    public @NonNull DescriptorInfo getDescriptorInfo(int binding) {
        return mDescriptorInfos[binding];
    }

    public int getBindingCount() {
        return mDescriptorInfos.length;
    }

    public boolean hasImmutableSamplers() {
        for (var entry : mDescriptorInfos) {
            if (entry.mImmutableSampler != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DescriptorSetLayout that)) return false;

        return Arrays.equals(mDescriptorInfos, that.mDescriptorInfos);
    }

    @Override
    public int hashCode() {
        return mHash;
    }
}

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
import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.FramebufferCache;
import icyllis.arc3d.engine.FramebufferDesc;
import icyllis.arc3d.engine.RenderPassDesc;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines a compatibility domain and provides a set of VkRenderPass that are
 * compatible with each other.
 * <p>
 * This object is tracked and managed by {@link VulkanGraphicsPipeline}. And this object
 * manages {@link VulkanRenderPass} and also {@link VulkanRenderPassFramebuffer}.
 */
public final class VulkanRenderPassSet extends RefCnt {

    // We use two ints uniquely defines a compatible render pass.
    //
    // Render pass flags (F): defined in RenderPassDesc, fits in 3 bits
    // Sample count (M): store the log2 of sample count, fits in 3 bits
    // Has resolve attachment (R): whether to use color attachment, fits in 1 bit
    // Depth stencil format (DS): ImageFormat, fits in 6 bits
    // Color attachment count (C): number of attachments, 0..8, fits in 4 bits
    //
    // The remaining bits represent color formats, while kUnsupported represents placeholders,
    // (so the count is required in the key).
    // If more flags are added in the future, we can still pack them into a key in a more compact way.
    //
    // key1  LSB                               MSB
    //       +---+---+---+----+---+-----+-----+---+
    //       | F | M | R | DS | C | CF0 | CF1 | 0 |
    //       +---+---+---+----+---+-----+-----+---+
    //  bits   3   3   1   6    4    5     5    5
    //
    // key2  LSB                               MSB
    //       +-----+-----+-----+-----+-----+-----+---+
    //       | CF2 | CF3 | CF4 | CF5 | CF6 | CF7 | 0 |
    //       +-----+-----+-----+-----+-----+-----+---+
    //  bits    5     5     5     5     5     5    2

    public static final int kRenderPassFlagsBits = RenderPassDesc.kFlagsBits;
    public static final int kSampleCountBits = 3;
    public static final int kHasResolveAttachmentBits = 1;
    public static final int kDepthStencilFormatBits = 6;
    public static final int kColorCountBits = 4;
    public static final int kColorFormatBits = kDepthStencilFormatBits - 1;

    public static final int kRenderPassFlagsShift = 0;
    public static final int kSampleCountShift = kRenderPassFlagsShift + kRenderPassFlagsBits;
    public static final int kHasResolveAttachmentShift = kSampleCountShift + kSampleCountBits;
    public static final int kDepthStencilFormatShift = kHasResolveAttachmentShift + kHasResolveAttachmentBits;
    public static final int kColorCountShift = kDepthStencilFormatShift + kDepthStencilFormatBits;
    public static final int kColorFormat0Shift = kColorCountShift + kColorCountBits;
    public static final int kColorFormat1Shift = kColorFormat0Shift + kColorFormatBits;

    static {
        //noinspection ConstantValue
        assert kRenderPassFlagsBits == 3; // currently it's 3
        //noinspection ConstantValue
        assert Engine.ImageFormat.kCount <= (1 << kDepthStencilFormatBits);
        //noinspection ConstantValue
        assert (Engine.ImageFormat.kLastColor + 1) <= (1 << kColorFormatBits); // currently there are 26 color formats, fits in 5 bits
        //noinspection ConstantValue
        assert Engine.ImageFormat.kUnsupported == 0;
    }

    public static final class CompatibleKey {

        private int key1;
        private int key2;

        public CompatibleKey() {
        }

        public CompatibleKey(@NonNull CompatibleKey other) {
            this.key1 = other.key1;
            this.key2 = other.key2;
        }

        /**
         * Generate a key that can uniquely represent a compatible render pass.
         */
        public CompatibleKey update(@NonNull RenderPassDesc desc) {
            // see above descriptions
            assert desc.mSampleCount >= 1 && desc.mSampleCount <= 128;
            assert desc.mColorAttachments.length <= Caps.MAX_COLOR_TARGETS;

            int bits1 = desc.mRenderPassFlags << kRenderPassFlagsShift;
            bits1 |= (MathUtil.floorLog2(desc.mSampleCount)) << kSampleCountShift;
            bits1 |= desc.mHasColorResolveAttachment ? (1 << kHasResolveAttachmentShift) : 0;
            // kUnsupported (0) means no depth/stencil
            bits1 |= desc.mDepthStencilFormat << kDepthStencilFormatShift;
            // 0 is allowed
            bits1 |= desc.mColorAttachments.length << kColorCountShift;

            int bits2 = 0; // kUnsupported (0), all zeros by default

            switch (desc.mColorAttachments.length) {
                case Caps.MAX_COLOR_TARGETS:
                case 7:
                case 6:
                case 5:
                case 4:
                case 3:
                    // 2..7 are encapsulated in the second key
                    for (int i = 2; i < desc.mColorAttachments.length; i++) {
                        bits2 |= (desc.mColorAttachments[i].mFormat) << (kColorFormatBits * (i - 2));
                    }
                    // fallthrough
                case 2:
                    bits1 |= desc.mColorAttachments[1].mFormat << kColorFormat1Shift;
                    // fallthrough
                case 1:
                    bits1 |= desc.mColorAttachments[0].mFormat << kColorFormat0Shift;
                    // fallthrough
                default:
                    break;
            }

            key1 = bits1;
            key2 = bits2;

            return this;
        }

        @Contract(value = " -> new", pure = true)
        public @NonNull CompatibleKey copy() {
            return new CompatibleKey(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CompatibleKey that)) return false;

            return key1 == that.key1 && key2 == that.key2;
        }

        @Override
        public int hashCode() {
            int result = key1;
            result = 31 * result + key2;
            return result;
        }
    }

    // There won't be too many combinations in practice, so only 12 will be kept.
    //
    // In addition, 0 is the standard RenderPass used to create graphics pipelines,
    // which will hold a ref to this RenderPassSet but no ref to the RenderPass,
    // so it must not be deleted!
    private static final int kMaxCachedRenderPasses = 12;
    private final ObjectArrayList<@SharedPtr VulkanRenderPass> mRenderPasses
            = new ObjectArrayList<>(kMaxCachedRenderPasses);
    private int mLastReturnedIndex;

    // framebuffer is definitely managed by RP Set
    private final FramebufferCache mFramebufferCache = new FramebufferCache();

    private VulkanRenderPassSet(@NonNull @SharedPtr VulkanRenderPass standardRenderPass) {
        mRenderPasses.add(standardRenderPass); // move
        mLastReturnedIndex = 0;
    }

    @Nullable
    @SharedPtr
    public static VulkanRenderPassSet make(@NonNull VulkanDevice device,
                                           @NonNull RenderPassDesc desc) {
        // create a standard render pass that is likely to be reused later
        desc = new RenderPassDesc(desc);

        if ((desc.mRenderPassFlags & RenderPassDesc.kLoadFromResolve_Flag) != 0) {
            assert desc.mColorAttachments.length == 1;
            for (var colorAttachment : desc.mColorAttachments) {
                colorAttachment.mLoadOp = Engine.LoadOp.kDiscard;
                colorAttachment.mStoreOp = Engine.StoreOp.kDiscard;
            }
            assert desc.mHasColorResolveAttachment;
            desc.mColorResolveLoadOp = Engine.LoadOp.kLoad;
            desc.mColorResolveStoreOp = Engine.StoreOp.kStore;
        } else {
            for (var colorAttachment : desc.mColorAttachments) {
                colorAttachment.mLoadOp = Engine.LoadOp.kLoad;
                colorAttachment.mStoreOp = Engine.StoreOp.kStore;
            }
            desc.mColorResolveLoadOp = Engine.LoadOp.kDiscard;
            desc.mColorResolveStoreOp = Engine.StoreOp.kStore;
        }
        desc.mDepthStencilLoadOp = Engine.LoadOp.kClear;
        desc.mDepthStencilStoreOp = Engine.StoreOp.kDiscard;

        @SharedPtr
        VulkanRenderPass standardRenderPass = VulkanRenderPass.make(device, desc);
        if (standardRenderPass == null) {
            return null;
        }
        return new VulkanRenderPassSet(standardRenderPass); // move
    }

    @NonNull
    @RawPtr
    public VulkanRenderPass getCompatibleRenderPass() {
        return mRenderPasses.get(0);
    }

    @Nullable
    @SharedPtr
    public VulkanRenderPass findOrCreateRenderPass(@NonNull VulkanDevice device,
                                                   @NonNull RenderPassDesc desc) {
        int denseLoadStoreOps = VulkanRenderPass.extractLoadStoreOps(desc);
        for (int i = 0; i < mRenderPasses.size(); i++) {
            int idx = (i + mLastReturnedIndex) % mRenderPasses.size();
            VulkanRenderPass renderPass = mRenderPasses.get(idx);
            if (renderPass.getDenseLoadStoreOps() == denseLoadStoreOps) {
                mLastReturnedIndex = idx;
                renderPass.ref();
                return renderPass;
            }
        }
        @SharedPtr
        VulkanRenderPass renderPass = VulkanRenderPass.make(device, desc);
        if (renderPass == null) {
            return null;
        }
        if (mRenderPasses.size() < kMaxCachedRenderPasses) {
            mRenderPasses.add(renderPass); // move
            mLastReturnedIndex = mRenderPasses.size() - 1;
        } else {
            // randomly delete a render pass, 0 cannot be deleted, start from 1
            mLastReturnedIndex = ThreadLocalRandom.current().nextInt(1, kMaxCachedRenderPasses);
            mRenderPasses.set(mLastReturnedIndex, renderPass) // swap
                    .unref();
        }
        renderPass.ref();
        return renderPass;
    }

    @Nullable
    @SharedPtr
    public VulkanRenderPassFramebuffer findOrCreateFramebuffer(@NonNull VulkanDevice device,
                                                               @NonNull FramebufferDesc desc) {
        return null;
    }

    public void purgeStaleFramebuffers() {
        mFramebufferCache.purgeStaleFramebuffers();
    }

    @Override
    protected void deallocate() {
        // this can be called from any thread
        //TODO post to executing thread
        synchronized (this) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < mRenderPasses.size(); i++) {
                RefCnt.move(mRenderPasses.get(i));
            }
            mRenderPasses.clear();

            mFramebufferCache.close();
        }
    }
}

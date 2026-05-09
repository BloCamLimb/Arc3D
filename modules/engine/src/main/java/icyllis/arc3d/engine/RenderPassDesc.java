/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.Size;
import icyllis.arc3d.engine.Engine.ImageFormat;
import icyllis.arc3d.engine.Engine.LoadOp;
import icyllis.arc3d.engine.Engine.StoreOp;
import org.jspecify.annotations.NonNull;

/**
 * Information about a render pass. This structure is designed to be mutable.
 */
public final class RenderPassDesc {

    public static final class AttachmentDesc {
        public int mFormat = ImageFormat.kUnsupported;
        public byte mLoadOp = LoadOp.kDiscard;
        public byte mStoreOp = StoreOp.kDiscard;

        public AttachmentDesc() {
        }

        public AttachmentDesc(RenderPassDesc.@NonNull AttachmentDesc other) {
            mFormat = other.mFormat;
            mLoadOp = other.mLoadOp;
            mStoreOp = other.mStoreOp;
        }

        /**
         * False if this attachment is unused, a placeholder to maintain ABI, layout(location = N).
         */
        public boolean isUsed() {
            return mFormat != ImageFormat.kUnsupported;
        }
    }

    //// Color Targets

    public static final @NonNull AttachmentDesc @NonNull [] NO_COLOR_ATTACHMENTS = new AttachmentDesc[0];
    /**
     * If {@link AttachmentDesc#isUsed()} return false, it
     * means it's a mock/placeholder attachment. This array can be empty.
     */
    public @NonNull @Size(max = Caps.MAX_COLOR_TARGETS) AttachmentDesc @NonNull [] mColorAttachments = NO_COLOR_ATTACHMENTS;


    //// Color Resolve Target (single RT case)

    /**
     * If {@link AttachmentDesc#isUsed()} return true, it
     * means there's color resolve attachment, and MSAA will be auto resolved,
     * and resolve image format must match MSAA image format, and resolve StoreOp should be Store.
     * <p>
     * If MSAA LoadOp is Load/Clear, then resolve LoadOp should be Discard.
     */
    public final @NonNull AttachmentDesc mColorResolveAttachment;


    //// Depth-Stencil Target (no resolve)

    /**
     * If {@link AttachmentDesc#isUsed()} return true, it
     * means there's depth stencil attachment.
     */
    public final @NonNull AttachmentDesc mDepthStencilAttachment;


    //// Overall Settings

    //TODO TBD reserved for future use
    public int mSampleCount = 1;


    //// Subpass & Dependency Settings

    /**
     * True to make all pipelines in the mainpass to be compatible to use dst texture as
     * input attachment, and auto bind the input attachment on begin.
     */
    public static final int kUseDstAsInput_Flag = 0x1;
    /**
     * True to make all pipelines in the mainpass to be compatible to add pipeline barrier
     * for non-coherent advanced blends.
     */
    public static final int kUseNonCoherentAdvBlend_Flag = 0x2;
    /**
     * True to add a load subpass & inline draw, and MSAA will load from resolve attachment.
     * In this case, if resolve LoadOp must be Load, and MSAA LoadOp must be Discard.
     */
    public static final int kLoadFromResolve_Flag = 0x4;

    public static final int kFlagsBits = 3; // 3 bits to store all flags

    public int mRenderPassFlags = 0;

    public RenderPassDesc() {
        mColorResolveAttachment = new AttachmentDesc();
        mDepthStencilAttachment = new AttachmentDesc();
    }

    @SuppressWarnings("IncompleteCopyConstructor")
    public RenderPassDesc(@NonNull RenderPassDesc other) {
        if (other.mColorAttachments.length > 0) {
            mColorAttachments = new AttachmentDesc[other.mColorAttachments.length];
            for (int i = 0; i < mColorAttachments.length; i++) {
                mColorAttachments[i] = new AttachmentDesc(other.mColorAttachments[i]);
            }
        }
        mColorResolveAttachment = new AttachmentDesc(other.mColorResolveAttachment);
        mDepthStencilAttachment = new AttachmentDesc(other.mDepthStencilAttachment);
        mSampleCount = other.mSampleCount;
        mRenderPassFlags = other.mRenderPassFlags;
    }
}

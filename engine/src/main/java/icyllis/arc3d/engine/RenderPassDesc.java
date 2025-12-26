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

    //// Color Targets

    public static final class ColorAttachmentDesc {
        public int mFormat = ImageFormat.kUnsupported;
        public byte mLoadOp = LoadOp.kDiscard;
        public byte mStoreOp = StoreOp.kDiscard;

        public ColorAttachmentDesc() {
        }

        public ColorAttachmentDesc(int format, byte loadOp, byte storeOp) {
            mFormat = format;
            mLoadOp = loadOp;
            mStoreOp = storeOp;
        }

        public ColorAttachmentDesc(@NonNull ColorAttachmentDesc other) {
            mFormat = other.mFormat;
            mLoadOp = other.mLoadOp;
            mStoreOp = other.mStoreOp;
        }
    }

    public static final @NonNull ColorAttachmentDesc @NonNull [] NO_COLOR_ATTACHMENTS = new ColorAttachmentDesc[0];
    public @NonNull @Size(max = Caps.MAX_COLOR_TARGETS) ColorAttachmentDesc @NonNull [] mColorAttachments = NO_COLOR_ATTACHMENTS;


    //// Color Resolve Target (single RT case)

    /*
     * When true, MSAA will be auto resolved, and resolve image format always matches
     * MSAA image format, and mColorResolveStoreOp should be Store.
     */
    public boolean mHasColorResolveAttachment = false;
    /*
     * If mLoadOp is Load/Clear, then mColorResolveLoadOp should be Discard.
     */
    public byte mColorResolveLoadOp = LoadOp.kDiscard;
    public byte mColorResolveStoreOp = StoreOp.kDiscard;


    //// Depth-Stencil Target (no resolve)

    public int mDepthStencilFormat = ImageFormat.kUnsupported;
    public byte mDepthStencilLoadOp = LoadOp.kDiscard;
    public byte mDepthStencilStoreOp = StoreOp.kDiscard;


    //// Overall Settings

    //TODO TBD reserved for future use
    public int mSampleCount = 1;


    //// Subpass Settings

    /*
     * True to make all pipelines in the mainpass to be compatible to use dst texture as
     * input attachment, and auto bind the input attachment on begin.
     */
    public static final int kUseDstAsInput_Flag = 0x1;
    /*
     * True to make all pipelines in the mainpass to be compatible to add pipeline barrier
     * for non-coherent advanced blends.
     */
    public static final int kUseNonCoherentAdvBlend_Flag = 0x2;
    /*
     * True to add a subpass & inline draw, and MSAA will load from resolve attachment.
     * In this case, if mColorResolveLoadOp must be Load, and mLoadOp must be Discard.
     */
    public static final int kLoadFromResolve_Flag = 0x4;

    public static final int kFlagsBits = 3; // 3 bits to store all flags

    public int mRenderPassFlags = 0;

    public RenderPassDesc() {
    }

    @SuppressWarnings("IncompleteCopyConstructor")
    public RenderPassDesc(@NonNull RenderPassDesc other) {
        if (other.mColorAttachments.length > 0) {
            mColorAttachments = new ColorAttachmentDesc[other.mColorAttachments.length];
            for (int i = 0; i < mColorAttachments.length; i++) {
                mColorAttachments[i] = new ColorAttachmentDesc(other.mColorAttachments[i]);
            }
        }
        mHasColorResolveAttachment = other.mHasColorResolveAttachment;
        mColorResolveLoadOp = other.mColorResolveLoadOp;
        mColorResolveStoreOp = other.mColorResolveStoreOp;
        mDepthStencilFormat = other.mDepthStencilFormat;
        mDepthStencilLoadOp = other.mDepthStencilLoadOp;
        mDepthStencilStoreOp = other.mDepthStencilStoreOp;
        mSampleCount = other.mSampleCount;
        mRenderPassFlags = other.mRenderPassFlags;
    }
}

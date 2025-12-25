/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2024 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.engine.Engine.ImageFormat;
import icyllis.arc3d.engine.Engine.LoadOp;
import icyllis.arc3d.engine.Engine.StoreOp;
import org.jspecify.annotations.NonNull;

/**
 * Information about a render pass. This structure is designed to be mutable.
 */
public final class RenderPassDesc {

    //// Color Targets

    public static class ColorAttachmentDesc {
        public int mFormat = ImageFormat.kUnsupported;
        public byte mLoadOp = LoadOp.kDiscard;
        public byte mStoreOp = StoreOp.kDiscard;
    }

    public static final @NonNull ColorAttachmentDesc @NonNull [] NO_COLOR_ATTACHMENTS = new ColorAttachmentDesc[0];
    public @NonNull ColorAttachmentDesc @NonNull [] mColorAttachments = NO_COLOR_ATTACHMENTS;


    //// Color Resolve Target (single RT case)

    // if this is true, MSAA will be auto resolved (fullscreen, cannot be disabled),
    // and resolve image format always matches MSAA image format
    public boolean mHasColorResolveAttachment = false;
    // if mHasResolveAttachment is true,
    // 1) if mLoadOp is Load/Clear, then mResolveLoadOp must be Discard
    // 2) if mResolveLoadOp is not Discard, it must be Load, and mLoadOp must be Discard;
    //    in this special case, MSAA will load from resolve attachment
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

    // vulkan only feature
    public boolean mSetupDstAsInput = false;
    //TODO maybe provide a way to specify input attachments and subpasses explicitly
}

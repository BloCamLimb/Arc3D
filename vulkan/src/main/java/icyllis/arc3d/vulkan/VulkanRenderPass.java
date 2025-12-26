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

import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ManagedResource;
import icyllis.arc3d.engine.RenderPassDesc;
import org.jspecify.annotations.NonNull;

/**
 * Managed by {@link VulkanRenderPassSet} or tracked by in-flight command buffer.
 */
public final class VulkanRenderPass extends ManagedResource {

    public static final int kLoadOpBits = 2;
    public static final int kStoreOpBits = 1;
    public static final int kLoadStoreOpsBits = kLoadOpBits + kStoreOpBits;

    static {
        //noinspection ConstantValue
        assert Engine.LoadOp.kCount <= (1 << kLoadOpBits);
        //noinspection ConstantValue
        assert Engine.StoreOp.kCount <= (1 << kStoreOpBits);
        //noinspection ConstantValue
        assert (Caps.MAX_COLOR_TARGETS + 1 + 1) * kLoadStoreOpsBits <= Integer.SIZE;
        // MRT + one resolve + one depth/stencil, 30 bits used
    }

    /**
     * Extracts all the load/store ops in the desc into int32 key.
     */
    public static int extractLoadStoreOps(@NonNull RenderPassDesc desc) {
        assert desc.mColorAttachments.length <= Caps.MAX_COLOR_TARGETS;
        int bits = 0;
        for (int i = 0; i < desc.mColorAttachments.length; i++) {
            var attachment = desc.mColorAttachments[i];
            bits |= ((attachment.mLoadOp) | (attachment.mStoreOp << kLoadOpBits))
                    << (kLoadStoreOpsBits * i);
        }
        bits |= ((desc.mColorResolveLoadOp) | (desc.mColorResolveStoreOp << kLoadOpBits))
                << (kLoadStoreOpsBits * (Caps.MAX_COLOR_TARGETS));
        bits |= ((desc.mDepthStencilLoadOp) | (desc.mDepthStencilStoreOp << kLoadOpBits))
                << (kLoadStoreOpsBits * (Caps.MAX_COLOR_TARGETS + 1));
        return bits;
    }

    public VulkanRenderPass(VulkanDevice device) {
        super(device);
    }

    @Override
    protected void deallocate() {

    }
}

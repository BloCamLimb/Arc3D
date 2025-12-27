/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.core.Size;
import icyllis.arc3d.core.WeakIdentityKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Descriptor to create a framebuffer.
 */
//TODO experimental, to be reviewed
@Immutable
public final class FramebufferDesc {

    /**
     * This is a OpenGL only flag. It tells us that the internal render target wraps the OpenGL
     * default framebuffer (id=0) that preserved by window. RT only.
     */
    @ApiStatus.Internal
    public static final
    int FLAG_GL_WRAP_DEFAULT_FB = ISurface.FLAG_PROTECTED << 4;
    /**
     * This means the render target is multi-sampled, and internally holds a non-msaa image
     * for resolving into. The render target resolves itself by blit-ting into this internal
     * image. (It might or might not have the internal image access, but if it does, we
     * always resolve the render target before accessing this image's data.) RT only.
     */
    @ApiStatus.Internal
    public static final
    int FLAG_MANUAL_MSAA_RESOLVE = ISurface.FLAG_PROTECTED << 5;
    /**
     * This is a Vulkan only flag. It tells us that the internal render target is wrapping a raw
     * Vulkan secondary command buffer. RT only.
     */
    @ApiStatus.Internal
    public static final
    int FLAG_VK_WRAP_SECONDARY_CB = ISurface.FLAG_PROTECTED << 6;

    @Immutable
    public static final class AttachmentDesc {
        /**
         * Null means this entry is unused.
         */
        @Nullable
        public final WeakIdentityKey<@RawPtr Resource> mAttachment;
        /**
         * If < 0, all the layers will be bound, and framebuffer will be layered if
         * it contains multiple layers (2D array, cubemap, cubemap array).
         * <p>
         * Otherwise, it specifies array index + face to use.
         */
        public final int mArraySlice;

        /**
         * Use {@link #UNUSED_ATTACHMENT}
         */
        AttachmentDesc() {
            mAttachment = null;
            mArraySlice = -1;
        }

        public AttachmentDesc(@Nullable @RawPtr Image attachment) {
            this(attachment, -1);
        }

        public AttachmentDesc(@Nullable @RawPtr Image attachment,
                              int arraySlice) {
            if (attachment != null) {
                mAttachment = attachment.getUniqueID();
                mArraySlice = arraySlice;
            } else {
                mAttachment = null;
                mArraySlice = -1;
            }
        }

        public boolean isStale() {
            @RawPtr Resource e;
            return mAttachment != null && ((e = mAttachment.get()) == null || e.isDestroyed());
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(mAttachment);
            result = 31 * result + mArraySlice;
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof AttachmentDesc that) {
                return mAttachment == that.mAttachment &&
                        mArraySlice == that.mArraySlice;
            }
            return false;
        }
    }

    public static final @NonNull AttachmentDesc UNUSED_ATTACHMENT = new AttachmentDesc();


    //// Color Targets

    public static final @NonNull AttachmentDesc @NonNull [] NO_COLOR_ATTACHMENTS = new AttachmentDesc[0];
    /**
     * This matches {@link RenderPassDesc}, if a slot's format is kUnsupported, use {@link #UNUSED_ATTACHMENT}
     * to represent a placeholder, but elements cannot be null.
     */
    public final @NonNull @Size(max = Caps.MAX_COLOR_TARGETS) AttachmentDesc @NonNull [] mColorAttachments;


    //// Color Resolve Target (single RT case)

    public final @NonNull AttachmentDesc mColorResolveAttachment;


    //// Depth-Stencil Target (no resolve)

    public final @NonNull AttachmentDesc mDepthStencilAttachment;


    //// Overall Settings

    /**
     * If there are any attachments, then framebuffer bounds/layers must be <em>exactly</em>
     * the intersection (minimum) of all attachment bounds/layers.
     * <p>
     * If there's no attachments, this can be any value > 0.
     */
    public final int mWidth, mHeight, mLayers;
    //TODO WIP
    public int mFramebufferFlags;

    public FramebufferDesc(int width, int height, int layers,
                           @Nullable AttachmentDesc colorAttachment,
                           @Nullable AttachmentDesc colorResolveAttachment,
                           @Nullable AttachmentDesc depthStencilAttachment) {
        this(width, height, layers,
                colorAttachment != null ? new AttachmentDesc[]{colorAttachment} : null,
                colorResolveAttachment,
                depthStencilAttachment);
    }

    public FramebufferDesc(int width, int height, int layers,
                           @NonNull @Size(max = Caps.MAX_COLOR_TARGETS) AttachmentDesc @Nullable [] colorAttachments,
                           @Nullable AttachmentDesc colorResolveAttachment,
                           @Nullable AttachmentDesc depthStencilAttachment) {
        mWidth = width;
        mHeight = height;
        mLayers = layers;
        mColorAttachments = colorAttachments != null
                ? colorAttachments
                : NO_COLOR_ATTACHMENTS;
        mColorResolveAttachment = colorResolveAttachment != null
                ? colorResolveAttachment
                : UNUSED_ATTACHMENT;
        mDepthStencilAttachment = depthStencilAttachment != null
                ? depthStencilAttachment
                : UNUSED_ATTACHMENT;
        assert mColorAttachments.length <= Caps.MAX_COLOR_TARGETS;
        for (var colorAttachment : mColorAttachments) {
            assert colorAttachment != null;
        }
    }

    /**
     * Should the framebuffer keyed by this be deleted now? Used to delete framebuffers
     * if one of the attachments has already been deleted.
     *
     * @return true to delete, false to keep
     */
    public boolean isStale() {
        for (var colorAttachment : mColorAttachments) {
            if (colorAttachment.isStale()) {
                return true;
            }
        }
        if (mColorResolveAttachment.isStale()) {
            return true;
        }
        return mDepthStencilAttachment.isStale();
    }

    @Override
    public int hashCode() {
        int result = mWidth;
        result = 31 * result + mHeight;
        result = 31 * result + mLayers;
        for (var colorAttachment : mColorAttachments) {
            result = 31 * result + colorAttachment.hashCode();
        }
        result = 31 * result + mColorResolveAttachment.hashCode();
        result = 31 * result + mDepthStencilAttachment.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof FramebufferDesc that) {
            if (mWidth == that.mWidth &&
                    mHeight == that.mHeight &&
                    mLayers == that.mLayers &&
                    mColorAttachments.length == that.mColorAttachments.length &&
                    mDepthStencilAttachment.equals(that.mDepthStencilAttachment) &&
                    mColorResolveAttachment.equals(that.mColorResolveAttachment)) {
                for (int i = 0; i < mColorAttachments.length; i++) {
                    if (!mColorAttachments[i].equals(that.mColorAttachments[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}

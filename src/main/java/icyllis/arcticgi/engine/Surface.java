/*
 * The Arctic Graphics Engine.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arctic is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arctic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arctic. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcticgi.engine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base class that represents something that can be color or depth/stencil
 * attachments of framebuffers. It provides the backing store of 2D images.
 */
public abstract class Surface extends GpuResource {

    protected final int mWidth;
    protected final int mHeight;

    protected Surface(Server server, int width, int height) {
        super(server);
        assert width > 0 && height > 0;
        mWidth = width;
        mHeight = height;
    }

    /**
     * @return the width of this surface
     */
    public final int getWidth() {
        return mWidth;
    }

    /**
     * @return the height of this surface
     */
    public final int getHeight() {
        return mHeight;
    }

    /**
     * Note textures are always single sampled. Multisample textures (MSAA color attachments) are
     * created with render targets, they cannot be used directly (need MSAA resolve).
     *
     * @return the number of samples
     */
    public abstract int getSampleCount();

    /**
     * @return true if this surface has mipmaps
     */
    public abstract boolean isMipmapped();

    /**
     * @return the backend format of this surface
     */
    @Nonnull
    public abstract BackendFormat getBackendFormat();

    /**
     * Compressed surfaces are not recycled and read only.
     *
     * @return true if the surface is created with a compressed format
     */
    public final boolean isFormatCompressed() {
        return getBackendFormat().isCompressed();
    }

    /**
     * The pixel values of this surface cannot be modified (e.g. doesn't support write pixels or
     * mipmap regeneration). To be exact, only wrapped textures, external textures, stencil
     * attachments and MSAA color attachments can be read only.
     *
     * @return true if pixels in this surface are read-only
     */
    public abstract boolean isReadOnly();

    /**
     * @return true if we are working with protected content
     */
    public abstract boolean isProtected();

    @Nullable
    @Override
    protected final ScratchKey computeScratchKey() {
        BackendFormat format = getBackendFormat();
        if (format.isCompressed()) {
            return null;
        }
        return new ScratchKey().compute(
                format,
                mWidth, mHeight,
                getSampleCount(),
                isMipmapped(),
                isProtected());
    }

    /**
     * Storage key of attachments, may be compared with {@link TextureProxy}.
     */
    public static class ScratchKey {

        public int mWidth;
        public int mHeight;
        public int mFormat;
        public int mFlags;

        /**
         * Compute a {@link Surface} key. The usage is limited to the following cases and cannot be mixed.
         * Don't confuse this with {@link icyllis.arcticgi.core.Surface} or
         * {@link icyllis.arcticgi.core.SurfaceCharacterization}, core package surfaces are render targets.
         * <p>
         * <h3>For OpenGL</h3>
         * <ul>
         *     <code>isProtected</code> must be false.
         *     <li><code>sampleCount</code> is 1, it's {@link icyllis.arcticgi.opengl.GLTexture},
         *     used as textures and can be promoted to render targets (managed by {@link Server}).</li>
         *     <li><code>sampleCount</code> is > 1, it's {@link icyllis.arcticgi.opengl.GLRenderbuffer}
         *     and can be stencil attachments or MSAA color attachments of render targets,
         *     <code>mipmapped</code> must be false.</li>
         * </ul>
         * <p>
         * <h3>For Vulkan</h3>
         * <ul>
         *     <li><code>sampleCount</code> is 1, it's {@link icyllis.arcticgi.vulkan.VkImage},
         *     used as textures and can be promoted to render targets (managed by {@link Server}).</li>
         *     <li><code>sampleCount</code> is > 1, it's {@link icyllis.arcticgi.vulkan.VkImage},
         *     and can be stencil attachments or MSAA color attachments of render targets,
         *     <code>mipmapped</code> must be false.</li>
         * </ul>
         * <p>
         * Format can not be compressed. Stencil and MSAA color attachments are distinguished by format.
         *
         * @return the scratch key
         */
        @Nonnull
        public ScratchKey compute(BackendFormat format,
                                  int width, int height,
                                  int sampleCount,
                                  boolean mipmapped,
                                  boolean isProtected) {
            assert (width > 0 && height > 0);
            assert (sampleCount > 0);
            assert (sampleCount == 1 || !mipmapped);
            mWidth = width;
            mHeight = height;
            mFormat = format.getFormatKey();
            mFlags = (mipmapped ? 1 : 0) | (isProtected ? 2 : 0) | (sampleCount << 2);
            return this;
        }

        /**
         * Keep {@link TextureProxy#hashCode()} sync with this.
         */
        @Override
        public int hashCode() {
            int result = mWidth;
            result = 31 * result + mHeight;
            result = 31 * result + mFormat;
            result = 31 * result + mFlags;
            return result;
        }

        /**
         * Keep {@link TextureProxy#equals(Object)}} sync with this.
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScratchKey key = (ScratchKey) o;
            if (mWidth != key.mWidth) return false;
            if (mHeight != key.mHeight) return false;
            if (mFormat != key.mFormat) return false;
            return mFlags == key.mFlags;
        }
    }
}

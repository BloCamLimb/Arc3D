/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.sketch;

import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Surface is responsible for managing the pixels that a canvas draws into.
 * The pixels will be allocated on the GPU (a RenderTarget surface).
 * Surface takes care of allocating a {@link Canvas} that will draw into the surface.
 * Call {@link #getCanvas()} to use that canvas (it is owned by the surface).
 * Surface always has non-zero dimensions. If there is a request for a new surface,
 * and either of the requested dimensions are zero, then null will be returned.
 */
public abstract class Surface extends RefCnt {

    private final int mWidth;
    private final int mHeight;
    private Object mGenerationID;

    // unique ptr
    private Canvas mCachedCanvas;
    @SharedPtr
    private Image mCachedImage;

    protected Surface(int width, int height) {
        assert width > 0 && height > 0;
        mWidth = width;
        mHeight = height;
    }

    @Override
    protected void deallocate() {
        if (mCachedCanvas != null) {
            mCachedCanvas.mSurface = null;
            mCachedCanvas.close();
            mCachedCanvas = null;
        }
        mCachedImage = RefCnt.move(mCachedImage);
    }

    /**
     * Returns pixel count in each row; may be zero or greater.
     *
     * @return number of pixel columns
     */
    public final int getWidth() {
        return mWidth;
    }

    /**
     * Returns pixel row count; may be zero or greater.
     *
     * @return number of pixel rows
     */
    public final int getHeight() {
        return mHeight;
    }

    /**
     * Returns an ImageInfo describing the Surface.
     */
    @NonNull
    public abstract ImageInfo getImageInfo();

    /**
     * Returns unique value identifying the content of Surface. Returned value changes
     * each time the content changes. Content is changed by drawing, or by calling
     * {@link #notifyWillChange()}.
     *
     * @return unique content identifier
     */
    public final Object getGenerationID() {
        if (mGenerationID == null) {
            assert mCachedCanvas == null || mCachedCanvas.mSurface == this;
            mGenerationID = new Object();
        }
        return mGenerationID;
    }

    protected static final int
            kPreserve_ContentChangeMode = 0,    // preserves surface on change
            kDiscard_ContentChangeMode = 1;     // discards surface on change

    /**
     * Notifies that Surface contents will be changed externally.
     * Subsequent calls to {@link #getGenerationID()} return a different value.
     */
    public final void notifyWillChange() {
        aboutToDraw(kDiscard_ContentChangeMode);
    }

    /**
     * Returns the GPU context being used by the Surface.
     *
     * @return the GPU context, if available; null otherwise
     */
    @RawPtr
    @Nullable
    public Object getCommandContext() {
        return null;
    }

    /**
     * Returns Canvas that draws into Surface. Subsequent calls return the same Canvas.
     * Canvas returned is managed and owned by Surface, and is deleted when Surface
     * is deleted.
     *
     * @return drawing Canvas for Surface
     */
    @RawPtr
    public final Canvas getCanvas() {
        return getCachedCanvas();
    }

    /**
     * Returns Image capturing Surface contents. Subsequent drawing to Surface contents
     * are not captured. Image allocation is accounted for if Surface was created with
     * Budgeted flag.
     *
     * @return Image initialized with Surface contents
     */
    @Nullable
    @SharedPtr
    public final Image makeImageSnapshot() {
        return RefCnt.create(getCachedImage());
    }

    /**
     * Like the no-parameter version, this returns an image of the current surface contents.
     * This variant takes a rectangle specifying the subset of the surface that is of interest.
     * These bounds will be sanitized before being used.
     * <p>
     * - If bounds extends beyond the surface, it will be trimmed to just the intersection of
     * it and the surface.<br>
     * - If bounds does not intersect the surface, then this returns nullptr.<br>
     * - If bounds == the surface, then this is the same as calling the no-parameter variant.
     */
    @Nullable
    @SharedPtr
    public final Image makeImageSnapshot(@NonNull Rect2ic subset) {
        var bounds = new Rect2i(subset);
        if (!bounds.intersect(0, 0, mWidth, mHeight)) {
            return null;
        }
        assert !bounds.isEmpty();
        if (bounds.left() == 0 && bounds.top() == 0 && bounds.right() == mWidth && bounds.bottom() == mHeight) {
            return makeImageSnapshot();
        } else {
            return onNewImageSnapshot(bounds);
        }
    }

    @ApiStatus.Internal
    @RawPtr
    public final Canvas getCachedCanvas() {
        if (mCachedCanvas == null) {
            mCachedCanvas = onNewCanvas();
            if (mCachedCanvas != null) {
                mCachedCanvas.mSurface = this;
            }
        }
        return mCachedCanvas;
    }

    @ApiStatus.Internal
    @RawPtr
    public final Image getCachedImage() {
        if (mCachedImage != null) {
            return mCachedImage;
        }

        mCachedImage = onNewImageSnapshot(null);

        assert mCachedCanvas == null || mCachedCanvas.mSurface == this;
        return mCachedImage;
    }

    @ApiStatus.Internal
    public final boolean hasCachedImage() {
        return mCachedImage != null;
    }

    /**
     * Allocate a canvas that will draw into this surface. We will cache this
     * canvas, to return the same object to the caller multiple times. We
     * take ownership, and will call unref() on the canvas when we go out of
     * scope.
     */
    @ApiStatus.Internal
    @RawPtr
    protected abstract Canvas onNewCanvas();

    /**
     * Allocate an Image that represents the current contents of the surface.
     * This needs to be able to outlive the surface itself (if need be), and
     * must faithfully represent the current contents, even if the surface
     * is changed after this called (e.g. it is drawn to via its canvas).
     * <p>
     * If a subset is specified, the impl must make a copy, rather than try to wait
     * on copy-on-write.
     */
    @ApiStatus.Internal
    @Nullable
    @SharedPtr
    protected abstract Image onNewImageSnapshot(@Nullable Rect2ic subset);

    /**
     * Called as a performance hint when the Surface is allowed to make its contents
     * undefined.
     */
    @ApiStatus.Internal
    protected void onDiscard() {
    }

    /**
     * If the surface is about to change, we call this so that our subclass
     * can optionally fork their backend (copy-on-write) in case it was
     * being shared with the cachedImage.
     * <p>
     * Returns false if the backing cannot be un-shared.
     */
    @ApiStatus.Internal
    protected abstract boolean onCopyOnWrite(int changeMode);

    /**
     * Signal the surface to remind its backing store that it's mutable again.
     * Called only when we _didn't_ copy-on-write; we assume the copies start mutable.
     */
    @ApiStatus.Internal
    protected void onRestoreBackingMutability() {
    }

    // Returns true if there is an outstanding image-snapshot, indicating that a call to aboutToDraw
    // would trigger a copy-on-write.
    final boolean hasOutstandingImageSnapshot() {
        return mCachedImage != null && !mCachedImage.unique();
    }

    // Returns false if drawing should not take place (allocation failure).
    final boolean aboutToDraw(int changeMode) {
        mGenerationID = null;

        assert mCachedCanvas == null || mCachedCanvas.mSurface == this;

        if (mCachedImage != null) {
            // the surface may need to fork its backend, if it's sharing it with
            // the cached image. Note: we only call if there is an outstanding owner
            // on the image (besides us).
            boolean unique = mCachedImage.unique();
            if (!unique) {
                if (!onCopyOnWrite(changeMode)) {
                    return false;
                }
            }

            // regardless of copy-on-write, we must drop our cached image now, so
            // that the next request will get our new contents.
            mCachedImage = RefCnt.move(mCachedImage);

            if (unique) {
                // Our content isn't held by any image now, so we can consider that content mutable.
                // Raster surfaces need to be told it's safe to consider its pixels mutable again.
                // We make this call after the ->unref() so the subclass can assert there are no images.
                onRestoreBackingMutability();
            }
        } else if (changeMode == kDiscard_ContentChangeMode) {
            onDiscard();
        }
        return true;
    }
}

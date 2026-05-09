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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.BackendImage;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.sketch.Canvas;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Surface;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The surface that is backed by GPU and Granite Renderer.
 * <p>
 * This object, and coupled {@link GraniteDevice}, {@link Canvas}, should be
 * only used on the {@link RecordingContext#getOwnerThread()}. Only their
 * internal GPU resources can be used in other threads independently of these
 * objects.
 */
public final class GraniteSurface extends Surface {

    @SharedPtr
    private GraniteDevice mDevice;

    @VisibleForTesting
    public GraniteSurface(@SharedPtr GraniteDevice device) {
        super(device.getWidth(), device.getHeight());
        mDevice = device;
    }

    @Nullable
    @SharedPtr
    static GraniteSurface make(RecordingContext context,
                               ImageInfo info,
                               boolean budgeted,
                               boolean mipmapped,
                               boolean exact,
                               int surfaceOrigin,
                               byte initialLoadOp,
                               String label,
                               boolean trackDevice) {
        @SharedPtr
        GraniteDevice device = GraniteDevice.make(
                context, info, budgeted, mipmapped, exact, surfaceOrigin,
                initialLoadOp, label, trackDevice
        );
        if (device == null) {
            return null;
        }
        // A non-budgeted surface should be fully instantiated before we return it
        // to the client.
        assert (budgeted || device.getReadView().getProxy().isInstantiated());
        return new GraniteSurface(device); // move
    }

    @Nullable
    @SharedPtr
    public static GraniteSurface make(RecordingContext rc,
                                      ImageInfo info,
                                      boolean budgeted,
                                      boolean mipmapped,
                                      boolean exact,
                                      int surfaceOrigin,
                                      String label) {
        return make(rc, info, budgeted, mipmapped, exact,
                surfaceOrigin, Engine.LoadOp.kClear, label, true);
    }

    /**
     * Returns Surface on GPU indicated by context. Allocates memory for pixels,
     * based on the width, height, and ColorType in ColorInfo. <code>imageInfo</code>
     * describes the pixel format in ColorType, and transparency in AlphaType.
     * <p>
     * While clients hold a ref on a Surface, the backing gpu object does <em>not</em>
     * count against the budget. Once a Surface is freed, the backing gpu object may or may
     * not become a scratch (i.e., reusable) resource but, if it does, it will be counted against
     * the budget.
     * <p>
     * When MSAA is required, the implementation uses dynamic/discardable MSAA.
     * There's no way to request explicit MSAA for this method.
     * <p>
     * <code>origin</code> pins either the top-left or the bottom-left corner to the origin.
     * Note that bottom-left origin is deprecated, shaders that require device space coordinates
     * will not be working properly.
     * <p>
     * <code>mipmapped</code> hints that Image returned by makeImageSnapshot() has mipmaps.
     *
     * @param context       GPU context
     * @param info          width, height, ColorType, AlphaType; width, or height, or both, may be zero
     * @param mipmapped     hint that Surface will host mipmap images
     * @param surfaceOrigin see {@link Engine.SurfaceOrigin}
     * @param label         the GPU object label; null to use a default label, empty to clear label
     * @return Surface if all parameters are valid; otherwise, null
     */
    @Nullable
    @SharedPtr
    public static GraniteSurface makeRenderTarget(RecordingContext context,
                                                  @NonNull ImageInfo info,
                                                  boolean mipmapped,
                                                  int surfaceOrigin,
                                                  @Nullable String label) {
        if (label == null) {
            label = "SurfaceRenderTarget";
        }
        // create non-budgeted, exact-fit device
        return make(context, info, false, mipmapped, true,
                surfaceOrigin, label);
    }

    @Nullable
    @SharedPtr
    public static GraniteSurface wrapBackendImage(RecordingContext context,
                                                  BackendImage backendImage,
                                                  @NonNull ImageInfo info,
                                                  int surfaceOrigin,
                                                  @Nullable Runnable releaseFn,
                                                  @Nullable String label) {
        try {
            if (context == null) {
                return null;
            }

            if (backendImage == null || !info.isValid()) {
                return null;
            }
            if (info.width() != backendImage.getWidth() ||
                    info.height() != backendImage.getHeight()) {
                return null;
            }

            if (!context.getCaps().isRenderableFormat(info.colorType(),
                    backendImage.getImageFormat(), backendImage.getSampleCount(), false)) {
                return null;
            }

            if (label == null) {
                label = "SurfaceWrappedImage";
            }

            @SharedPtr
            icyllis.arc3d.engine.Image image = context.getResourceProvider().wrapBackendImage(backendImage, label);
            if (image == null) {
                return null;
            }

            image.setReleaseCallback(releaseFn);
            releaseFn = null;

            @SharedPtr
            ImageProxy proxy = ImageProxy.wrap(image); // move

            short readSwizzle = context.getCaps().getReadSwizzle(
                    info.colorType(), image.getDesc());

            @SharedPtr
            ImageProxyView view = new ImageProxyView(proxy, surfaceOrigin, readSwizzle); // move

            GraniteDevice device = GraniteDevice.make(
                    context, view, // move
                    info, Engine.LoadOp.kLoad,
                    true
            );
            if (device == null) {
                return null;
            }
            return new GraniteSurface(device);
        } finally {
            if (releaseFn != null) {
                releaseFn.run();
            }
        }
    }

    @Override
    protected void deallocate() {
        super.deallocate();
        // Mark the device immutable when the Surface is destroyed to flush any pending work to the
        // recorder and to flag the device so that any linked image views can detach from the Device
        // when they are next drawn.
        mDevice.setImmutable();
        mDevice = RefCnt.move(mDevice);
    }

    public void flush() {
        mDevice.flushPendingWork();
    }

    @NonNull
    @Override
    public ImageInfo getImageInfo() {
        return mDevice.getImageInfo();
    }

    @Override
    protected Canvas onNewCanvas() {
        return new Canvas(RefCnt.create(mDevice));
    }

    @Nullable
    @Override
    protected Image onNewImageSnapshot(@Nullable Rect2ic subset) {
        return makeImageCopy(subset, mDevice.getReadView().isMipmapped());
    }

    @Override
    protected boolean onCopyOnWrite(int changeMode) {
        // onNewImageSnapshot() always copy, no-op here
        return true;
    }

    @Nullable
    @SharedPtr
    public GraniteImage makeImageCopy(@Nullable Rect2ic subset, boolean mipmapped) {
        assert !hasCachedImage();
        if (subset == null) {
            subset = new Rect2i(0, 0, getWidth(), getHeight());
        }
        return mDevice.makeImageCopy(subset, false, mipmapped, false);
    }

    @RawPtr
    @NonNull
    @Override
    public RecordingContext getCommandContext() {
        return mDevice.getCommandContext();
    }

    /**
     * Raw ptr to render target proxy to underlying image.
     * Can be null when wrapping OpenGL default FBO.
     */
    @RawPtr
    public ImageProxy getBackingTarget() {
        return mDevice.getReadView().getProxy();
    }

    /**
     * Raw ptr to read view.
     * Even if the target is not texturable, this returns non-null.
     */
    @RawPtr
    public ImageProxyView getReadSurfaceView() {
        return mDevice.getReadView();
    }

    //<code>sampleCount</code> requests the number of samples per pixel.
    //     * Pass one to disable multi-sample anti-aliasing.  The request is rounded
    //     * up to the next supported count, or rounded down if it is larger than the
    //     * maximum supported count.

    // DEPRECATED code below

    /*
     * Wraps a GPU-backed texture into Surface. Caller must ensure the texture is
     * valid for the lifetime of returned Surface. If <code>sampleCount</code> greater
     * than one, creates an intermediate MSAA Surface which is used for drawing
     * <code>backendTexture</code>.
     * <p>
     * Surface is returned if all parameters are valid. <code>backendTexture</code>
     * is valid if its pixel configuration agrees with <code>context</code>; for instance,
     * if <code>backendTexture</code> has an sRGB configuration, then <code>context</code>
     * must support sRGB. Further, <code>backendTexture</code> width and height must
     * not exceed <code>context</code> capabilities, and the <code>context</code> must
     * be able to support back-end textures.
     * <p>
     * Upon success <code>releaseCallback</code> is called when it is safe to delete the
     * texture in the backend API (accounting only for use of the texture by this surface).
     * If Surface creation fails <code>releaseCallback</code> is called before this method
     * returns.
     *
     * @param context         GPU context
     * @param backendImage    texture residing on GPU
     * @param sampleCount     samples per pixel, or 1 to disable full scene anti-aliasing
     * @param releaseCallback function called when texture can be released, may be null
     * @return Surface if all parameters are valid; otherwise, null
     */
    /*@Nullable
    public static Surface makeFromBackendTexture(RecordingContext context,
                                                 BackendImage backendImage,
                                                 int origin, int sampleCount,
                                                 int colorType,
                                                 Runnable releaseCallback) {
        if (context == null || sampleCount < 1 || colorType == ColorInfo.CT_UNKNOWN) {
            if (releaseCallback != null) {
                releaseCallback.run();
            }
            return null;
        }

        if (!validateBackendTexture(context.getCaps(), backendImage, sampleCount, colorType, true)) {
            if (releaseCallback != null) {
                releaseCallback.run();
            }
            return null;
        }

        return null;
    }*/

    /*@Nullable
    public static Surface wrapBackendRenderTarget(RecordingContext rContext,
                                                  BackendRenderTarget backendRenderTarget,
                                                  int origin,
                                                  int colorType,
                                                  ColorSpace colorSpace) {
        if (colorType == ColorInfo.CT_UNKNOWN) {
            return null;
        }
        var provider = rContext.getSurfaceProvider();
        var rtProxy = provider.wrapBackendRenderTarget(backendRenderTarget, null);
        if (rtProxy == null) {
            return null;
        }
        var dev = SurfaceDevice.make(rContext,
                colorType,
                colorSpace,
                rtProxy,
                origin,
                false);
        if (dev == null) {
            return null;
        }
        return new Surface(dev);
    }*/

    /*private static boolean validateBackendTexture(Caps caps,
                                                  BackendImage backendImage,
                                                  int sampleCount,
                                                  int colorType,
                                                  boolean texturable) {
        if (backendImage == null) {
            return false;
        }

        BackendFormat backendFormat = backendImage.getImageFormat();

        if (!caps.isFormatCompatible(colorType, backendFormat)) {
            return false;
        }

        if (caps.isRenderableFormat(colorType, backendFormat, sampleCount, )) {
            return false;
        }

        //return !texturable || caps.isFormatTexturable(backendFormat);
        return true;
    }*/
}

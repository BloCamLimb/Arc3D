/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.granite.task.CopyImageTask;
import icyllis.arc3d.sketch.Image;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The image that is backed by GPU.
 */
public final class GraniteImage extends Image {

    @RawPtr
    RecordingContext mContext;
    @SharedPtr
    ImageProxyView mImageProxyView;

    public GraniteImage(@NonNull @RawPtr RecordingContext context,
                        @NonNull @SharedPtr ImageProxyView view,
                        int colorType, int alphaType,
                        @Nullable ColorSpace colorSpace) {
        super(ImageInfo.make(view.getWidth(), view.getHeight(),
                colorType, alphaType, colorSpace));
        mContext = context;
        mImageProxyView = view;
    }

    @Nullable
    @SharedPtr
    public static GraniteImage copy(@RawPtr RecordingContext rc,
                                    @RawPtr ImageProxyView srcView,
                                    @NonNull ImageInfo srcInfo,
                                    @NonNull Rect2ic subset,
                                    boolean budgeted,
                                    boolean mipmapped,
                                    boolean approxFit,
                                    @Nullable String label) {
        assert !(mipmapped && approxFit);
        if (srcView == null) {
            return null;
        }

        assert (new Rect2i(0, 0, srcInfo.width(), srcInfo.height()).contains(subset));

        int width = subset.width();
        int height = subset.height();
        if (approxFit) {
            width = ISurface.getApproxSize(width);
            height = ISurface.getApproxSize(height);
        }
        var dstDesc = rc.getCaps().getImageDescForSampledCopy(
                srcView.getProxy().getDesc(), width, height, 1,
                mipmapped ? ISurface.FLAG_MIPMAPPED : 0
        );

        @SharedPtr
        ImageProxy dst = ImageProxy.make(
                rc, dstDesc,
                budgeted, label
        );
        if (dst == null) {
            return null;
        }

        @SharedPtr
        CopyImageTask copyTask = CopyImageTask.make(
                srcView.refProxy(),
                subset,
                RefCnt.create(dst),
                0, 0, 0
        );
        if (copyTask == null) {
            RefCnt.move(dst);
            return null;
        }

        rc.addTask(copyTask); // move

        return new GraniteImage(rc, new ImageProxyView(dst, srcView.getOrigin(), srcView.getSwizzle()), // move
                srcInfo.colorType(), srcInfo.alphaType(), srcInfo.colorSpace());
    }

    @Override
    protected void deallocate() {
        mImageProxyView.close();
        mImageProxyView = null;
    }

    @Override
    public RecordingContext getCommandContext() {
        return mContext;
    }

    @RawPtr
    public ImageProxyView getImageProxyView() {
        return mImageProxyView;
    }

    @Override
    public boolean isTextureBacked() {
        return true;
    }

    @Override
    public long getTextureSize() {
        return mImageProxyView.getProxy().getMemorySize();
    }

    @Override
    public String toString() {
        return "GraniteImage{" +
                "mContext=" + mContext +
                ", mImageProxyView=" + mImageProxyView +
                '}';
    }
}

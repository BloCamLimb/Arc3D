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

package icyllis.arc3d.sketch;

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.PixelUtils;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.sketch.j2d.RasterDraw;
import org.jspecify.annotations.NonNull;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * The ScalerContext controls the rasterization with a specified typeface
 * and rasterization options.
 * <p>
 * This class is not thread safe, even it is stateless.
 */
public abstract class ScalerContext {

    protected final StrikeDesc mDesc;

    private final Typeface mTypeface;

    // if this is set, we draw the image from a path, rather than
    // calling generateImage.
    private final boolean mGenerateImageFromPath;

    public ScalerContext(Typeface typeface,
                         StrikeDesc desc) {
        var mutableDesc = new StrikeDesc.Lookup(desc);
        // Allow the typeface to adjust the rec.
        typeface.onFilterStrikeDesc(mutableDesc);
        mDesc = mutableDesc.immutable();

        mTypeface = typeface;
        mGenerateImageFromPath = mDesc.getFrameWidth() >= 0;
    }

    public final Typeface getTypeface() {
        return mTypeface;
    }

    // Mask::Format
    public final byte getMaskFormat() {
        return mDesc.getMaskFormat();
    }

    public final boolean isSubpixel() {
        return (mDesc.mFlags & StrikeDesc.kSubpixelPositioning_Flag) != 0;
    }

    public final boolean isLinearMetrics() {
        return (mDesc.mFlags & StrikeDesc.kLinearMetrics_Flag) != 0;
    }

    private static void saturate_glyph_bounds(
            Glyph glyph, float left, float top, float right, float bottom) {
        // round out to get grid-fitted bounds
        int l = (int) Math.floor(left);
        int t = (int) Math.floor(top);
        int r = (int) Math.ceil(right);
        int b = (int) Math.ceil(bottom);
        // saturate cast
        glyph.mLeft = (short) MathUtil.clamp(l, Short.MIN_VALUE, Short.MAX_VALUE);
        glyph.mTop = (short) MathUtil.clamp(t, Short.MIN_VALUE, Short.MAX_VALUE);
        glyph.mWidth = (short) MathUtil.clamp(r - l, 0, 0xFFFF);
        glyph.mHeight = (short) MathUtil.clamp(b - t, 0, 0xFFFF);
    }

    @NonNull
    public final Glyph makeGlyph(int packedID) {
        Glyph glyph = new Glyph(packedID);
        // subclass may return a different value
        glyph.mMaskFormat = getMaskFormat();
        GlyphMetrics metrics = generateMetrics(glyph);
        assert !metrics.mNeverRequestPath || !metrics.mComputeFromPath;

        glyph.mMaskFormat = metrics.mMaskFormat;

        if (metrics.mComputeFromPath || (mGenerateImageFromPath && !metrics.mNeverRequestPath)) {
            internalGetPath(glyph);
            Path devPath = glyph.getPath();
            if (devPath != null) {
                // other formats can not be produced from paths.
                if (glyph.mMaskFormat != Mask.kBW_Format &&
                        glyph.mMaskFormat != Mask.kA8_Format) {
                    glyph.mMaskFormat = Mask.kA8_Format;
                }

                var bounds = new Rect2f();
                devPath.getBounds(bounds);
                if (!bounds.isEmpty() && glyph.getPathIsHairline()) {
                    bounds.outset(1, 1);
                }
                saturate_glyph_bounds(glyph,
                        bounds.left(), bounds.top(),
                        bounds.right(), bounds.bottom());
            }
        } else {
            saturate_glyph_bounds(glyph,
                    metrics.mLeft, metrics.mTop,
                    metrics.mRight, metrics.mBottom);
            if (metrics.mNeverRequestPath) {
                glyph.setPath((Path) null, false);
            }
        }

        // if either dimension is empty, zap the image bounds of the glyph
        if (glyph.mWidth == 0 || glyph.mHeight == 0) {
            glyph.mLeft = glyph.mTop = glyph.mWidth = glyph.mHeight = 0;
        }

        return glyph;
    }

    public final void getImage(Glyph glyph) {
        if (!mGenerateImageFromPath) {
            generateImage(glyph, glyph.getImageBase(), glyph.getImageAddress());
        } else {
            assert glyph.setPathHasBeenCalled();
            Path devPath = glyph.getPath();
            if (devPath == null) {
                generateImage(glyph, glyph.getImageBase(), glyph.getImageAddress());
            } else {
                assert glyph.getMaskFormat() != Mask.kARGB32_Format;
                generateImageFromPath(glyph, devPath);
            }
        }
    }

    private static void generateImageFromPath(
            Glyph glyph, Path path) {
        assert glyph.getMaskFormat() == Mask.kBW_Format ||
                glyph.getMaskFormat() == Mask.kA8_Format;

        Matrix matrix = Matrix.makeTranslate(-glyph.getLeft(), -glyph.getTop());

        Paint paint = new Paint();
        if (glyph.getPathIsHairline()) {
            paint.setStroke(true);
            paint.setStrokeWidth(0);
        } else {
            paint.setStyle(Paint.FILL);
        }
        paint.setAntiAlias(glyph.getMaskFormat() != Mask.kBW_Format);
        paint.setBlendMode(BlendMode.SRC);

        BufferedImage bufferedImage = new BufferedImage(glyph.getWidth(), glyph.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);

        var g2d = bufferedImage.createGraphics();

        RasterDraw.drawPath(g2d, matrix, path, paint);

        var data = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

        switch (glyph.getMaskFormat()) {
            case Mask.kBW_Format -> {
                PixelUtils.packA8ToBW(
                        data,
                        0,
                        glyph.getWidth(),
                        glyph.getImageBase(),
                        glyph.getImageAddress(),
                        glyph.getRowBytes(),
                        glyph.getWidth(),
                        glyph.getHeight()
                );
            }
            case Mask.kA8_Format -> {
                PixelUtils.copyImage(
                        data,
                        0,
                        glyph.getWidth(),
                        glyph.getImageBase(),
                        glyph.getImageAddress(),
                        glyph.getRowBytes(),
                        glyph.getRowBytes(),
                        glyph.getHeight()
                );
            }
        }

        paint.close();
    }

    public final void getPath(@NonNull Glyph glyph) {
        internalGetPath(glyph);
    }

    private void internalGetPath(@NonNull Glyph glyph) {
        if (glyph.setPathHasBeenCalled()) {
            return;
        }

        PathBuilder builder = new PathBuilder();
        if (!generatePath(glyph, builder)) {
            glyph.setPath((Path) null, false);
            return;
        }

        if (isSubpixel()) {
            float subX = glyph.getSubX();
            if (subX != 0) {
                builder.offset(subX, 0);
            }
        }

        if (mDesc.getFrameWidth() <= 0 && mDesc.getPathEffect() == null) {
            // fill or hairline, but hairline+fill == fill
            boolean hairline = mDesc.getFrameWidth() == 0 &&
                    (mDesc.mFlags & StrikeDesc.kFrameAndFill_Flag) == 0;
            glyph.setPath(builder.build(), hairline);
            return;
        }

        // need the path in user-space, with only the point-size applied
        // so that our stroking and effects will operate the same way they
        // would if the user had extracted the path themself, and then
        // called drawPath
        Matrix localToDevice = new Matrix();
        Matrix deviceToLocal = new Matrix();

        mDesc.getDeviceMatrix(localToDevice);
        if (!localToDevice.invert(deviceToLocal)) {
            glyph.setPath(new Path(), false);
            return;
        }

        builder.transform(deviceToLocal);
        // now builder holds local path that is only affected by the paint settings,
        // and not the canvas matrix

        StrokeRec strokeRec = new StrokeRec();

        if (mDesc.getFrameWidth() >= 0) {
            strokeRec.setStrokeStyle(mDesc.getFrameWidth(),
                    (mDesc.mFlags & StrikeDesc.kFrameAndFill_Flag) != 0);
            // glyphs are always closed contours, so cap type is ignored,
            // so we just pass something.
            //TODO pass user-supplied cap, custom font may have open contours
            strokeRec.setStrokeParams(Paint.CAP_BUTT,
                    mDesc.getStrokeJoin(),
                    Paint.ALIGN_CENTER,
                    mDesc.getMiterLimit());

            // added by Arc3D, this is missing in Skia
            strokeRec.setResScale(localToDevice.getMaxScale());
        }

        if (mDesc.getPathEffect() != null) {
            //TODO
        }

        if (strokeRec.isStrokeStyle()) {
            // wide strokes
            Path localPath = builder.build();
            builder.reset();
            boolean res = strokeRec.applyToPath(localPath, builder);
            assert res;
        }

        // transform into device space
        builder.transform(localToDevice);
        glyph.setPath(builder.build(), strokeRec.isHairlineStyle());
    }

    public static class GlyphMetrics {
        // CBox (box of all control points, approximate) or
        // BBox (exact bonding box, compute extrema) of the glyph
        public float mLeft;
        public float mTop;
        public float mRight;
        public float mBottom;

        public byte mMaskFormat;

        public boolean mNeverRequestPath;
        public boolean mComputeFromPath;

        public GlyphMetrics(byte maskFormat) {
            mMaskFormat = maskFormat;
        }
    }

    protected abstract GlyphMetrics generateMetrics(Glyph glyph);

    /**
     * Generates the contents of glyph.fImage.
     * When called, glyph.fImage will be pointing to a pre-allocated,
     * uninitialized region of memory of size glyph.imageSize().
     * This method may not change glyph.fMaskFormat.
     * <p>
     * Because glyph.imageSize() will determine the size of fImage,
     * generateMetrics will be called before generateImage.
     */
    protected abstract void generateImage(Glyph glyph, Object imageBase, long imageAddress);

    /**
     * Appends the glyph's outline to the path builder. The builder
     * is in its <em>initial state</em> when this method is called.
     * <p>
     * A glyph can have an empty outline, but if the glyph cannot be
     * represented as a path (like color emoji), returns false instead.
     * <p>
     * Subclass must NOT apply subpixel positioning to the path.
     *
     * @return false if this glyph does not have any path.
     */
    protected abstract boolean generatePath(@NonNull Glyph glyph, @NonNull PathBuilder dst);
}

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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImageDesc;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.Key;
import icyllis.arc3d.engine.KeyBuilder;
import icyllis.arc3d.granite.geom.ArcShape;
import icyllis.arc3d.granite.geom.BoundsManager;
import icyllis.arc3d.granite.geom.BoxShape;
import icyllis.arc3d.granite.geom.EdgeAAQuad;
import icyllis.arc3d.granite.geom.HybridBoundsManager;
import icyllis.arc3d.granite.geom.Rect;
import icyllis.arc3d.granite.geom.SubRunData;
import icyllis.arc3d.granite.task.DrawTask;
import icyllis.arc3d.granite.task.RenderPassTask;
import icyllis.arc3d.sketch.*;
import icyllis.arc3d.sketch.shaders.ImageShader;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The device that is backed by GPU.
 */
public final class GraniteDevice extends Device {

    @RawPtr
    private RecordingContext mContext;
    @SharedPtr
    private SurfaceDrawContext mDrawContext;

    private final ClipStack mClipStack;
    private final ObjectArrayList<ClipStack.Element> mElementsForMask = new ObjectArrayList<>();

    // The max depth value sent to the DrawContext, incremented so each draw has a unique value.
    private int mCurrentDepth = Draw.CLEAR_DEPTH;

    // Tracks accumulated intersections for ordering dependent use of the color and depth attachment
    // (i.e. depth-based clipping, and transparent blending)
    private final BoundsManager mColorDepthBoundsManager;

    private final TextBlobCache.FeatureKey mBlobKey = new TextBlobCache.FeatureKey();

    @RawPtr
    private final RendererProvider mRendererProvider;
    @RawPtr
    private final PaintParams mPaintParams = new PaintParams();

    /**
     * Gatherer of {@link DrawPass#GEOMETRY_UNIFORM_BLOCK_NAME} and {@link DrawPass#FRAGMENT_UNIFORM_BLOCK_NAME}.
     * But we will not gather both at the same time.
     */
    @SharedPtr
    private UniformDataGatherer mUniformDataGatherer;
    @RawPtr
    private KeyContext mKeyContext;

    private GraniteDevice(RecordingContext context, SurfaceDrawContext drawContext) {
        super(drawContext.getImageInfo());
        mContext = context;
        mDrawContext = drawContext;
        mClipStack = new ClipStack(this);
        // These default tuning numbers for the HybridBoundsManager were chosen from looking at performance
        // and accuracy curves produced by the BoundsManagerBench for random draw bounding boxes. This
        // config will use brute force for the first 64 draw calls to the Device and then switch to a grid
        // that is dynamically sized to produce cells that are 16x16, up to a grid that's 32x32 cells.
        // This seemed like a sweet spot balancing accuracy for low-draw count surfaces and overhead for
        // high-draw count and high-resolution surfaces. With the 32x32 grid limit, cell size will increase
        // above 16px when the surface dimension goes above 512px.
        // TODO: These could be exposed as context options or surface options, and we may want to have
        // different strategies in place for a base device vs. a layer's device.
        mColorDepthBoundsManager = new HybridBoundsManager(
                getWidth(), getHeight(),
                16, 64, 32
        );
        //mColorDepthBoundsManager = new SimpleBoundsManager();
        mRendererProvider = context.getSharedObject(GraniteUtil.RENDERER_PROVIDER);

        // layout doesn't matter, our struct definition yields same layout in std140 and std430
        mUniformDataGatherer = new UniformDataGatherer(UniformDataGatherer.Std140Layout);

        mKeyContext = new KeyContext(context, drawContext, new KeyBuilder(),
                mUniformDataGatherer, drawContext.getTextureDataGatherer(), drawContext.getImageInfo());
    }

    @Nullable
    @SharedPtr
    public static GraniteDevice make(@RawPtr RecordingContext rc,
                                     @NonNull ImageInfo deviceInfo,
                                     int surfaceFlags,
                                     int origin,
                                     byte initialLoadOp,
                                     String label,
                                     boolean trackDevice) {
        if (rc == null) {
            return null;
        }

        if ((surfaceFlags & ISurface.FLAG_MIPMAPPED) != 0 &&
                (surfaceFlags & ISurface.FLAG_APPROX_FIT) != 0) {
            // mipmapping requires full size
            return null;
        }

        int backingWidth = deviceInfo.width();
        int backingHeight = deviceInfo.height();
        if ((surfaceFlags & ISurface.FLAG_APPROX_FIT) != 0) {
            backingWidth = ISurface.getApproxSize(backingWidth);
            backingHeight = ISurface.getApproxSize(backingHeight);
        }

        ImageDesc desc = rc.getCaps().getDefaultColorImageDesc(
                Engine.ImageType.k2D,
                deviceInfo.colorType(),
                backingWidth,
                backingHeight,
                1,
                surfaceFlags | ISurface.FLAG_SAMPLED_IMAGE | ISurface.FLAG_RENDERABLE);
        if (desc == null) {
            return null;
        }
        short readSwizzle = rc.getCaps().getReadSwizzle(
                desc, deviceInfo.colorType());
        @SharedPtr
        ImageProxy target = ImageProxy.make(rc, desc,
                (surfaceFlags & ISurface.FLAG_BUDGETED) != 0, label);
        if (target == null) {
            return null;
        }

        return make(rc, new ImageProxyView(target, // move
                        origin, readSwizzle), // move
                deviceInfo, initialLoadOp, trackDevice);
    }

    @Nullable
    @SharedPtr
    public static GraniteDevice make(@RawPtr RecordingContext rc,
                                     @SharedPtr ImageProxyView targetView,
                                     ImageInfo deviceInfo,
                                     byte initialLoadOp,
                                     boolean trackDevice) {
        if (rc == null) {
            return null;
        }
        SurfaceDrawContext drawContext = SurfaceDrawContext.make(rc,
                targetView, // move
                deviceInfo);
        if (drawContext == null) {
            return null;
        }
        if (initialLoadOp == Engine.LoadOp.kClear) {
            drawContext.clear(null);
        } else if (initialLoadOp == Engine.LoadOp.kDiscard) {
            drawContext.discard();
        }

        @SharedPtr
        GraniteDevice device = new GraniteDevice(rc, drawContext);
        if (trackDevice) {
            rc.trackDevice(RefCnt.create(device));
        }
        return device;
    }

    @Override
    protected void deallocate() {
        super.deallocate();
        freeDrawContext();
        assert mContext == null;
    }

    public void setImmutable() {
        if (mContext != null) {
            // Push any pending work to the RC now. setImmutable() is only called by the
            // destructor of a client-owned Surface, or explicitly in layer/filtering workflows. In
            // both cases this is restricted to the RC's thread. This is in contrast to deallocate(),
            // which might be called from another thread if it was linked to an Image used in multiple
            // recorders.
            flushPendingWork();
            mContext.untrackDevice(this);
            // Discarding the context ensures that there are no further operations that can be recorded
            // and is relied on by Image::notifyInUse() to detect when it can unlink from a Device.
            discardContext();
        }
    }

    private void freeDrawContext() {
        // we own these objects that used to record draws
        mKeyContext = null;

        if (mDrawContext != null) {
            mDrawContext.close();
        }
        mDrawContext = null;

        if (mUniformDataGatherer != null) {
            mUniformDataGatherer.close();
        }
        mUniformDataGatherer = null;
    }

    public void discardContext() {
        freeDrawContext();
        mContext = null;
    }

    @RawPtr
    public RecordingContext getContext() {
        return mContext;
    }

    @NonNull
    @Override
    public RecordingContext getCommandContext() {
        assert mContext != null;
        return mContext;
    }

    /**
     * @return raw ptr to the read view
     */
    @RawPtr
    public ImageProxyView getReadView() {
        return mDrawContext.getReadView();
    }

    @Nullable
    @SharedPtr
    public GraniteImage makeImageCopy(@NonNull Rect2ic subset,
                                      boolean budgeted,
                                      boolean mipmapped,
                                      boolean approxFit) {
        assert mContext.isOwnerThread();
        flushPendingWork();

        var srcInfo = getImageInfo();
        @RawPtr
        var srcView = mDrawContext.getReadView();
        String label = srcView.getProxy().getLabel();
        if (label == null || label.isEmpty()) {
            label = "CopyDeviceTexture";
        } else {
            label += "_DeviceCopy";
        }

        return GraniteImage.copy(
                mContext, srcView, srcInfo, subset,
                budgeted, mipmapped, approxFit, label
        );
    }

    @Override
    public void pushClipStack() {
        mClipStack.save();
    }

    @Override
    public void popClipStack() {
        mClipStack.restore();
    }

    public ClipStack getClipStack() {
        return mClipStack;
    }

    @Override
    public void clipRect(Rect2fc rect, int clipOp, boolean doAA) {
        mClipStack.clipShape(getLocalToDevice33(), new Rect(rect), clipOp, doAA);
    }

    @Override
    public boolean isClipAA() {
        return false;
    }

    @Override
    public boolean isClipEmpty() {
        return mClipStack.currentClipState() == ClipStack.STATE_EMPTY;
    }

    @Override
    public boolean isClipRect() {
        var state = mClipStack.currentClipState();
        return state == ClipStack.STATE_DEVICE_RECT || state == ClipStack.STATE_WIDE_OPEN;
    }

    @Override
    public boolean isClipWideOpen() {
        return mClipStack.currentClipState() == ClipStack.STATE_WIDE_OPEN;
    }

    private final Rect2f mTmpClipBounds = new Rect2f();
    private final Rect2i mTmpClipBoundsI = new Rect2i();

    @Override
    public void getClipBounds(@NonNull Rect2i bounds) {
        mClipStack.getConservativeBounds(mTmpClipBounds);
        mTmpClipBounds.roundOut(bounds);
    }

    @Override
    protected Rect2ic getClipBounds() {
        mClipStack.getConservativeBounds(mTmpClipBounds);
        mTmpClipBounds.roundOut(mTmpClipBoundsI);
        return mTmpClipBoundsI;
    }

    @Override
    public void drawPaint(Paint paint) {
        // An empty shape with an inverse fill completely floods the clip
        drawGeometry(getLocalToDevice33(), null, true,
                mPaintParams.set(paint, null, false, false),
                null, mRendererProvider.getNonAABoundsFill());
    }

    @Override
    public void drawPoints(int mode, float[] pts, int offset, int count, Paint paint) {
        // draw points by filling shape
        var oldStyle = paint.getStyle();
        paint.setStyle(Paint.FILL);
        var cap = paint.getStrokeCap();
        if (mode == Canvas.POINT_MODE_POINTS) {
            float radius = paint.getStrokeWidth() * 0.5f;
            if (cap == Paint.CAP_ROUND) {
                for (int i = offset, e = offset + count * 2; i < e; i += 2) {
                    drawEllipse(pts[i], pts[i + 1], radius, radius, paint);
                }
            } else {
                Rect2f rect = new Rect2f(-radius, -radius, radius, radius);
                for (int i = offset, e = offset + count * 2; i < e; i += 2) {
                    rect.offsetTo(pts[i], pts[i + 1]);
                    drawRect(rect, paint);
                }
            }
        } else {
            float width = paint.getStrokeWidth();
            int inc = mode == Canvas.POINT_MODE_LINES ? 4 : 2;
            for (int i = offset, e = offset + (count - 1) * 2; i < e; i += inc) {
                drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], cap, width, paint);
            }
        }
        paint.setStyle(oldStyle);
    }

    @Override
    public void drawLine(float x0, float y0, float x1, float y1,
                         @Paint.Cap int cap, float width, Paint paint) {
        var shape = new BoxShape();
        shape.setLine(x0, y0, x1, y1, cap, width);
        drawGeometry(getLocalToDevice33(), shape, false,
                mPaintParams.set(paint, null, false, false), paint,
                mRendererProvider.getAnalyticBox(false));
    }

    @Override
    public void drawRect(Rect2fc r, Paint paint) {
        PaintParams p = mPaintParams.set(paint, null, false, false);
        if (paint.getStyle() == Paint.FILL) {
            if (!paint.isAntiAlias()) {
                drawGeometry(getLocalToDevice33(), new Rect(r), false,
                        p, paint,
                        mRendererProvider.getNonAABoundsFill());
                return;
            }
        } else {
            var join = paint.getStrokeJoin();
            boolean complex = join == Paint.JOIN_BEVEL ||
                    (join == Paint.JOIN_MITER && paint.getStrokeMiter() < MathUtil.SQRT2);
            if (complex) {
                // since it's a stroke, promote it to RRect and lose the original geometric info
                drawGeometry(getLocalToDevice33(), new RRect(r), false,
                        p, paint,
                        mRendererProvider.getAnalyticRRect());
                return;
            }
        }
        drawGeometry(getLocalToDevice33(), new Rect(r), false,
                p, paint,
                mRendererProvider.getAnalyticBox(false));
    }

    @Override
    public void drawRRect(RRect rr, Paint paint) {
        //TODO stroking an ellipse requires new renderer
        boolean complex = switch (rr.getType()) {
            case RRect.kOval_Type, RRect.kSimple_Type -> !rr.isSimpleCircular() && !rr.isCircle();
            case RRect.kNineSlice_Type, RRect.kComplex_Type -> true;
            default -> {
                // empty and rect are handled by Canvas
                assert false;
                yield false;
            }
        };
        PaintParams p = mPaintParams.set(paint, null, false, false);
        if (complex) {
            drawGeometry(getLocalToDevice33(), new RRect(rr), false,
                    p, paint,
                    mRendererProvider.getAnalyticRRect());
        } else {
            drawGeometry(getLocalToDevice33(), new BoxShape(rr), false,
                    p, paint,
                    mRendererProvider.getAnalyticBox(false));
        }
    }

    @Override
    public void drawEllipse(float cx, float cy, float rx, float ry, Paint paint) {
        PaintParams p = mPaintParams.set(paint, null, false, false);
        if (!RRect.radiiAlmostEqual(rx, ry)) {
            //TODO stroking an ellipse requires new renderer
            var shape = new RRect();
            shape.setEllipse(cx, cy, rx, ry);
            drawGeometry(getLocalToDevice33(), shape, false,
                    p, paint,
                    mRendererProvider.getAnalyticRRect());
        } else {
            var shape = new BoxShape();
            shape.setCircle(cx, cy, rx);
            drawGeometry(getLocalToDevice33(), shape, false,
                    p, paint,
                    mRendererProvider.getAnalyticBox(false));
        }
    }

    @Override
    public void drawArc(float cx, float cy, float radius, float startAngle,
                        float sweepAngle, int cap, float width, Paint paint) {
        var shape = new ArcShape(cx, cy, radius, startAngle, sweepAngle, width * 0.5f);
        shape.mType = switch (cap) {
            case Paint.CAP_BUTT -> ArcShape.kArc_Type;
            case Paint.CAP_ROUND -> ArcShape.kArcRound_Type;
            case Paint.CAP_SQUARE -> ArcShape.kArcSquare_Type;
            default -> throw new AssertionError();
        };
        drawGeometry(getLocalToDevice33(), shape, false,
                mPaintParams.set(paint, null, false, false), paint,
                mRendererProvider.getArc(shape.mType));
    }

    @Override
    public void drawPie(float cx, float cy, float radius, float startAngle,
                        float sweepAngle, Paint paint) {
        var shape = new ArcShape(cx, cy, radius, startAngle, sweepAngle, 0);
        shape.mType = ArcShape.kPie_Type;
        drawGeometry(getLocalToDevice33(), shape, false,
                mPaintParams.set(paint, null, false, false), paint,
                mRendererProvider.getArc(shape.mType));
    }

    @Override
    public void drawChord(float cx, float cy, float radius, float startAngle,
                          float sweepAngle, Paint paint) {
        var shape = new ArcShape(cx, cy, radius, startAngle, sweepAngle, 0);
        shape.mType = ArcShape.kChord_Type;
        drawGeometry(getLocalToDevice33(), shape, false,
                mPaintParams.set(paint, null, false, false), paint,
                mRendererProvider.getArc(shape.mType));
    }

    @Override
    public void drawImageRect(@RawPtr Image image, Rect2fc src, Rect2fc dst,
                              SamplingOptions sampling, Paint paint, int constraint) {
        Paint modifiedPaint = new Paint(paint);
        Rect2f modifiedDst = ImageShader.preparePaintForDrawImageRect(
                image, sampling, src, dst,
                constraint == Canvas.SRC_RECT_CONSTRAINT_STRICT,
                modifiedPaint
        );
        if (!modifiedDst.isEmpty()) {
            drawRect(modifiedDst, modifiedPaint);
        }
        modifiedPaint.close();
    }

    @Override
    protected void onDrawGlyphRunList(Canvas canvas,
                                      GlyphRunList glyphRunList,
                                      Paint paint) {
        Matrix positionMatrix = new Matrix(getLocalToDevice33());
        positionMatrix.preTranslate(glyphRunList.mOriginX, glyphRunList.mOriginY);

        if (glyphRunList.mOriginalTextBlob != null) {
            // use cache if it comes from TextBlob
            var blobCache = mContext.getTextBlobCache();
            mBlobKey.update(glyphRunList, paint, positionMatrix);
            var entry = blobCache.find(glyphRunList.mOriginalTextBlob, mBlobKey);
            if (entry == null || !entry.canReuse(paint, positionMatrix,
                    glyphRunList.getSourceBounds().centerX(),
                    glyphRunList.getSourceBounds().centerY())) {
                if (entry != null) {
                    // We have to remake the blob because changes may invalidate our masks.
                    blobCache.remove(entry);
                }
                entry = BakedTextBlob.make(
                        glyphRunList,
                        paint,
                        positionMatrix,
                        StrikeCache.getGlobalStrikeCache()
                );
                entry = blobCache.insert(glyphRunList.mOriginalTextBlob, mBlobKey,
                        entry);
            }
            entry.draw(canvas, glyphRunList.mOriginX, glyphRunList.mOriginY, paint, this);
        } else {
            SubRunContainer container = SubRunContainer.make(
                    glyphRunList,
                    positionMatrix,
                    paint,
                    StrikeCache.getGlobalStrikeCache()
            );
            container.draw(canvas, glyphRunList.mOriginX, glyphRunList.mOriginY, paint, this);
        }
    }

    @Override
    public void drawVertices(Vertices vertices, Blender blender, Paint paint) {
        drawGeometry(getLocalToDevice33(), vertices, false,
                mPaintParams.set(paint, blender, false, false), null,
                mRendererProvider.getVertices(
                        vertices.getVertexMode(), vertices.hasColors(), vertices.hasTexCoords()));
    }

    @Override
    public void drawEdgeAAQuad(Rect2fc r, @Size(8) float @Nullable [] clip, int clipOffset, int flags, Paint paint) {
        EdgeAAQuad quad = clip != null ? new EdgeAAQuad(clip, clipOffset, flags) : new EdgeAAQuad(r, flags);
        drawGeometry(getLocalToDevice33(), quad, false,
                mPaintParams.set(paint, null, false, false), null,
                mRendererProvider.getPerEdgeAAQuad());
    }

    @Override
    public boolean drawBlurredRRect(RRect rr, Paint paint, float blurRadius, float noiseAlpha) {
        if (!Float.isFinite(blurRadius)) {
            return true;
        }
        //TODO compute device blur radius
        if (blurRadius < 0.1f) {
            drawRRect(rr, paint);
            return true;
        }
        if (!(noiseAlpha >= 0f)) {
            noiseAlpha = 0f;
        }
        var shape = new BoxShape();
        shape.setBlur(rr, blurRadius, noiseAlpha);
        drawGeometry(getLocalToDevice33(), shape, false,
                mPaintParams.set(paint, null, false, false), null,
                mRendererProvider.getAnalyticBox(true));
        return true;
    }

    public void drawAtlasSubRun(SubRunContainer.AtlasSubRun subRun,
                                float originX, float originY,
                                Paint paint) {
        int maskFormat = subRun.getMaskFormat();
        if (!mContext.getAtlasProvider().getGlyphAtlasManager().initAtlas(
                maskFormat
        )) {
            return;
        }

        boolean ignoreShader = subRun.getMaskFormat() == Engine.MASK_FORMAT_ARGB;
        PaintParams p = mPaintParams.set(paint, ignoreShader ? BlendMode.DST_IN : null, false, ignoreShader);

        int subRunEnd = subRun.getGlyphCount();
        boolean flushed = false;
        for (int subRunCursor = 0; subRunCursor < subRunEnd; ) {
            int glyphsPrepared = subRun.prepareGlyphs(subRunCursor, subRunEnd,
                    mContext);
            if (glyphsPrepared < 0) {
                // There was a problem allocating the glyph in the atlas.
                break;
            }
            if (glyphsPrepared > 0) {
                // subRunToDevice is our "localToDevice",
                // as sub run's coordinates are returned in sub run's space
                Matrix subRunToLocal = new Matrix();
                Matrix subRunToDevice = new Matrix();
                int filter = subRun.getMatrixAndFilter(
                        getLocalToDevice33(),
                        originX, originY,
                        subRunToLocal,
                        subRunToDevice);
                SubRunData subRunData = new SubRunData(subRun,
                        subRunToLocal, filter,
                        subRunCursor, glyphsPrepared,
                        mContext.getAtlasProvider());

                drawGeometry(subRunToDevice, subRunData, false,
                        p, null,
                        mRendererProvider.getRasterText(maskFormat));
            } else if (flushed) {
                // Treat as an error.
                break;
            }
            subRunCursor += glyphsPrepared;

            if (subRunCursor < subRunEnd) {
                // Flush if not all the glyphs are handled because the atlas is out of space.
                // We flush every Device because the glyphs that are being flushed/referenced are not
                // necessarily specific to this Device. This addresses both multiple Surfaces within
                // a Recorder, and nested layers.
                mContext.flushTrackedDevices();
                flushed = true;
            }
        }
    }

    public void drawGeometry(@NonNull Matrixc localToDevice,
                             @Nullable Bounded geometry,
                             boolean inverseFill,
                             @NonNull PaintParams paint,
                             @Nullable Paint style,
                             GeometryRenderer renderer) {
        Draw draw = new Draw(localToDevice, geometry, inverseFill);
        assert geometry != null || draw.isFloodFill();

        if (style != null && style.getStyle() != Paint.FILL) {
            draw.mHalfWidth = style.getStrokeWidth() * 0.5f;
            switch (style.getStrokeJoin()) {
                case Paint.JOIN_ROUND -> draw.mJoinLimit = -1;
                case Paint.JOIN_BEVEL -> draw.mJoinLimit = 0;
                case Paint.JOIN_MITER -> draw.mJoinLimit = style.getStrokeMiter();
            }
            draw.mStrokeCap = (byte) style.getStrokeCap();
            draw.mStrokeAlign = (byte) style.getStrokeAlign();
        }

        // Calculate the clipped bounds of the draw and determine the clip elements that affect the
        // draw without updating the clip stack.
        assert mElementsForMask.isEmpty();
        int clipped = mClipStack.prepareForDraw(draw, mElementsForMask);
        if (clipped == ClipStack.CLIPPED_OUT) {
            return;
        }

        if (clipped == ClipStack.CLIPPED_GEOMETRICALLY) {
            //TODO require a general way to choose renderer for flood-fill/rect/box/rrect
            // currently this can only be flood fill
            renderer = mRendererProvider.getNonAABoundsFill();
        }
        assert renderer != null;

        if (renderer.outsetBoundsForAA()) {
            draw.outsetBoundsForAA();
        }

        // A renderer that emits a primitive color should only be used by a drawX() call that sets a
        // non-null primitive blender.
        assert (paint.getPrimitiveBlender() != null) == (renderer.emitsPrimitiveColor());

        // collect fragment data and pipeline key
        KeyContext keyContext = mKeyContext.reset(paint.getColor());
        if (renderer.handlesSolidColor()) {
            draw.mSolidColor = paint.getSolidColor(keyContext);
            // solid color will be non-null if paint is
        }
        // Add paint fragment stages, final blender, clip shader,
        int dstUsage = paint.toKey(keyContext,
                draw.mSolidColor,
                null);
        // KeyBuilder as a read-only view
        Key paintParamsKey = keyContext.paintParamsKeyBuilder;
        // uniform data gatherer will be used for geometry steps, deduplicate now
        int fragmentUniformIndex = mDrawContext.getFragmentUniformDataCache()
                .insert(keyContext.uniformDataGatherer.finish());

        boolean overwritesEntireSurface = dstUsage == PaintParams.DST_USAGE_NONE
                && draw.isFloodFill()
                && mElementsForMask.isEmpty()
                && draw.mScissorRect.contains(getBounds());
        if (overwritesEntireSurface) {
            BlendMode bm = paint.getFinalBlendMode();
            // Since we don't depend on the dst, a dst-out blend mode implies source is
            // opaque, which causes dst-out to behave like clear.
            if (bm == BlendMode.CLEAR || bm == BlendMode.DST_OUT) {
                // do fullscreen clear
                mDrawContext.clear(null);
                return;
            }
            // Since flood-fill uses cover bounds renderer, and it can handle solid color.
            // If the color can be extracted, then solid color is not null.
            if (draw.mSolidColor != null) {
                // do fullscreen clear
                mDrawContext.clear(draw.mSolidColor);
                return;
            }
            mDrawContext.discard();
            // But then continue to render the flood fill with shading
        }

        int numNewRenderSteps = renderer.numSteps();
        if (draw.mHalfWidth < 0 && renderer.useNonAAInnerFill()) {
            assert mRendererProvider.getNonAABoundsFill().numSteps() == 1;
            numNewRenderSteps += 1; // make it a compile-time constant
        }

        // Decide if we have any reason to flush pending work. We want to flush before updating the clip
        // state or making any permanent changes to a path atlas, since otherwise clip operations and/or
        // atlas entries for the current draw will be flushed.
        final boolean needsFlush = needsFlushBeforeDraw(numNewRenderSteps);
        if (needsFlush) {
            flushPendingWork();
        }

        // we need to persist the Draw, so clone matrix first
        draw.mTransform = draw.mTransform.clone();

        // Update the clip stack after issuing a flush (if it was needed). A draw will be recorded after
        // this point.
        draw.mDepth = mCurrentDepth + 1;
        int clipOrder = mClipStack.updateForDraw(
                draw, mElementsForMask, mColorDepthBoundsManager, draw.mDepth);

        // A draw's order always depends on the clips that must be drawn before it
        draw.dependsOnPaintersOrder(clipOrder);
        // If a draw is not opaque, it must be drawn after the most recent draw it intersects with in
        // order to blend correctly.
        if (renderer.emitsCoverage() || dstUsage != PaintParams.DST_USAGE_NONE) {
            int prevDraw = mColorDepthBoundsManager.getMostRecentDraw(draw.mDrawBounds);
            draw.dependsOnPaintersOrder(prevDraw);
        }

        if ((renderer.depthStencilFlags() & Engine.DepthStencilFlags.kStencil) != 0) {
            //TODO stencil set
        } else if (dstUsage == PaintParams.DST_USAGE_NONE && renderer == mRendererProvider.getNonAABoundsFill()) {
            // Sort this draw front to back since it will not blend against what came before it.
            // We could do this for all opaque/non-blending draws but that can hurt the performance of
            // the sort in SurfaceDrawContext if it has to effectively reverse a large list. For now,
            // limit it to flood fills (here and for the later non-AA inner fill).
            draw.reverseDepthAsStencil();
        }

        if (draw.mHalfWidth < 0 && // fill
                dstUsage == PaintParams.DST_USAGE_NONE && renderer.useNonAAInnerFill()) {
            assert renderer.emitsCoverage();
            skipInnerFill: {
                Rect2f bounds;
                if (draw.mGeometry instanceof Rect r) {
                    bounds = new Rect2f();
                    r.getBounds(bounds);
                } else if (draw.mGeometry instanceof BoxShape b && b.mType == BoxShape.kBox_Type) {
                    bounds = new Rect2f();
                    b.getInnerBounds(bounds);
                } else if (draw.mGeometry instanceof RRect rr) {
                    bounds = new Rect2f();
                    rr.getInnerBounds(bounds);
                } else if (draw.mGeometry instanceof EdgeAAQuad q && q.isRect()) {
                    bounds = new Rect2f();
                    q.getBounds(bounds);
                } else {
                    break skipInnerFill;
                }
                // a renderer that uses non-AA inner fill doesn't handle inverse fills,
                // only NonAABoundsFill and path renderer handles them
                assert !draw.mInverseFill;

                // If the aa inset is too large, rect becomes empty and the inner bounds draw is
                // automatically skipped
                // the AA radius comes from outer bounds, we just overestimate it
                bounds.inset(draw.mAARadius, draw.mAARadius);
                // Only add a second draw if it will have a reasonable number of covered pixels; otherwise
                // we are just adding draws to sort and pipelines to switch around.
                // Approximate the device-space area based on the minimum scale factor of the transform.
                if (bounds.width() * bounds.height() >= (128*64) * draw.mAARadius) {
                    Draw drawWithoutCoverage = draw.clone();
                    drawWithoutCoverage.mGeometry = new Rect(bounds);
                    drawWithoutCoverage.mPaintOrder = Draw.NO_INTERSECTION;
                    drawWithoutCoverage.dependsOnPaintersOrder(clipOrder);
                    // The regular draw has analytic coverage, so isn't being sorted front to back, but
                    // we do want to sort the inner fill to maximize overdraw reduction
                    drawWithoutCoverage.reverseDepthAsStencil();
                    mDrawContext.recordDraw(mRendererProvider.getNonAABoundsFill(),
                            drawWithoutCoverage, fragmentUniformIndex, paintParamsKey, mUniformDataGatherer);
                    // Force the coverage draw to come after the non-AA draw in order to benefit from
                    // early depth testing.
                    draw.dependsOnPaintersOrder(drawWithoutCoverage.mPaintOrder);
                }
            }
        }

        mDrawContext.recordDraw(renderer, draw, fragmentUniformIndex, paintParamsKey, mUniformDataGatherer);

        // Post-draw book keeping (bounds manager, depth tracking, etc.)
        mColorDepthBoundsManager.recordDraw(draw.mDrawBounds, draw.mPaintOrder);
        mCurrentDepth = draw.mDepth;

        mElementsForMask.clear();
    }

    public void drawClipShape(Draw draw) {
        if (draw.mInverseFill || !(draw.mGeometry instanceof Rect)) {
            //TODO not implement tessellation yet
            return;
        }
        // difference clip => non-inverse-fill, draw rect
        GeometryRenderer renderer = mRendererProvider.getNonAABoundsFill();

        assert mDrawContext.numPendingSteps() + renderer.numSteps() < DrawPass.MAX_RENDER_STEPS;

        assert !renderer.emitsCoverage();

        mDrawContext.recordDraw(renderer, draw, DrawPass.INVALID_INDEX, KeyBuilder.EMPTY, mUniformDataGatherer);

        // This ensures that draws recorded after this clip shape has been popped off the stack will
        // be unaffected by the Z value the clip shape wrote to the depth attachment.
        if (draw.mDepth > mCurrentDepth) {
            mCurrentDepth = draw.mDepth;
        }
    }

    private boolean needsFlushBeforeDraw(int numNewRenderSteps) {
        // Must also account for the elements in the clip stack that might need to be recorded.
        numNewRenderSteps += mClipStack.maxDeferredClipDraws() * GeometryRenderer.MAX_RENDER_STEPS;
        // Need flush if we don't have room to record into the current list.
        return (DrawPass.MAX_RENDER_STEPS - mDrawContext.numPendingSteps()) < numNewRenderSteps;
    }

    /**
     * Ensures clip elements are drawn that will clip previous draw calls, snaps all pending work
     * from the {@link SurfaceDrawContext} as a {@link RenderPassTask} and records it in the
     * {@link GraniteDevice}'s {@link RecordingContext}.
     */
    public void flushPendingWork() {
        assert mContext.isOwnerThread();
        mPaintParams.reset();
        // Push any pending uploads from the atlas provider that pending draws reference.
        mContext.getAtlasProvider().recordUploads(mDrawContext);

        // Clip shapes are depth-only draws, but aren't recorded in the DrawContext until a flush in
        // order to determine the Z values for each element.
        mClipStack.recordDeferredClipDraws();

        // Flush all pending items to the internal task list and reset Device tracking state
        mDrawContext.flush(mContext);

        mColorDepthBoundsManager.clear();
        mCurrentDepth = Draw.CLEAR_DEPTH;

        // Any cleanup in the AtlasProvider
        mContext.getAtlasProvider().compact();

        DrawTask drawTask = mDrawContext.snapDrawTask(mContext);

        if (drawTask != null) {
            mContext.addTask(drawTask);
        }
    }
}

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
import icyllis.arc3d.granite.geom.BoundsManager;
import icyllis.arc3d.granite.geom.EdgeAAQuad;
import icyllis.arc3d.granite.geom.Rect;
import icyllis.arc3d.sketch.Bounded;
import icyllis.arc3d.sketch.Canvas;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.RRect;
import icyllis.arc3d.sketch.Shape;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * GPU hierarchical clipping.
 * <p>
 * We use scissor test and depth test to apply clip, antialiasing only works on
 * multisampled targets.
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class ClipStack {

    /**
     * Clip ops.
     */
    public static final int
            OP_DIFFERENCE = Canvas.CLIP_OP_DIFFERENCE,  // target minus operand
            OP_INTERSECT = Canvas.CLIP_OP_INTERSECT;    // target intersected with operand

    /**
     * Clip states.
     */
    public static final int
            STATE_EMPTY = 0,
            STATE_WIDE_OPEN = 1,
            STATE_DEVICE_RECT = 2,
            STATE_COMPLEX = 3;

    /**
     * Applied result.
     */
    public static final int
            CLIPPED_OUT = 0,
            CLIPPED_GEOMETRICALLY = 1,
            CLIPPED = 2;

    // The default tolerance to use for fuzzy geometric comparisons that are already transformed
    // into device-space. Distances, containment checks, or equality tests closer than
    // DEFAULT_PIXEL_TOLERANCE (< ~0.004) can be considered perceptibly equivalent. This can be
    // tested in a different coordinate space by scaling this constant with localAARadius
    public static final float DEFAULT_PIXEL_TOLERANCE = 0.0039f; // (1.0f - 0.001f) / 255.f;

    private final ObjectArrayList<SaveRecord> mSaves = new ObjectArrayList<>();
    private final ObjectArrayList<RawElement> mElements = new ObjectArrayList<>();
    private final Collection<Element> mElementsView = Collections.unmodifiableCollection(mElements);

    private final GraniteDevice mDevice;
    private final Rect2i mDeviceBounds;
    private final Rect2f mDeviceBoundsF;

    public ClipStack(@NonNull GraniteDevice device) {
        mDevice = device;
        mDeviceBounds = new Rect2i(device.getBounds());
        mDeviceBoundsF = new Rect2f(mDeviceBounds);
        mSaves.add(new SaveRecord(mDeviceBoundsF));
    }

    public int currentClipState() {
        return mSaves.top().mState;
    }

    public void save() {
        mSaves.top().pushSave();
    }

    public void restore() {
        SaveRecord current = mSaves.top();
        if (current.popSave()) {
            // This was just a deferred save being undone, so the record doesn't need to be removed yet
            return;
        }

        // When we remove a save record, we delete all elements >= its starting index and any masks
        // that were rasterized for it.
        current.removeElements(mElements, mDevice);
        mSaves.pop();
        // Restore any remaining elements that were only invalidated by the now-removed save record.
        mSaves.top().restoreElements(mElements);
    }

    // for testing purposes only, does not represent the effective elements
    @UnmodifiableView
    public Collection<Element> elements() {
        return mElementsView;
    }

    private final RawElement mTmpElement = new RawElement();

    // This method transfers ownership of `shape`, meaning it can be modified.
    // However, `localToDevice` will be copied.
    // The caller must simplify the shape; for example, RRect can be simplified to Rect,
    // and Path can be simplified to Rect/RRect. Furthermore, the shape cannot be empty.
    public void clipShape(@NonNull Matrixc localToDevice,
                          @NonNull Shape shape,
                          int op, boolean doAA) {
        assert shape instanceof Rect || shape instanceof RRect || shape instanceof Path;

        if (mSaves.top().state() == STATE_EMPTY) {
            return;
        }

        // This will apply the transform if it's shape-type preserving, and clip the element's bounds
        // to the device bounds (NOT the conservative clip bounds, since those are based on the net
        // effect of all elements while device bounds clipping happens implicitly. During addElement,
        // we may still be able to invalidate some older elements).
        RawElement element = mTmpElement.init(
                mDeviceBoundsF,
                localToDevice,
                shape,
                op,
                doAA
        );

        // An empty op means do nothing (for difference), or close the save record, so we try and detect
        // that early before doing additional unnecessary save record allocation.
        if (element.shape() == null) {
            if (element.op() == OP_DIFFERENCE) {
                // If the shape is empty and we're subtracting, this has no effect on the clip
                return;
            }
            // else we will make the clip empty, but we need a new save record to record that change
            // in the clip state; fall through to below and updateForElement() will handle it.
        }

        boolean wasDeferred;
        SaveRecord current = mSaves.top();
        if (current.canBeUpdated()) {
            // Current record is still open, so it can be modified directly
            wasDeferred = false;
        } else {
            // Must un-defer the save to get a new record.
            boolean alive = current.popSave();
            assert alive;
            wasDeferred = true;
            current = new SaveRecord(current, mElements.size());
            mSaves.push(current);
        }

        int elementCount = mElements.size();
        if (!current.addElement(element, mElements, mDevice)) {
            if (wasDeferred) {
                // We made a new save record, but ended up not adding an element to the stack.
                // So instead of keeping an empty save record around, pop it off and restore the counter
                assert (elementCount == mElements.size());
                mSaves.pop();
                mSaves.top().pushSave();
            }
        }

        element.mShape = null; // match rvalue, avoid leaking path data
    }

    public int maxDeferredClipDraws() {
        return mElements.size();
    }

    public void getConservativeBounds(Rect2f out) {
        SaveRecord current = mSaves.top();
        int state = current.state();
        if (state == STATE_EMPTY) {
            out.setEmpty();
        } else if (state == STATE_WIDE_OPEN) {
            out.set(mDeviceBoundsF);
        } else {
            if (current.op() == OP_DIFFERENCE) {
                subtract(mDeviceBoundsF, current.mInnerBounds, out, true);
            } else {
                assert (mDeviceBoundsF.contains(current.outerBounds()));
                out.set(current.mOuterBounds);
            }
        }
    }

    private final DrawShape mTmpDraw = new DrawShape();

    // Compute the bounds and the effective elements of the clip stack when applied to the draw
    // described by the provided transform, shape, and stroke.
    //
    // Applying clips to a draw is a mostly lazy operation except for what is returned:
    //  - The Clip's scissor is set to 'conservativeBounds()'.
    //  - The Clip stores the draw's clipped bounds, taking into account its transform, styling, and
    //    the above scissor.
    //  - The Clip also stores the draw's fill-style invariant clipped bounds which is used in atlas
    //    draws and may differ from the draw bounds.
    //
    // All clip elements that affect the draw will be returned in `elementsForMask` alongside
    // the bounds. This method does not have any side-effects and the per-clip element state has to
    // be explicitly updated by calling `updateForDraw()` which prepares the clip stack for
    // later rendering.
    //
    // The returned clip element list will be empty if the shape is clipped out or if the draw is
    // unaffected by any of the clip elements.
    public int prepareForDraw(@NonNull Draw draw,
                                  @NonNull List<Element> elementsForMask) {
        SaveRecord cs = mSaves.top();
        if (cs.state() == STATE_EMPTY) {
            // We know the draw is clipped out so don't bother computing the base draw bounds.
            return CLIPPED_OUT;
        }

        DrawShape ds = mTmpDraw.init(
                draw.mTransform, draw.mGeometry, draw.mInverseFill
        );
        if (!ds.applyStyle(draw, mDeviceBoundsF)) {
            return CLIPPED_OUT;
        }

        // For intersect clips, the scissor rectangle is snapped outer bounds (to loosely restrict
        // rasterization if absolutely necessary). Cases where the draw is fully inside the scissor are
        // automatically handled during GPU command generation.
        //
        // For difference clips, a tight scissor could be `subtract(drawBounds, cs.innerBounds())`
        // but this is only useful when the clip spans across an axis of the draw and can otherwise
        // lead to scissor state thrashing since it's connected to the draw's bounds as well. So just
        // use the device bounds for simplicity.
        ds.applyScissor(cs.op() == OP_INTERSECT && cs.state() != STATE_WIDE_OPEN
                ? snapScissor(cs.outerBounds(), mDevice.getWidth(), mDevice.getHeight())
                : mDeviceBounds);

        switch (getClipGeometry(cs, ds)) {
            case CLIP_GEOMETRY_EMPTY:
                // The draw is offscreen or clipped out, so there is no need to visit the clip elements.
                return CLIPPED_OUT;

            case CLIP_GEOMETRY_B_ONLY:
                // The draw is unaffected by the clip stack (except possibly `scissor`), and there's no
                // need to visit each clip element.
                return ds.toClip(draw);

            case CLIP_GEOMETRY_A_ONLY:
                // The draw covers the clip entirely. Replace the shape with a flood fill, which can
                // intersect with shapes efficiently.
                ds.resetToFloodFill();

                // fallthrough
            case CLIP_GEOMETRY_BOTH:
                // Check each element's influence on the draw below
                break;
        }

        // If we made it here, the clip stack affects the draw in a complex way so iterate each element.
        // A draw is a transformed shape that "intersects" the clip. We use empty inner bounds because
        // there's currently no way to re-write the draw as the clip's geometry, so there's no need to
        // check if the draw contains the clip (vice versa is still checked and represents an unclipped
        // draw so is very useful to identify).
        assert elementsForMask.isEmpty();

        for (int i = mElements.size() - 1; i >= 0; --i) {
            RawElement e = mElements.get(i);
            if (i < cs.oldestElementIndex()) {
                // All earlier elements have been invalidated by elements already processed
                break;
            } else if (e.isInvalid()) {
                // Cannot affect the draw
                continue;
            }

            switch (getClipGeometry(e, ds)) {
                case CLIP_GEOMETRY_EMPTY:
                    // This can happen for difference op elements that have a larger fInnerBounds than
                    // can be preserved at the next level.
                    elementsForMask.clear();
                    return CLIPPED_OUT;
                case CLIP_GEOMETRY_B_ONLY:
                    // This element does not interact, so continue to the next
                    continue;
                case CLIP_GEOMETRY_A_ONLY:
                    // This element is covered entirely by the draw, so the draw's geometry can be
                    // replaced assuming the coordinate spaces are compatible. To facilitate this, we
                    // switch the drawn geometry to a flood fill and then fall through to intersection.
                    // Even if the coordinate spaces aren't in alignment, this eliminates the draw's
                    // source of analytic coverage.
                    ds.resetToFloodFill();

                    // fallthrough
                case CLIP_GEOMETRY_BOTH: {
                    //TODO geometrically intersection

                    // Second try to tighten the scissor, which is lighter weight than adding an
                    // analytic clip pipeline variation or triggering MSAA.
                    if (e.mEffectiveNonAA && e.clipType() == STATE_DEVICE_RECT) {
                        Rect2i scissor = new Rect2i();
                        // in this case, shape bounds == outer bounds == inner bounds
                        e.shapeBounds().round(scissor);
                        ds.applyScissor(scissor);
                        continue;
                    }

                    // Third try to use non-AA fill without analytic or tessellator
                    if (e.mEffectiveNonAA && e.op() == OP_DIFFERENCE && e.shape() instanceof Rect) {
                        elementsForMask.add(e);
                        continue;
                    }

                    // First apply using HW methods (scissor and window rects). When the inner and outer
                    // bounds match, nothing else needs to be done.
                    boolean fullyApplied = false;
                    //TODO analytic

                        /*if (e.op() == OP_INTERSECT) {
                            // The second test allows clipped draws that are scissored by multiple
                            // elements to remain scissor-only.
                            fullyApplied = e.innerBounds() == e.outerBounds() ||
                                    e.innerBounds().contains(scissor);
                        }*/

                    if (!fullyApplied) {
                        elementsForMask.add(e);
                    }

                    break;
                }
            }
        }

        return ds.toClip(draw);
    }

    // Update the per-clip element state for later rendering using pre-computed clip state data for
    // a particular draw. The provided 'z' value is the depth value that the draw will use if it's
    // not clipped out entirely.
    //
    // The returned CompressedPaintersOrder is the largest order that will be used by any of the
    // clip elements that affect the draw.
    //
    // If the provided `clipState` indicates that the draw will be clipped out, then this method has
    // no effect and returns DrawOrder::NO_INTERSECTION.
    public int updateForDraw(@NonNull Draw draw,
                             @NonNull List<Element> elementsForMask,
                             @NonNull BoundsManager boundsManager,
                             int depth) {
        if (draw.isClippedOut()) {
            return Draw.NO_INTERSECTION;
        }

        assert mSaves.top().state() != STATE_EMPTY;

        int maxClipOrder = Draw.NO_INTERSECTION;
        for (int i = 0; i < elementsForMask.size(); i++) {
            RawElement e = (RawElement) elementsForMask.get(i);
            int order = e.updateForDraw(boundsManager,
                    mDevice.getWidth(), mDevice.getHeight(),
                    draw.mDrawBounds, depth);
            maxClipOrder = Math.max(maxClipOrder, order);
        }

        return maxClipOrder;
    }

    public void recordDeferredClipDraws() {
        for (int i = 0; i < mElements.size(); i++) {
            var e = mElements.get(i);
            // When a Device requires all clip elements to be recorded, we have to iterate all elements,
            // and will draw clip shapes for elements that are still marked as invalid from the clip
            // stack, including those that are older than the current save record's oldest valid index,
            // because they could have accumulated draw usage prior to being invalidated, but weren't
            // flushed when they were invalidated because of an intervening save.
            e.drawClip(mDevice);
        }
    }

    interface TransformedShape {

        int op();

        Rect2fc shapeBounds();

        Matrixc localToDevice();

        Rect2fc outerBounds();

        boolean contains(TransformedShape o);
    }

    static boolean intersects(
            @NonNull TransformedShape a,
            @NonNull TransformedShape b
    ) {
        if (!a.outerBounds().intersects(b.outerBounds())) {
            return false;
        }

        if (a.localToDevice().isAxisAligned() &&
                b.localToDevice().isAxisAligned()) {
            // The two shape's coordinate spaces are different but both rect-stays-rect or simpler.
            // This means, though, that their outer bounds approximations are tight to their transformed
            // shape bounds. There's no point to do further tests given that and that we already found
            // that these outer bounds *do* intersect.
            return true;
        } else if (a.localToDevice().equals(b.localToDevice())) {
            // Since the two shape's local coordinate spaces are the same, we can compare shape
            // bounds directly for a more accurate intersection test. We intentionally do not go
            // further and do shape-specific intersection tests since these could have unknown
            // complexity (for paths) and limited utility (e.g. two round rects that are disjoint
            // solely from their corner curves).
            return a.shapeBounds().intersects(b.shapeBounds());
        }
        // assume intersection and allow the rasterizer to handle perspective clipping.
        return true;
    }

    // This captures which of the two elements in (A op B) would be required when they are combined,
    // where op is intersect or difference.
    static final int
            CLIP_GEOMETRY_EMPTY = 0,
            CLIP_GEOMETRY_A_ONLY = 1,
            CLIP_GEOMETRY_B_ONLY = 2,
            CLIP_GEOMETRY_BOTH = 3;

    // RawElement <-> RawElement
    // RawElement <-> DrawShape
    // SaveRecord <-> RawElement
    // SaveRecord <-> DrawShape
    static int getClipGeometry(@NonNull TransformedShape A,
                               @NonNull TransformedShape B) {

        switch ((A.op() << 1) | B.op()) {
            case (OP_INTERSECT << 1) | OP_INTERSECT:
                // Intersect (A) + Intersect (B)
                if (!intersects(A, B)) {
                    // Regions with non-zero coverage are disjoint, so intersection = empty
                    return CLIP_GEOMETRY_EMPTY;
                }

                if (B.contains(A)) {
                    // B's full coverage region contains entirety of A, so intersection = A
                    return CLIP_GEOMETRY_A_ONLY;
                }

                if (A.contains(B)) {
                    // A's full coverage region contains entirety of B, so intersection = B
                    return CLIP_GEOMETRY_B_ONLY;
                }

                // The shapes intersect in some non-trivial manner
                return CLIP_GEOMETRY_BOTH;
            case (OP_INTERSECT << 1) | OP_DIFFERENCE:
                // Intersect (A) + Difference (B)
                if (!intersects(A, B)) {
                    // A only intersects B's full coverage region, so intersection = A
                    return CLIP_GEOMETRY_A_ONLY;
                }

                if (B.contains(A)) {
                    // B's zero coverage region completely contains A, so intersection = empty
                    return CLIP_GEOMETRY_EMPTY;
                }

                // Intersection cannot be simplified. Note that the combination of a intersect
                // and difference op in this order cannot produce kBOnly
                return CLIP_GEOMETRY_BOTH;
            case (OP_DIFFERENCE << 1) | OP_INTERSECT:
                // Difference (A) + Intersect (B) - the mirror of Intersect(A) + Difference(B),
                // but combining is commutative so this is equivalent barring naming.
                if (!intersects(A, B)) {
                    // B only intersects A's full coverage region, so intersection = B
                    return CLIP_GEOMETRY_B_ONLY;
                }

                if (A.contains(B)) {
                    // A's zero coverage region completely contains B, so intersection = empty
                    return CLIP_GEOMETRY_EMPTY;
                }

                // Cannot be simplified
                return CLIP_GEOMETRY_BOTH;
            case (OP_DIFFERENCE << 1) | OP_DIFFERENCE:
                // Difference (A) + Difference (B)
                if (A.contains(B)) {
                    // A's zero coverage region contains B, so B doesn't remove any extra
                    // coverage from their intersection.
                    return CLIP_GEOMETRY_A_ONLY;
                }

                if (B.contains(A)) {
                    // Mirror of the above case, intersection = B instead
                    return CLIP_GEOMETRY_B_ONLY;
                }

                // Intersection of the two differences cannot be simplified. Note that for
                // this op combination it is not possible to produce kEmpty.
                return CLIP_GEOMETRY_BOTH;
        }

        throw new IllegalStateException();
    }

    // All data describing a geometric modification to the clip
    public static class Element {

        @Nullable Shape mShape;
        final Rect2f mShapeBounds; // owned memory
        final Matrix mLocalToDevice; // owned memory
        int mOp;

        Element() {
            mShapeBounds = new Rect2f();
            mLocalToDevice = new Matrix();
        }

        Element(@Nullable Shape shape, Rect2fc shapeBounds, Matrixc localToDevice, int op) {
            mShape = shape;
            mShapeBounds = new Rect2f(shapeBounds);
            mLocalToDevice = new Matrix(localToDevice);
            mOp = op;
        }

        // do not modify
        public final @Nullable Shape shape() {
            return mShape;
        }

        // local rect
        // do not modify
        public final @NonNull Rect2fc shapeBounds() {
            return mShapeBounds;
        }

        // local to device
        // do not modify
        public final @NonNull Matrixc localToDevice() {
            return mLocalToDevice;
        }

        public final int op() {
            return mOp;
        }

        @Override
        public String toString() {
            return "Element{" +
                    "mShape=" + mShape +
                    ", mShapeBounds=" + mShapeBounds +
                    ", mLocalToDevice=" + mLocalToDevice +
                    ", mOp=" + (mOp == OP_INTERSECT ? "Intersect" : "Difference") +
                    '}';
        }
    }

    // Implements the geometric Element data with logic for containment and bounds testing.
    static final class RawElement extends Element implements TransformedShape {

        // true if this shape is axis-aligned && pixel-aligned Rect (will be snapped),
        // or requested non-AA for other Rect or arbitrary RRect.
        boolean mEffectiveNonAA;

        // cached inverse of mLocalToDevice for contains() optimization
        final Matrix mDeviceToLocal = new Matrix();

        // Device space bounds. These bounds are not snapped to pixels with the assumption that if
        // a relation (intersects, contains, etc.) is true for the bounds it will be true for the
        // rasterization of the coordinates that produced those bounds.
        final Rect2f mInnerBounds = new Rect2f();
        final Rect2f mOuterBounds = new Rect2f();

        // State tracking how this clip element needs to be recorded into the draw context. As the
        // clip stack is applied to additional draws, the clip's Z and usage bounds grow to account
        // for it; its compressed painter's order is selected the first time a draw is affected.
        final Rect2i mUsageBounds = new Rect2i();
        int mPaintersOrder = Draw.NO_INTERSECTION;
        int mMaxDepth = Draw.CLEAR_DEPTH;

        // Elements are invalidated by SaveRecords as the record is updated with new elements that
        // override old geometry. An invalidated element stores the index of the first element of
        // the save record that invalidated it. This makes it easy to undo when the save record is
        // popped from the stack, and is stable as the current save record is modified.
        int mInvalidatedByIndex = -1;

        public RawElement() {
        }

        // actually a rvalue constructor
        public RawElement(@NonNull RawElement e) {
            super(e.mShape, e.mShapeBounds, e.mLocalToDevice, e.mOp);
            mEffectiveNonAA = e.mEffectiveNonAA;
            mDeviceToLocal.set(e.mDeviceToLocal);
            mInnerBounds.set(e.mInnerBounds);
            mOuterBounds.set(e.mOuterBounds);
            mUsageBounds.set(e.mUsageBounds);
            mPaintersOrder = e.mPaintersOrder;
            mMaxDepth = e.mMaxDepth;
            mInvalidatedByIndex = e.mInvalidatedByIndex;

            e.mShape = null;
        }

        // actually a rvalue assignment
        public void set(@NonNull RawElement e) {
            assert this != e;
            mShape = e.mShape;
            mShapeBounds.set(e.mShapeBounds);
            mLocalToDevice.set(e.mLocalToDevice);
            mOp = e.mOp;
            mEffectiveNonAA = e.mEffectiveNonAA;
            mDeviceToLocal.set(e.mDeviceToLocal);
            mInnerBounds.set(e.mInnerBounds);
            mOuterBounds.set(e.mOuterBounds);
            mUsageBounds.set(e.mUsageBounds);
            mPaintersOrder = e.mPaintersOrder;
            mMaxDepth = e.mMaxDepth;
            mInvalidatedByIndex = e.mInvalidatedByIndex;

            e.mShape = null;
        }

        // init and simplify
        public RawElement init(@NonNull Rect2fc deviceBounds,
                               @NonNull Matrixc localToDevice,
                               @NonNull Shape shape,
                               int op, boolean doAA) {
            assert shape instanceof Rect || shape instanceof RRect || shape instanceof Path;
            mShape = shape;
            mLocalToDevice.set(localToDevice);
            mOp = op;
            mEffectiveNonAA = !doAA;
            mUsageBounds.setInfiniteInverted();
            mPaintersOrder = Draw.NO_INTERSECTION;
            mMaxDepth = Draw.CLEAR_DEPTH;
            mInvalidatedByIndex = -1;

            if (!localToDevice.invert(mDeviceToLocal)) {
                // If the transform can't be inverted, it means that two dimensions are collapsed to 0 or
                // 1 dimension, making the device-space geometry effectively empty.
                mShape = null;
                mShapeBounds.setEmpty();
                mDeviceToLocal.setIdentity();
            } else {
                mShape.getBounds(mShapeBounds);
            }

            mInnerBounds.setInfiniteInverted();
            mLocalToDevice.mapRect(mShapeBounds, mOuterBounds);
            mOuterBounds.intersectNoCheck(deviceBounds);

            // Apply axis-aligned transforms to rects and translating transforms to round rects
            // to reduce the number of unique local coordinate systems that are in play.
            if (!mOuterBounds.isEmpty() && mLocalToDevice.isAxisAligned()) {
                if (mShape instanceof Rect) {
                    // The actual geometry can be updated to the device-intersected bounds and we can
                    // know the inner bounds
                    if (!doAA) {
                        // Emulate non-AA by snapping to pixels
                        mOuterBounds.round(mOuterBounds);
                    } else {
                        Rect2f rounded = new Rect2f();
                        mOuterBounds.round(rounded);
                        // If it is equivalent to non AA, then we will use scissor instead of analytic clip
                        mEffectiveNonAA = mOuterBounds.equalsWithTolerance(rounded, DEFAULT_PIXEL_TOLERANCE);
                    }
                    ((Rect) mShape).set(mOuterBounds);
                    mShapeBounds.set(mOuterBounds);
                    mLocalToDevice.setIdentity();
                    mDeviceToLocal.setIdentity();
                    mInnerBounds.set(mOuterBounds);
                } else if (mShape instanceof RRect && mLocalToDevice.isTranslate()) {
                    ((RRect) mShape).offset(mLocalToDevice.getTranslateX(), mLocalToDevice.getTranslateY());
                    mShape.getBounds(mShapeBounds);
                    mLocalToDevice.setIdentity();
                    mDeviceToLocal.setIdentity();
                    // Refresh outer bounds to match the transformed round rect just in case
                    mOuterBounds.set(mShapeBounds);
                    mOuterBounds.intersectNoCheck(deviceBounds);
                    ((RRect) mShape).getInnerBounds(mInnerBounds);
                    mInnerBounds.intersectNoCheck(mOuterBounds); // ensure on-screen
                }
            }

            if (mOuterBounds.isEmpty()) {
                // Either was already an empty shape or a non-empty shape is offscreen, so treat it as such.
                mShape = null;
                mShapeBounds.setEmpty();
                mInnerBounds.setInfiniteInverted();
            }

            // Post-conditions on inner and outer bounds
            assert (mShape == null) == mShapeBounds.isEmpty();
            assert (mShape == null || deviceBounds.contains(mOuterBounds));
            assert validate();

            return this;
        }

        public boolean hasPendingDraw() {
            return mPaintersOrder != Draw.NO_INTERSECTION;
        }

        // As new elements are pushed on to the stack, they may make older elements redundant.
        // The old elements are marked invalid so they are skipped during clip application, but may
        // become active again when a save record is restored.
        public boolean isInvalid() {
            return mInvalidatedByIndex >= 0;
        }

        public void markInvalid(@NonNull SaveRecord current) {
            assert (!isInvalid());
            mInvalidatedByIndex = current.firstActiveElementIndex();
            // NOTE: We don't draw the accumulated clip usage when the element is marked invalid. Some
            // invalidated elements are part of earlier save records so can become re-active after a restore
            // in which case they should continue to accumulate. Invalidated elements that are part of the
            // active save record are removed at the end of the stack modification, which is when they are
            // explicitly drawn.
        }

        public void restoreValid(@NonNull SaveRecord current) {
            if (current.firstActiveElementIndex() < mInvalidatedByIndex) {
                mInvalidatedByIndex = -1;
            }
        }

        public boolean combine(RawElement other, SaveRecord current) {
            // Don't combine elements that have collected draw usage, since that changes their geometry.
            if (hasPendingDraw() || other.hasPendingDraw()) {
                return false;
            }
            // To reduce the number of possibilities, only consider intersect+intersect. Difference and
            // mixed op cases could be analyzed to simplify one of the shapes, but that is a rare
            // occurrence and the math is much more complicated.
            if (mOp != OP_INTERSECT || other.mOp != OP_INTERSECT) {
                return false;
            }
            // There will be no flood-fill because our ops are intersect, and the shapes are
            // logically filled, while clip masks are inverse-filled.
            // Only DrawShape can have flood-fill, RawElement's flood-fill will be early-out.
            // If either this.shape or other.shape is empty, it will be also early-out and
            // this method will not be called.
            assert mShape != null && other.mShape != null;
            if (!(mShape instanceof Rect) || !(other.mShape instanceof Rect)) {
                // Otherwise, we will consider the case of two rectangles.
                return false;
            }

            // At the moment, only rect+rect are supported.
            boolean shapeUpdated = false;
            // We only check if the two transforms are mathematically equal. This is because for axis-aligned
            // transforms, they have already been applied to the rect shapes and simplified to the identity matrix.
            // For non-axis-aligned transform, LocalToOther (i.e., other.DeviceToLocal * this.LocalToDevice)
            // is unlikely to be axis-aligned (due to FP errors).
            if (mLocalToDevice.equals(other.mLocalToDevice)) {
                Rect2f otherRect = new Rect2f(other.mShapeBounds);
                otherRect.intersectNoCheck(mShapeBounds);
                // Make sure that the intersected shape does not become subpixel in size, since drawing a
                // subpixel/hairline shape produces a different result than something that's clipped.
                float localAARadius = mLocalToDevice.localAARadius(otherRect);
                if (!Float.isFinite(localAARadius) ||
                        otherRect.width() <= localAARadius || otherRect.height() <= localAARadius) {
                    return false;
                }
                ((Rect) mShape).set(otherRect);
                mShape.getBounds(mShapeBounds);
                mEffectiveNonAA &= other.mEffectiveNonAA;
                shapeUpdated = true;
            }

            if (shapeUpdated) {
                // This logic works under the assumption that both combined elements were intersect.
                assert (mOp == OP_INTERSECT && other.mOp == OP_INTERSECT);
                mOuterBounds.intersectNoCheck(other.mOuterBounds);
                mInnerBounds.intersectNoCheck(other.mInnerBounds);
                // Inner bounds can become empty, but outer bounds should not be able to.
                assert !mOuterBounds.isEmpty();
                assert validate();
                return true;
            } else {
                return false;
            }
        }

        // 'added' represents a new op added to the element stack. Its combination with this element
        // can result in a number of possibilities:
        //  1. The entire clip is empty (signaled by both this and 'added' being invalidated).
        //  2. The 'added' op supercedes this element (this element is invalidated).
        //  3. This op supercedes the 'added' element (the added element is marked invalidated).
        //  4. Their combination can be represented by a single new op (in which case this
        //     element should be invalidated, and the combined shape stored in 'added').
        //  5. Or both elements remain needed to describe the clip (both are valid and unchanged).
        //
        // The calling element will only modify its invalidation index since it could belong
        // to part of the inactive stack (that might be restored later). All merged state/geometry
        // is handled by modifying 'added'.
        public void updateForElement(RawElement added, SaveRecord current) {
            if (isInvalid()) {
                // Already doesn't do anything, so skip this element
                return;
            }

            // 'A' refers to this element, 'B' refers to 'added'.
            switch (getClipGeometry(this, added)) {
                case CLIP_GEOMETRY_EMPTY:
                    // Mark both elements as invalid to signal that the clip is fully empty
                    markInvalid(current);
                    added.markInvalid(current);
                    break;

                case CLIP_GEOMETRY_A_ONLY:
                    // This element already clips more than 'added', so mark 'added' is invalid to skip it
                    added.markInvalid(current);
                    break;

                case CLIP_GEOMETRY_B_ONLY:
                    // 'added' clips more than this element, so mark this as invalid
                    markInvalid(current);
                    break;

                case CLIP_GEOMETRY_BOTH:
                    // Else the bounds checks think we need to keep both, but depending on the combination
                    // of the ops and shape kinds, we may be able to do better.
                    if (added.combine(this, current)) {
                        // 'added' now fully represents the combination of the two elements
                        markInvalid(current);
                    }
                    break;
            }
        }

        // Updates usage tracking to incorporate the bounds and Z value for the new draw call.
        // If this element hasn't affected any prior draws, it will use the bounds manager to
        // assign itself a compressed painters order for later rendering.
        //
        // This method assumes that this element affects the draw in a complex way, such that
        // calling `testForDraw()` on the same draw would return `DrawInfluence::kIntersect`. It is
        // assumed that `testForDraw()` was called beforehand to ensure that this is the case.
        //
        // Assuming that this element does not clip out the draw, returns the painters order the
        // draw must sort after.
        public int updateForDraw(BoundsManager boundsManager,
                                 int deviceWidth, int deviceHeight,
                                 Rect2fc drawBounds,
                                 int drawDepth) {
            assert (!isInvalid());
            assert (!drawBounds.isEmpty());

            // Always record snapped draw bounds to avoid scissor thrashing since these bounds will be used
            // to determine the scissor applied to the depth-only draw for the clip element.
            Rect2i snappedDrawBounds = snapScissor(drawBounds, deviceWidth, deviceHeight);

            if (!hasPendingDraw()) {
                // No usage yet so we need an order that we will use when drawing to just the depth
                // attachment. It is sufficient to use the next CompressedPaintersOrder after the
                // most recent draw under this clip's outer bounds. It is necessary to use the
                // entire clip's outer bounds because the order has to be determined before the
                // final usage bounds are known and a subsequent draw could require a completely
                // different portion of the clip than this triggering draw.
                //
                // Lazily determining the order has several benefits to computing it when the clip
                // element was first created:
                //  - Elements that are invalidated by nested clips before draws are made do not
                //    waste time in the BoundsManager.
                //  - Elements that never actually modify a draw (e.g. a defensive clip) do not
                //    waste time in the BoundsManager.
                //  - A draw that triggers clip usage on multiple elements will more likely assign
                //    the same order to those elements, meaning their depth-only draws are more
                //    likely to batch in the final DrawPass.
                //
                // However, it does mean that clip elements can have the same order as each other,
                // or as later draws (e.g. after the clip has been popped off the stack). Any
                // overlap between clips or draws is addressed when the clip is drawn by selecting
                // an appropriate DisjointStencilIndex value. Stencil-aside, this order assignment
                // logic, max Z tracking, and the depth test during rasterization are able to
                // resolve everything correctly even if clips have the same order value.
                // See go/clip-stack-order for a detailed analysis of why this works.
                Rect2i snappedOuterBounds = snapScissor(mOuterBounds, deviceWidth, deviceHeight);
                mPaintersOrder = boundsManager.getMostRecentDraw(snappedOuterBounds) + 1;
                mUsageBounds.set(snappedDrawBounds);
                mMaxDepth = drawDepth;
            } else {
                // Earlier draws have already used this element so we cannot change where the
                // depth-only draw will be sorted to, but we need to ensure we cover the new draw's
                // bounds and use a Z value that will clip out its pixels as appropriate.
                mUsageBounds.joinNoCheck(snappedDrawBounds);
                mMaxDepth = Math.max(mMaxDepth, drawDepth);
            }

            return mPaintersOrder;
        }

        // Record a depth-only draw to the given device, restricted to the portion of the clip that
        // is actually required based on prior recorded draws. Resets usage tracking for subsequent
        // passes.
        public void drawClip(GraniteDevice device) {
            assert validate();

            // Skip elements that have not affected any draws
            if (!hasPendingDraw()) {
                assert (mUsageBounds.isEmpty());
                return;
            }

            assert (!mUsageBounds.isEmpty());
            // For clip draws, the usage bounds is the scissor.
            var scissor = new Rect2i(mUsageBounds);

            Rect2i snappedOuterBounds = snapScissor(mOuterBounds, device.getWidth(), device.getHeight());
            scissor.intersectNoCheck(snappedOuterBounds);
            // But if the overlap is sufficiently large, just rasterize out to the snapped bounds instead of
            // adding a tight scissor. A factor of 1/2 is used because that corresponds to the area
            // change caused by a 45-degree rotation.
            if (snappedOuterBounds.width() * snappedOuterBounds.height() / 2 < scissor.width() * scissor.height()) { // no SINT32 overflow
                scissor.set(snappedOuterBounds);
            }

            var drawBounds = new Rect2f(scissor);
            if (mOp == OP_DIFFERENCE) {
                drawBounds.intersectNoCheck(mOuterBounds);
            }
            if (!drawBounds.isEmpty()) {
                // An element's clip op is encoded in the shape's fill type. Inverse fills are intersect ops
                // and regular fills are difference ops. This means fShape is already in the right state to
                // draw directly.
                boolean inverseFill = mOp == OP_INTERSECT;
                assert mShape != null;
                // Rect can be modified so make a copy to record draw
                //TODO always use shape.clone() once supported
                Draw draw = new Draw(mLocalToDevice, mShape instanceof Rect ? new Rect(mShapeBounds) : mShape, inverseFill);
                draw.mDrawBounds = drawBounds;
                draw.mTransformedShapeBounds = new Rect2f(mOuterBounds);
                draw.mScissorRect = scissor;
                // Although we are recording this clip draw after all the draws it affects, 'mPaintersOrder' was
                // determined at the first usage, so after sorting by DrawOrder the clip draw will be in the
                // right place. Unlike regular draws that use their own "Z", by writing (1 + max depth this clip
                // affects), it will cause those draws to fail either GREATER and GEQUAL depth tests where
                // they need to be clipped.
                draw.mDepth = mMaxDepth + 1;
                draw.mPaintOrder = mPaintersOrder;
                device.drawClipShape(draw);
            }

            // After the clip shape is drawn, reset its state. If the clip element is being popped off the
            // stack or overwritten because a new clip invalidated it, this won't matter. But if the clips
            // were drawn because the Device had to flush pending work while the clip stack was not empty,
            // subsequent draws will still need to be clipped to the elements. In this case, the usage
            // accumulation process will begin again and automatically use the Device's post-flush Z values
            // and BoundsManager state.
            mUsageBounds.setInfiniteInverted();
            mPaintersOrder = Draw.NO_INTERSECTION;
            mMaxDepth = Draw.CLEAR_DEPTH;
        }

        public Rect2fc innerBounds() {
            return mInnerBounds;
        }

        @Override
        public Rect2fc outerBounds() {
            return mOuterBounds;
        }

        @Override
        public boolean contains(TransformedShape o) {
            if (mInnerBounds.containsNoCheck(o.outerBounds())) {
                return true;
            }
            // Skip more expensive contains() checks if configured not to, or if the extent of 'o' exceeds
            // this shape's outer bounds. When that happens there must be some part of 'o' that cannot be
            // contained in this shape.
            if (!mOuterBounds.containsNoCheck(o.outerBounds())) {
                return false;
            }
            if (mShape == null) {
                return false;
            }

            if (mLocalToDevice.equals(o.localToDevice())) {
                if (o instanceof RawElement other && mShape instanceof RRect && mShape.equals(other.mShape)) {
                    // same rrect
                    return true;
                }
                // A and B are in the same coordinate space, so don't bother mapping
                return mShape.contains(o.shapeBounds());
            }

            if (mLocalToDevice.isAxisAligned() && o.localToDevice().isAxisAligned()) {
                // Optimize the common case of draws (B, with identity matrix) and axis-aligned shapes,
                // instead of checking the four corners separately.
                Rect2f localBounds = new Rect2f(o.shapeBounds());
                o.localToDevice().mapRect(localBounds);
                mDeviceToLocal.mapRect(localBounds);
                return mShape.contains(localBounds);
            }

            return false;
        }

        private int clipType() {
            if (mShape == null) {
                return STATE_EMPTY;
            } else if (mShape instanceof Rect) {
                return mOp == OP_INTERSECT && mLocalToDevice.isIdentity()
                        ? STATE_DEVICE_RECT : STATE_COMPLEX;
            }
            return STATE_COMPLEX;
        }

        private boolean validate() {
            assert ((mShape == null) == mShapeBounds.isEmpty());
            assert ((mShape == null || !mOuterBounds.isEmpty()) &&
                    (mInnerBounds.isEmpty() || mOuterBounds.contains(mInnerBounds)));
            assert (!hasPendingDraw() || !mUsageBounds.isEmpty());
            return true;
        }
    }

    static void subtract(Rect2fc a, Rect2fc b, Rect2f out, boolean exact) {
        Rect2f diff = new Rect2f();
        if (Rect2f.subtract(a, b, diff) || !exact) {
            // Either A-B is exactly the rectangle stored in diff, or we don't need an exact answer
            // and can settle for the subrect of A excluded from B (which is also 'diff')
            out.set(diff);
        } else {
            // For our purposes, we want the original A when A-B cannot be exactly represented
            out.set(a);
        }
    }

    static @NonNull Rect2i snapScissor(@NonNull Rect2fc a, int deviceWidth, int deviceHeight) {
        // Snapping to 4 pixel boundaries seems to give a good tradeoff between rasterizing slightly
        // more (but being clipped by the depth test), vs. setting a tight scissor that forces a state
        // change.
        // NOTE: This rounds out to the *next* multiple of 4, so that if the input rectangle happens to
        // land on a multiple of 4 we still create some padding to avoid scissoring just AA outsets.
        // padding = 3, 1 <= pad < 4, use fast math
        int L = MathUtil.floor((a.left() - 3f) * 0.25f) << 2;
        int T = MathUtil.floor((a.top() - 3f) * 0.25f) << 2;
        int R = MathUtil.ceil((a.right() + 3f) * 0.25f) << 2;
        int B = MathUtil.ceil((a.bottom() + 3f) * 0.25f) << 2;

        return new Rect2i(
                Math.max(L, 0),
                Math.max(T, 0),
                Math.min(R, deviceWidth),
                Math.min(B, deviceHeight)
        );
    }

    static final class SaveRecord implements TransformedShape {

        // Inner bounds is always contained in outer bounds, or it is empty. All bounds will be
        // contained in the device bounds.
        private final Rect2f mInnerBounds; // Inside is full coverage (stack op == intersect) or 0 cov (diff)
        private final Rect2f mOuterBounds; // Outside is 0 coverage (op == intersect) or full cov (diff)

        final int mStartingElementIndex;  // First element owned by this save record
        int mOldestValidIndex; // Index of oldest element that remains valid for this record

        // Number of save() calls without modifications (yet)
        private int mDeferredSaveCount;

        private int mState;
        private int mStackOp;

        SaveRecord(Rect2fc deviceBounds) {
            mInnerBounds = new Rect2f(deviceBounds);
            mOuterBounds = new Rect2f(deviceBounds);
            mStartingElementIndex = 0;
            mOldestValidIndex = 0;
            mState = STATE_WIDE_OPEN;
            mStackOp = OP_INTERSECT;
        }

        SaveRecord(@NonNull SaveRecord prior,
                   int startingElementIndex) {
            mInnerBounds = new Rect2f(prior.mInnerBounds);
            mOuterBounds = new Rect2f(prior.mOuterBounds);
            mStartingElementIndex = startingElementIndex;
            mOldestValidIndex = prior.mOldestValidIndex;
            mState = prior.mState;
            mStackOp = prior.mStackOp;
            // If the prior record never needed a mask, this one will insert into the same index
            // (that's okay since we'll remove it when this record is popped off the stack).
            assert (startingElementIndex >= prior.mStartingElementIndex);
        }

        @Override
        public int op() {
            return mStackOp;
        }

        @Override
        public Rect2fc shapeBounds() {
            return mOuterBounds;
        }

        @Override
        public Matrixc localToDevice() {
            return Matrix.identity();
        }

        @Override
        public Rect2fc outerBounds() {
            return mOuterBounds;
        }

        public Rect2fc innerBounds() {
            return mInnerBounds;
        }

        public int state() {
            return mState;
        }

        @Override
        public boolean contains(TransformedShape o) {
            assert o instanceof RawElement || o instanceof DrawShape;
            return mInnerBounds.containsNoCheck(o.outerBounds());
        }

        public int firstActiveElementIndex() {
            return mStartingElementIndex;
        }

        public int oldestElementIndex() {
            return mOldestValidIndex;
        }

        public boolean canBeUpdated() {
            return mDeferredSaveCount == 0;
        }

        // Deferred save manipulation
        public void pushSave() {
            assert (mDeferredSaveCount >= 0);
            mDeferredSaveCount++;
        }

        // Returns true if the record should stay alive. False means the ClipStack must delete it
        public boolean popSave() {
            mDeferredSaveCount--;
            assert (mDeferredSaveCount >= -1);
            return mDeferredSaveCount >= 0;
        }

        // Return true if the element was added to 'elements', or otherwise affected the save record
        // (e.g. turned it empty).
        public boolean addElement(RawElement toAdd, ObjectArrayList<RawElement> elements, GraniteDevice device) {
            // Validity check the element's state first; if the shape class isn't empty, the outer bounds
            // shouldn't be empty; if the inner bounds are not empty, they must be contained in outer.
            assert (toAdd.validate());
            // And we shouldn't be adding an element if we have a deferred save
            assert (canBeUpdated());

            if (mState == STATE_EMPTY) {
                // The clip is already empty, and we only shrink, so there's no need to record this element.
                return false;
            } else if (toAdd.shape() == null) {
                // An empty difference op should have been detected earlier, since it's a no-op
                assert (toAdd.op() == OP_INTERSECT);
                mState = STATE_EMPTY;
                removeElements(elements, device);
                return true;
            }

            // In this invocation, 'A' refers to the existing stack's bounds and 'B' refers to the new
            // element.
            switch (getClipGeometry(this, toAdd)) {
                case CLIP_GEOMETRY_EMPTY:
                    // The combination results in an empty clip
                    mState = STATE_EMPTY;
                    removeElements(elements, device);
                    return true;

                case CLIP_GEOMETRY_A_ONLY:
                    // The combination would not be any different than the existing clip
                    return false;

                case CLIP_GEOMETRY_B_ONLY:
                    // The combination would invalidate the entire existing stack and can be replaced with
                    // just the new element.
                    replaceWithElement(toAdd, elements, device);
                    return true;

                case CLIP_GEOMETRY_BOTH:
                    // The new element combines in a complex manner, so update the stack's bounds based on
                    // the combination of its and the new element's ops (handled below)
                    break;
            }

            if (mState == STATE_WIDE_OPEN) {
                // When the stack was wide open and the clip effect was kBoth, the "complex" manner is
                // simply to keep the element and update the stack bounds to be the element's intersected
                // with the device.
                replaceWithElement(toAdd, elements, device);
                return true;
            }

            // Some form of actual clip element(s) to combine with.
            if (mStackOp == OP_INTERSECT) {
                if (toAdd.op() == OP_INTERSECT) {
                    // Intersect (stack) + Intersect (toAdd)
                    //  - Bounds updates is simply the paired intersections of outer and inner.
                    mOuterBounds.intersectNoCheck(toAdd.outerBounds());
                    mInnerBounds.intersectNoCheck(toAdd.innerBounds());
                    // Outer should not have become empty, but is allowed to if there's no intersection.
                    assert !mOuterBounds.isEmpty();
                } else {
                    // Intersect (stack) + Difference (toAdd)
                    //  - Shrink the stack's outer bounds if the difference op's inner bounds completely
                    //    cuts off an edge.
                    //  - Shrink the stack's inner bounds to completely exclude the op's outer bounds.
                    subtract(mOuterBounds, toAdd.innerBounds(), mOuterBounds, /* exact */ true);
                    subtract(mInnerBounds, toAdd.outerBounds(), mInnerBounds, /* exact */ false);
                }
            } else {
                if (toAdd.op() == OP_INTERSECT) {
                    // Difference (stack) + Intersect (toAdd)
                    //  - Bounds updates are just the mirror of Intersect(stack) + Difference(toAdd)
                    Rect2f oldOuter = new Rect2f(mOuterBounds);
                    subtract(toAdd.outerBounds(), mInnerBounds, mOuterBounds, /* exact */ true);
                    subtract(toAdd.innerBounds(), oldOuter, mInnerBounds,     /* exact */ false);
                } else {
                    // Difference (stack) + Difference (toAdd)
                    //  - The updated outer bounds is the union of outer bounds and the inner becomes the
                    //    largest of the two possible inner bounds
                    mOuterBounds.joinNoCheck(toAdd.outerBounds());
                    if (toAdd.innerBounds().width() * toAdd.innerBounds().height() >
                            mInnerBounds.width() * mInnerBounds.height()) {
                        mInnerBounds.set(toAdd.innerBounds());
                    }
                }
            }

            // If we get here, we're keeping the new element and the stack's bounds have been updated.
            // We ought to have caught the cases where the stack bounds resemble an empty or wide open
            // clip, so assert that's the case.
            assert (!mOuterBounds.isEmpty() &&
                    (mInnerBounds.isEmpty() || mOuterBounds.contains(mInnerBounds)));

            return appendElement(toAdd, elements, device);
        }

        private boolean appendElement(RawElement toAdd, ObjectArrayList<RawElement> elements, GraniteDevice device) {
            // Update past elements to account for the new element
            int i = elements.size() - 1;

            // After the loop, elements between [max(youngestValid, startingIndex)+1, count-1] can be
            // removed from the stack (these are the active elements that have been invalidated by the
            // newest element; since it's the active part of the stack, no restore() can bring them back).
            int youngestValid = mStartingElementIndex - 1;
            // After the loop, elements between [0, oldestValid-1] are all invalid. The value of oldestValid
            // becomes the save record's new fLastValidIndex value.
            int oldestValid = elements.size();
            // After the loop, this is the earliest active element that was invalidated. It may be
            // older in the stack than earliestValid, so cannot be popped off, but can be used to store
            // the new element instead of allocating more.
            RawElement oldestActiveInvalid = null;
            int oldestActiveInvalidIndex = elements.size();

            while (i >= 0) {
                RawElement existing = elements.get(i);
                if (i < mOldestValidIndex) {
                    break;
                }
                // We don't need to pass the actual index that toAdd will be saved to; just the minimum
                // index of this save record, since that will result in the same restoration behavior later.
                existing.updateForElement(toAdd, this);

                if (toAdd.isInvalid()) {
                    if (existing.isInvalid()) {
                        // Both new and old invalid implies the entire clip becomes empty
                        mState = STATE_EMPTY;
                        return true;
                    } else {
                        // The new element doesn't change the clip beyond what the old element already does
                        return false;
                    }
                } else if (existing.isInvalid()) {
                    // The new element cancels out the old element. The new element may have been modified
                    // to account for the old element's geometry.
                    if (i >= mStartingElementIndex) {
                        // Still active, so the invalidated index could be used to store the new element
                        oldestActiveInvalid = existing;
                        oldestActiveInvalidIndex = i;
                    }
                } else {
                    // Keep both new and old elements
                    oldestValid = i;
                    if (i > youngestValid) {
                        youngestValid = i;
                    }
                }

                --i;
            }

            // Post-iteration validity check
            assert (oldestValid == elements.size() ||
                    (oldestValid >= mOldestValidIndex && oldestValid < elements.size()));
            assert (youngestValid == mStartingElementIndex - 1 ||
                    (youngestValid >= mStartingElementIndex && youngestValid < elements.size()));
            assert (oldestActiveInvalid == null || (oldestActiveInvalidIndex >= mStartingElementIndex &&
                    oldestActiveInvalidIndex < elements.size()));

            // Update final state
            assert (oldestValid >= mOldestValidIndex);
            mOldestValidIndex = Math.min(oldestValid, oldestActiveInvalidIndex);
            mState = oldestValid == elements.size() ? toAdd.clipType() : STATE_COMPLEX;
            if (mStackOp == OP_DIFFERENCE && toAdd.op() == OP_INTERSECT) {
                // The stack remains in difference mode only as long as all elements are difference
                mStackOp = OP_INTERSECT;
            }

            int targetCount = youngestValid + 1;
            if (oldestActiveInvalid == null || oldestActiveInvalidIndex >= targetCount) {
                // toAdd will be stored right after youngestValid
                targetCount++;
                oldestActiveInvalid = null;
            }
            while (elements.size() > targetCount) {
                assert (oldestActiveInvalid != elements.top()); // shouldn't delete what we'll reuse
                elements.pop().drawClip(device);
            }
            if (oldestActiveInvalid != null) {
                oldestActiveInvalid.drawClip(device);
                oldestActiveInvalid.set(toAdd);
            } else if (elements.size() < targetCount) {
                elements.push(new RawElement(toAdd));
            } else {
                elements.top().drawClip(device);
                elements.top().set(toAdd);
            }

            return true;
        }

        private void replaceWithElement(RawElement toAdd, ObjectArrayList<RawElement> elements, GraniteDevice device) {
            // The aggregate state of the save record mirrors the element
            mInnerBounds.set(toAdd.mInnerBounds);
            mOuterBounds.set(toAdd.mOuterBounds);

            mStackOp = toAdd.op();
            mState = toAdd.clipType();

            // All prior active element can be removed from the stack: [startingIndex, count - 1]
            int targetCount = mStartingElementIndex + 1;
            while (elements.size() > targetCount) {
                elements.pop().drawClip(device);
            }
            if (elements.size() < targetCount) {
                elements.push(new RawElement(toAdd));
            } else {
                elements.top().drawClip(device);
                elements.top().set(toAdd);
            }

            assert (elements.size() == mStartingElementIndex + 1);

            // This invalidates all older elements that are owned by save records lower in the clip stack.
            mOldestValidIndex = mStartingElementIndex;
        }

        public void removeElements(ObjectArrayList<RawElement> elements,
                                   GraniteDevice device) {
            while (elements.size() > mStartingElementIndex) {
                // Since the element is being deleted now, it won't be in the ClipStack when the Device
                // calls recordDeferredClipDraws(). Record the clip's draw now (if it needs it).
                elements.pop().drawClip(device);
            }
        }

        public void restoreElements(ObjectArrayList<RawElement> elements) {
            // Presumably this SaveRecord is the new top of the stack, and so it owns the elements
            // from its starting index to restoreCount - 1. Elements from the old save record have
            // been destroyed already, so their indices would have been >= restoreCount, and any
            // still-present element can be un-invalidated based on that.
            int i = elements.size() - 1;
            while (i >= 0) {
                RawElement e = elements.get(i);
                if (i < mOldestValidIndex) {
                    break;
                }
                e.restoreValid(this);
                --i;
            }
        }

        // return value must be immutable, deviceBounds is immutable
        public Rect2ic scissor(Rect2ic deviceBounds, Rect2fc drawBounds) {
            // This should only be called when the clip stack actually has something non-trivial to evaluate
            // It is effectively a reduced version of Simplify() dealing only with device-space bounds and
            // returning the intersection results.
            assert (mState != STATE_EMPTY && mState != STATE_WIDE_OPEN);
            assert (deviceBounds.contains(drawBounds)); // This should have already been handled.
            if (mStackOp == OP_INTERSECT) {
                // kIntersect nominally uses the save record's outer bounds as the scissor. However, if the
                // draw is contained entirely within those bounds, it doesn't have any visual effect so
                // switch to using the device bounds as the canonical scissor to minimize state changes.
                if (mOuterBounds.contains(drawBounds)) {
                    // device bounds never change
                    return deviceBounds;
                } else {
                    // This automatically detects the case where the draw does not intersect the clip.
                    var res = new Rect2i();
                    mOuterBounds.roundOut(res);
                    return res;
                }
            } else {
                // kDifference nominally uses the draw's bounds minus the save record's inner bounds as the
                // scissor. However, if the draw doesn't intersect the clip at all then it doesn't have any
                // visual effect and we can switch to the device bounds as the canonical scissor.
                if (!mOuterBounds.intersects(drawBounds)) {
                    return deviceBounds;
                } else {
                    // This automatically detects the case where the draw is contained in inner bounds and
                    // would be entirely clipped out.
                    var diff = new Rect2f();
                    var res = new Rect2i();
                    if (Rect2f.subtract(drawBounds, mInnerBounds, diff)) {
                        diff.roundOut(res);
                    } else {
                        drawBounds.roundOut(res);
                    }
                    return res;
                }
            }
        }
    }

    static final class DrawShape implements TransformedShape {

        final Rect mRectStorage = new Rect();

        Matrixc mLocalToDevice;
        Rect mShape;
        boolean mInverted;

        final Rect2f mShapeBounds = new Rect2f();

        // these three bounds are not valid until applyStyle is called
        final Rect2f mTransformedShapeBounds = new Rect2f();
        final Rect2f mOuterBounds = new Rect2f();
        final Rect2f mInnerBounds = new Rect2f();

        final Rect2i mScissor = new Rect2i();

        /**
         * This sets to true if the shape matches the original geometry and is simple
         * (can perform conservative bounds check and can have inner bounds).
         */
        boolean mShapeCanBeModified;
        boolean mShapeWasModified;

        public DrawShape init(Matrixc localToDevice,
                         Bounded geometry,
                         boolean inverted) {
            mLocalToDevice = localToDevice;
            if (geometry == null) {
                // flood fill
                assert inverted;
                mShapeBounds.setEmpty();
                mShape = null;
                mInverted = true;
                mShapeCanBeModified = true;
            } else {
                geometry.getBounds(mShapeBounds);
                mRectStorage.set(mShapeBounds);
                mShape = mRectStorage;
                mInverted = inverted;
                // In these two cases, shape matches the geometry and can be AA arbitrarily.
                // Sometimes a rectangle can also be represented using Box or RRect,
                // but the shape does not match the geometry in that case (degenerate or stroke).
                mShapeCanBeModified = !inverted && (geometry instanceof Rect ||
                        geometry instanceof EdgeAAQuad quad && quad.isRect() && quad.edgeFlags() == EdgeAAQuad.kAll
                );
            }
            mScissor.setInfinite();
            mShapeWasModified = false;

            return this;
        }

        public boolean applyStyle(@NonNull Draw draw, @NonNull Rect2fc deviceBounds) {
            float origW = mShapeBounds.width();
            float origH = mShapeBounds.height();
            if (!Float.isFinite(origW) || !Float.isFinite(origH)) {
                // Discard all non-finite geometry as if it were clipped out
                return false;
            }

            if (!mInverted && (origW == 0f || origH == 0f)) {
                if (!draw.isStroke() || (draw.mStrokeCap == Paint.CAP_BUTT && origW == 0f && origH == 0f)) {
                    //TODO we should validate that Canvas performs all checks
                    assert false;
                    return false;
                }
            }

            // Anti-aliasing makes shapes larger than their original coordinates, but we only care about
            // that for local clip checks in certain cases (see above).
            // NOTE: After this if-else block, `transformedShapeBounds` will be in device space.
            float localAAOutset = mLocalToDevice.localAARadius(mShapeBounds);
            draw.mAARadius = localAAOutset;
            if (!Float.isFinite(localAAOutset)) {
                mTransformedShapeBounds.set(deviceBounds);
                mRectStorage.set(deviceBounds);
                mShape = mRectStorage;
                mLocalToDevice = Matrix.identity();
                mShapeCanBeModified = false;
            } else {
                mTransformedShapeBounds.set(mShapeBounds);
                float localOutset = 0.0f;
                if (draw.mHalfWidth > 0f) { // Wide stroke
                    localOutset = draw.getInflationRadius();
                }

                if (draw.mHalfWidth == 0.0f || // Hairline
                        (draw.mHalfWidth > 0.0f && draw.mHalfWidth * 2 < localAAOutset) ||
                        (draw.mHalfWidth < 0.0f && !mInverted && (origW < localOutset || origH < localOutset))) {
                    // The geometry is a hairline or projects to a subpixel shape, so rendering will not
                    // follow the typical 1/2px outset anti-aliasing that is compatible with clipping.
                    // In this case, apply the local AA radius to the shape to have a conservative clip
                    // query while preserving the oriented bounding box.
                    localOutset += localAAOutset;
                }

                if (localOutset > 0.0f) {
                    // Propagate style and AA outset into styledShape so clip queries reflect style.
                    mTransformedShapeBounds.outset(localOutset, localOutset);
                    mShape.set(mTransformedShapeBounds); // it's still local at this point
                    // Due to stroke or subpixel, shape no longer matches geometry
                    mShapeCanBeModified = false;
                }

                mLocalToDevice.mapRect(mTransformedShapeBounds);
            }

            mOuterBounds.set(mTransformedShapeBounds);

            if (mShapeCanBeModified && mLocalToDevice.isAxisAligned() && mShape != null) {
                // the shape is rect, so it has inner bounds equal to its outer bounds
                mInnerBounds.set(mOuterBounds);
            } else {
                // otherwise it's a flood fill, but should have empty bounds anyway,
                // or we either don't need the inner bounds, or the inner bounds can't be computed cheaply,
                // due to draw's shape or a non-axis-aligned transform
                mInnerBounds.setInfiniteInverted();
            }

            return true;
        }

        public void applyScissor(@NonNull Rect2ic scissor) {
            mScissor.intersectNoCheck(scissor); // For first call, mScissor is infinite so this is assignment
            mOuterBounds.intersectNoCheck(scissor);
            mInnerBounds.intersectNoCheck(scissor);
        }

        public void resetToFloodFill() {
            // null shape meaning it's already flood fill
            if (mShapeCanBeModified && mShape != null) {
                mShape = null;
                mInverted = true;
                mShapeBounds.setEmpty();
                mOuterBounds.setInfiniteInverted();
                mInnerBounds.setInfiniteInverted();
                mShapeWasModified = true;
                // later we will update AA radius, renderer, and transformed shape bounds
            }
        }

        public int toClip(Draw draw) {
            if (mShapeWasModified) {
                assert mShapeCanBeModified;

                draw.mGeometry = mShape;
                draw.mInverseFill = mInverted;

                // Reconstruct new transformedShapeBounds and outer bounds
                mLocalToDevice.mapRect(mShapeBounds, mTransformedShapeBounds);
                mOuterBounds.set(mTransformedShapeBounds);
                mOuterBounds.intersectNoCheck(mScissor);
                draw.mAARadius = mLocalToDevice.localAARadius(mShapeBounds);
            }

            Rect2f drawBounds = mInverted ? new Rect2f(mScissor) : new Rect2f(mOuterBounds);
            assert drawBounds.isEmpty() || mScissor.contains(drawBounds);
            assert !mScissor.isEmpty() || drawBounds.isEmpty();
            if (drawBounds.isEmpty()) {
                return CLIPPED_OUT;
            }
            draw.mDrawBounds = drawBounds;
            draw.mTransformedShapeBounds = new Rect2f(mTransformedShapeBounds);
            draw.mScissorRect = new Rect2i(mScissor);
            return mShapeWasModified ? CLIPPED_GEOMETRICALLY : CLIPPED;
        }

        @Override
        public int op() {
            // A regular draw is a transformed shape that "intersects" the clip. An inverse-filled draw
            // is equivalent to "difference".
            return mInverted ? OP_DIFFERENCE : OP_INTERSECT;
        }

        @Override
        public Rect2fc shapeBounds() {
            return mShapeBounds;
        }

        @Override
        public Matrixc localToDevice() {
            return mLocalToDevice;
        }

        @Override
        public Rect2fc outerBounds() {
            return mOuterBounds;
        }

        @Override
        public boolean contains(TransformedShape o) {
            assert o instanceof SaveRecord || o instanceof RawElement;
            // We provide an inner bounds for rect shapes because it's cheap, and
            // we can geometrically intersect clip elements with the draw geometry
            if (mInnerBounds.containsNoCheck(o.outerBounds())) {
                return true;
            }
            // Skip more expensive contains() checks if configured not to, or if the extent of 'o' exceeds
            // this shape's outer bounds. When that happens there must be some part of 'o' that cannot be
            // contained in this shape.
            if (!mShapeCanBeModified || !mOuterBounds.containsNoCheck(o.outerBounds())) {
                return false;
            }
            if (mShape == null) {
                // non-inverted flood fill can't contain anything
                return false;
            }

            if (mLocalToDevice.equals(o.localToDevice())) {
                return mShape.contains(o.shapeBounds());
            } else if (mLocalToDevice.isScaleTranslate() &&
                    o.localToDevice().isAxisAligned()) {
                // Optimize the common case where o's bounds can be mapped tightly into this coordinate
                // space and then tested against our shape.
                Rect2f bounds = new Rect2f(o.shapeBounds());
                o.localToDevice().mapRect(bounds);
                inverseMapRect(mLocalToDevice, bounds, false, null);
                return mShape.contains(bounds);
            }

            // in other cases, we cannot cheaply check whether the Draw contains a known element.
            return false;
        }
    }

    /**
     * Map a rect using the inverse of the given matrix.
     * Only for scale-translate matrices as an optimization.
     */
    // input is other rect, return is local rect
    //@formatter:off
    public static boolean inverseMapRect(@NonNull Matrixc localToOther, @NonNull Rect2f localOtherRect,
                                  boolean checkPrecision, Matrixc otherToDevice) {
        assert localToOther.isScaleTranslate();

        // Inline version of Matrix.invertScaleTranslate()
        // But we don't check if it's invertible, the mapped rectangle is not finite in that case
        float invSx = 1.0f / localToOther.getScaleX();
        float invSy = 1.0f / localToOther.getScaleY();
        float invTx = -localToOther.getTranslateX() * invSx;
        float invTy = -localToOther.getTranslateY() * invSy;

        // In this block, `localOtherRect` is defined in the other coord space and `mapped` is in
        // the local coord space. At the end of the block, `localOtherRect` is set to `mapped` so
        // that afterwards it is always defined in local space.
        float x1 = localOtherRect.left()   * invSx + invTx;
        float x2 = localOtherRect.right()  * invSx + invTx;
        float y1 = localOtherRect.top()    * invSy + invTy;
        float y2 = localOtherRect.bottom() * invSy + invTy;

        float mappedL = Math.min(x1, x2);
        float mappedT = Math.min(y1, y2);
        float mappedR = Math.max(x1, x2);
        float mappedB = Math.max(y1, y2);

        if (checkPrecision) {

            // If we don't have enough precision, the other shape might not map back to the geometry.
            // Allow up to 1/255th of a pixel in tolerance when mapping between coordinate spaces,
            // otherwise we'll have to clip the shapes independently.
            float otherTol = DEFAULT_PIXEL_TOLERANCE * otherToDevice.localAARadius(localOtherRect);
            // Map local back to other
            float bx1 = mappedL * localToOther.getScaleX() + localToOther.getTranslateX();
            float bx2 = mappedR * localToOther.getScaleX() + localToOther.getTranslateX();
            float by1 = mappedT * localToOther.getScaleY() + localToOther.getTranslateY();
            float by2 = mappedB * localToOther.getScaleY() + localToOther.getTranslateY();

            if (localOtherRect.isEmpty() ||
                    !MathUtil.isApproxEqual(Math.min(bx1, bx2), localOtherRect.left()  , otherTol) ||
                    !MathUtil.isApproxEqual(Math.min(by1, by2), localOtherRect.top()   , otherTol) ||
                    !MathUtil.isApproxEqual(Math.max(bx1, bx2), localOtherRect.right() , otherTol) ||
                    !MathUtil.isApproxEqual(Math.max(by1, by2), localOtherRect.bottom(), otherTol)) {
                // 'original other' and 'remapped other' empty, infinite, NaN, or error is too large
                return false;
            }
        }

        // unchecked case can be empty, infinite or NaN
        // but not alter contains/intersects
        localOtherRect.set(mappedL, mappedT, mappedR, mappedB);

        return true;
    }
    //@formatter:on

    /**
     * Conservatively computes the intersection of RRect and Rect, returns null to
     * if there's no intersection or intersection is not RRect.
     */
    public static @Nullable RRect intersect(@NonNull RRect a, @NonNull Rect b) {
        // a cannot be rect; both a,b cannot be empty
        assert !a.isEmpty() && !a.isRect();
        //TODO
        return null;
    }
}

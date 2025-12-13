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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.sketch.Bounded;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.StrokeRec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a recorded draw operation.
 * <p>
 * Draw contains multiple groups of data, each of which will be initialized step by step.
 */
public final class Draw implements Cloneable {

    /**
     * Pointer to the renderer instance, managed by {@link RendererProvider}.
     */
    public GeometryRenderer mRenderer;

    /**
     * This matrix transforms geometry's local space to device space.
     */
    public Matrixc mTransform;
    public Bounded mGeometry;
    public boolean mInverseFill;


    /**
     * Clip params (immutable), set by {@link ClipStack}.
     * <p>
     * DrawBounds: Tight bounds of the draw in device space, including any padding/outset for stroking and expansion
     * due to inverse fill and intersected with the scissor.
     * <p>
     * TransformedShapeBounds: Clipped bounds of the shape in device space, including any padding/outset for stroking,
     * intersected with the scissor and ignoring the fill rule. For a regular fill this is identical
     * to DrawBounds. For an inverse fill, this is a subset of DrawBounds.
     * <p>
     * ScissorRect: The scissor rectangle obtained by restricting the bounds of the clip stack that affects the
     * draw to the device bounds. The scissor must contain DrawBounds and must already be
     * intersected with the device bounds.
     */
    public Rect2f mDrawBounds;
    public Rect2f mTransformedShapeBounds;
    public Rect2ic mScissorRect;
    /**
     * Precomputed local AA radius if {@link GeometryRenderer#outsetBoundsForAA()} is true,
     * set by {@link ClipStack}, used for analytic AA draws.
     */
    public float mAARadius;


    /*
     * DrawOrder aggregates the three separate sequences that Granite uses to re-order draws and their
     * substeps as much as possible while preserving the painter's order semantics of the Canvas API.
     *
     * To build the full DrawOrder for a draw, start with its assigned PaintersDepth (i.e. the original
     * painter's order of the draw call). From there, the DrawOrder can be updated to reflect
     * dependencies on previous draws, either from depth-only clip draws or because the draw is
     * transparent and must blend with the previous color values. Lastly, once the
     * CompressedPaintersOrder is finalized, the DrawOrder can be updated to reflect whether or not
     * the draw will involve the stencil buffer--and if so, specify the disjoint stencil set it
     * belongs to.
     *
     * The original and effective order that draws are executed in is defined by the PaintersDepth.
     * However, the actual execution order is defined by first the CompressedPaintersOrder and then
     * the DisjointStencilIndex. This means that draws with much higher depths can be executed earlier
     * if painter's order compression allows for it.
     */

    /**
     * Orders are 16-bit unsigned integers.
     */
    public static final int MIN_SEQUENCE_VALUE = 0;
    public static final int MAX_SEQUENCE_VALUE = 0xFFFF;

    // The first PaintersDepth is reserved for clearing the depth attachment; any draw using this
    // depth will always fail the depth test.
    public static final int CLEAR_DEPTH = MIN_SEQUENCE_VALUE;
    // The first CompressedPaintersOrder is reserved to indicate there is no previous draw that
    // must come before a draw.
    public static final int NO_INTERSECTION = MIN_SEQUENCE_VALUE;
    // The first DisjointStencilIndex is reserved to indicate an unassigned stencil set.
    public static final int UNASSIGNED = MIN_SEQUENCE_VALUE;

    /**
     * CompressedPaintersOrder is an ordinal number that allows draw commands to be re-ordered so long
     * as when they are executed, the read/writes to the color|depth attachments respect the original
     * painter's order. Logical draws with the same CompressedPaintersOrder can be assumed to be
     * executed in any order, however that may have been determined (e.g. BoundsManager or relying on
     * a depth test during rasterization).
     */
    public int mPaintOrder = NO_INTERSECTION;
    /**
     * Each DisjointStencilIndex specifies an implicit set of non-overlapping draws. Assuming that two
     * draws have the same CompressedPaintersOrder and the same DisjointStencilIndex, their substeps
     * for multi-pass rendering (stencil-then-cover, etc.) can be intermingled with each other and
     * produce the same results as if each draw's substeps were executed in order before moving on to
     * the next draw's.
     * <p>
     * Ordering within a set can be entirely arbitrary (i.e. all stencil steps can go before all cover
     * steps). Ordering between sets is also arbitrary since all draws share the same
     * CompressedPaintersOrder, so long as one set is entirely drawn before the next.
     * <p>
     * Two draws that have different CompressedPaintersOrders but the same DisjointStencilIndex are
     * unrelated, they may or may not overlap. The painters order scopes the disjoint sets.
     */
    public int mStencilIndex = UNASSIGNED;
    /**
     * Every draw has an associated depth value. The value is constant across the entire draw and is
     * not related to any varying Z coordinate induced by a 4x4 transform. The painter's depth is stored
     * in the depth attachment and the GREATER depth test is used to reject or accept pixels/samples
     * relative to what has already been rendered into the depth attachment. This allows draws that do
     * not depend on the previous color to be radically re-ordered relative to their original painter's
     * order while producing correct results.
     */
    public int mDepth = CLEAR_DEPTH;


    /**
     * Stroke params.
     */
    public float mHalfWidth = -1;   // >0: relative to transform; ==0: hairline, 1px in device space; <0: fill
    public float mJoinLimit = -1;   // >0: miter join; ==0: bevel join; <0: round join
    // Paint::Cap
    public byte mStrokeCap;
    // Paint::Align
    public byte mStrokeAlign;

    public float @Nullable [] mSolidColor;

    public Draw(@NonNull Matrixc transform,
                Bounded geometry, boolean inverseFill) {
        assert geometry != null || inverseFill;
        mTransform = transform;
        mGeometry = geometry;
        mInverseFill = inverseFill;
    }

    public void getBounds(@NonNull Rect2f dest) {
        final Bounded g = mGeometry;
        if (g == null) dest.setEmpty();
        else g.getBounds(dest);
    }

    public boolean isFloodFill() {
        return mGeometry == null && mInverseFill;
    }

    public boolean isClippedOut() {
        return mDrawBounds.isEmpty();
    }

    public void outsetBoundsForAA() {
        // We use 1px to handle both subpixel/hairline approaches and the standard 1/2px outset
        // for shapes that cover multiple pixels.
        mTransformedShapeBounds.outset(1f, 1f);
        // This is a no-op for inverse fills (where mDrawBounds was already equal to mScissorRect),
        // and equivalent to mDrawBounds = (mTransformedShapeBounds intersect mScissorRect) with
        // the outset shape bounds.
        mDrawBounds.outset(1f, 1f);
        mDrawBounds.intersectNoCheck(mScissorRect);
    }

    /**
     * Returns true if the geometry is stroked instead of filled.
     */
    public boolean isStroke() {
        return mHalfWidth >= 0.f;
    }

    public boolean isMiterJoin() {
        return mJoinLimit > 0.f;
    }

    public boolean isBevelJoin() {
        return mJoinLimit == 0.f;
    }

    public boolean isRoundJoin() {
        return mJoinLimit < 0.f;
    }

    public float getMiterLimit() {
        return Math.max(0.f, mJoinLimit);
    }

    /**
     * @see StrokeRec#getInflationRadius()
     */
    public float getInflationRadius() {
        if (mHalfWidth < 0) { // fill
            return 0;
        } else if (mHalfWidth == 0) { // hairline
            return 1;
        }

        float multiplier = 1;
        if (mJoinLimit > 0) { // miter join
            multiplier = Math.max(multiplier, mJoinLimit);
        }
        if (mStrokeAlign == Paint.ALIGN_CENTER) {
            if (mStrokeCap == Paint.CAP_SQUARE) {
                multiplier = Math.max(multiplier, MathUtil.SQRT2);
            }
        } else {
            multiplier *= 2.0f;
        }
        return mHalfWidth * multiplier;
    }

    // the clip space depth value in z-buffer
    public int clipDepth() {
        return mDepth;
    }

    // the clip space depth value in z-buffer
    public float clipDepthAsFloat() {
        return (float) mDepth / (float) MAX_SEQUENCE_VALUE;
    }

    // Coopt the stencil index to encode the draw's actual painter's depth in decreasing order,
    // for use enforcing front-to-back order (since the compressed painter's order handles back-to-front).
    public void reverseDepthAsStencil() {
        assert mStencilIndex == UNASSIGNED; // can't have a real stencil index
        mStencilIndex = MAX_SEQUENCE_VALUE - mDepth;
    }

    public void dependsOnPaintersOrder(int prevDraw) {
        // A draw must be ordered after all previous draws that it depends on
        int next = prevDraw + 1;
        if (mPaintOrder < next) {
            mPaintOrder = next;
        }
    }

    public void dependsOnStencil(int disjointSet) {
        // Stencil usage should only be set once
        assert mStencilIndex == UNASSIGNED;
        mStencilIndex = disjointSet;
    }

    @Override
    public Draw clone() {
        try {
            return (Draw) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}

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

package icyllis.arc3d.core;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * The {@link Rect2i} holds four integer coordinates describing the upper and
 * lower bounds of a rectangle (left, top, right bottom). These fields can
 * be accessed directly. Use width() and height() to retrieve the rectangle's
 * width and height.
 * <p>
 * Rect may be created from outer bounds or from position, width, and height.
 * Rect describes an area; if its right is less than or equal to its left,
 * or if its bottom is less than or equal to its top, it is considered empty.
 * <p>
 * Note that the right and bottom coordinates are exclusive. This means a
 * {@link Rect2i} being drawn untransformed onto a {@link icyllis.arc3d.sketch.Canvas} will
 * draw into the column and row described by its left and top coordinates,
 * but not those of its bottom and right.
 */
@SuppressWarnings("unused")
public non-sealed class Rect2i extends Rect2ic {

    private static final Rect2ic EMPTY = new Rect2i();

    /**
     * Create a new rectangle with all coordinates initialized to 0.
     */
    public Rect2i() {
    }

    /**
     * Create a new rectangle with the specified coordinates. Note: no range
     * checking is performed, so the caller should ensure that left <= right and
     * top <= bottom.
     *
     * @param left   the X coordinate of the left side of the rectangle
     * @param top    the Y coordinate of the top of the rectangle
     * @param right  the X coordinate of the right side of the rectangle
     * @param bottom the Y coordinate of the bottom of the rectangle
     */
    public Rect2i(int left, int top, int right, int bottom) {
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
    }

    /**
     * Create a new rectangle, initialized with the values in the specified
     * rectangle (which is left unmodified).
     *
     * @param r the rectangle whose coordinates are copied into the new
     *          rectangle
     */
    public Rect2i(@NonNull Rect2ic r) {
        set(r);
    }

    /**
     * Create a new rectangle, initialized with the values in the specified
     * rectangle (which is left unmodified).
     *
     * @param r the rectangle whose coordinates are copied into the new
     *          rectangle
     */
    public Rect2i(@NonNull Rect2fc r) {
        mLeft = (int) r.mLeft;
        mTop = (int) r.mTop;
        mRight = (int) r.mRight;
        mBottom = (int) r.mBottom;
    }

    /**
     * Returns a read-only empty rect.
     *
     * @return an empty rect
     */
    @NonNull
    public static Rect2ic empty() {
        return EMPTY;
    }

    /**
     * Set the rectangle to (0,0,0,0)
     */
    public final void setEmpty() {
        mLeft = mRight = mTop = mBottom = 0;
    }

    /**
     * Set the rectangle's left.
     */
    @Contract(mutates = "this")
    public final void left(int left) {
        mLeft = left;
    }

    /**
     * Set the rectangle's top.
     */
    @Contract(mutates = "this")
    public final void top(int top) {
        mTop = top;
    }

    /**
     * Set the rectangle's right.
     */
    @Contract(mutates = "this")
    public final void right(int right) {
        mRight = right;
    }

    /**
     * Set the rectangle's bottom.
     */
    @Contract(mutates = "this")
    public final void bottom(int bottom) {
        mBottom = bottom;
    }

    /**
     * Set the rectangle's coordinates to the specified values. Note: no range
     * checking is performed, so it is up to the caller to ensure that
     * left <= right and top <= bottom.
     *
     * @param left   the X coordinate of the left side of the rectangle
     * @param top    the Y coordinate of the top of the rectangle
     * @param right  the X coordinate of the right side of the rectangle
     * @param bottom the Y coordinate of the bottom of the rectangle
     */
    public final void set(int left, int top, int right, int bottom) {
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
    }

    /**
     * Copy the coordinates from src into this rectangle.
     *
     * @param src the rectangle whose coordinates are copied into this
     *            rectangle.
     */
    public final void set(@NonNull Rect2ic src) {
        mLeft = src.mLeft;
        mTop = src.mTop;
        mRight = src.mRight;
        mBottom = src.mBottom;
    }

    /**
     * Offset the rectangle by adding dx to its left and right coordinates, and
     * adding dy to its top and bottom coordinates.
     *
     * @param dx the amount to add to the rectangle's left and right coordinates
     * @param dy the amount to add to the rectangle's top and bottom coordinates
     */
    public final void offset(int dx, int dy) {
        mLeft += dx;
        mTop += dy;
        mRight += dx;
        mBottom += dy;
    }

    /**
     * Offset the rectangle to a specific (left, top) position,
     * keeping its width and height the same.
     *
     * @param newLeft the new "left" coordinate for the rectangle
     * @param newTop  the new "top" coordinate for the rectangle
     */
    public final void offsetTo(int newLeft, int newTop) {
        mRight += newLeft - mLeft;
        mBottom += newTop - mTop;
        mLeft = newLeft;
        mTop = newTop;
    }

    /**
     * Inset the rectangle by (dx,dy). If dx is positive, then the sides are
     * moved inwards, making the rectangle narrower. If dx is negative, then the
     * sides are moved outwards, making the rectangle wider. The same holds true
     * for dy and the top and bottom.
     *
     * @param dx the amount to add(subtract) from the rectangle's left(right)
     * @param dy the amount to add(subtract) from the rectangle's top(bottom)
     */
    public final void inset(int dx, int dy) {
        mLeft += dx;
        mTop += dy;
        mRight -= dx;
        mBottom -= dy;
    }

    /**
     * Insets the rectangle on all sides specified by the insets.
     *
     * @param left   the amount to add from the rectangle's left
     * @param top    the amount to add from the rectangle's top
     * @param right  the amount to subtract from the rectangle's right
     * @param bottom the amount to subtract from the rectangle's bottom
     */
    public final void inset(int left, int top, int right, int bottom) {
        mLeft += left;
        mTop += top;
        mRight -= right;
        mBottom -= bottom;
    }

    /**
     * Insets the rectangle on all sides specified by the dimensions of the {@code insets}
     * rectangle.
     *
     * @param insets the rectangle specifying the insets on all side.
     */
    public final void inset(@NonNull Rect2ic insets) {
        mLeft += insets.mLeft;
        mTop += insets.mTop;
        mRight -= insets.mRight;
        mBottom -= insets.mBottom;
    }

    /**
     * Adjusts the rectangle on all sides specified by the values.
     *
     * @param left   the amount to add from the rectangle's left
     * @param top    the amount to add from the rectangle's top
     * @param right  the amount to add from the rectangle's right
     * @param bottom the amount to add from the rectangle's bottom
     */
    public final void adjust(int left, int top, int right, int bottom) {
        mLeft += left;
        mTop += top;
        mRight += right;
        mBottom += bottom;
    }

    /**
     * Adjusts the rectangle on all sides specified by the values.
     *
     * @param adjusts the rectangle specifying the adjusts on all side.
     */
    public final void adjust(@NonNull Rect2ic adjusts) {
        mLeft += adjusts.mLeft;
        mTop += adjusts.mTop;
        mRight += adjusts.mRight;
        mBottom += adjusts.mBottom;
    }

    // Evaluate A-B. If the difference shape cannot be represented as a rectangle then false is
    // returned and 'out' is set to the largest rectangle contained in said shape. If true is
    // returned then A-B is representable as a rectangle, which is stored in 'out'.
    public static boolean subtract(Rect2ic a, Rect2ic b, Rect2i out) {
        assert out != b;
        if (a.isEmpty() || b.isEmpty() || !intersects(a, b)) {
            // Either already empty, or subtracting the empty rect, or there's no intersection, so
            // in all cases the answer is A.
            if (out != a) {
                out.set(a);
            }
            return true;
        }

        // 4 rectangles to consider. If the edge in A is contained in B, the resulting difference can
        // be represented exactly as a rectangle. Otherwise the difference is the largest subrectangle
        // that is disjoint from B:
        // 1. Left part of A:   (A.left,  A.top,    B.left,  A.bottom)
        // 2. Right part of A:  (B.right, A.top,    A.right, A.bottom)
        // 3. Top part of A:    (A.left,  A.top,    A.right, B.top)
        // 4. Bottom part of A: (A.left,  B.bottom, A.right, A.bottom)
        //
        // Depending on how B intersects A, there will be 1 to 4 positive areas:
        //  - 4 occur when A contains B
        //  - 3 occur when B intersects a single edge
        //  - 2 occur when B intersects at a corner, or spans two opposing edges
        //  - 1 occurs when B spans two opposing edges and contains a 3rd, resulting in an exact rect
        //  - 0 occurs when B contains A, resulting in the empty rect
        //
        // Compute the relative areas of the 4 rects described above. Since each subrectangle shares
        // either the width or height of A, we only have to divide by the other dimension, which avoids
        // overflow on int32 types, and even if the float relative areas overflow to infinity, the
        // comparisons work out correctly and (one of) the infinitely large subrects will be chosen.
        float aHeight = (float) a.height();
        float aWidth = (float) a.width();
        float leftArea = 0.f, rightArea = 0.f, topArea = 0.f, bottomArea = 0.f;
        int positiveCount = 0;
        if (b.mLeft > a.mLeft) {
            leftArea = (b.mLeft - a.mLeft) / aWidth;
            positiveCount++;
        }
        if (a.mRight > b.mRight) {
            rightArea = (a.mRight - b.mRight) / aWidth;
            positiveCount++;
        }
        if (b.mTop > a.mTop) {
            topArea = (b.mTop - a.mTop) / aHeight;
            positiveCount++;
        }
        if (a.mBottom > b.mBottom) {
            bottomArea = (a.mBottom - b.mBottom) / aHeight;
            positiveCount++;
        }

        if (positiveCount == 0) {
            assert (b.contains(a));
            out.setEmpty();
            return true;
        }

        if (out != a) {
            out.set(a);
        }
        if (leftArea > rightArea && leftArea > topArea && leftArea > bottomArea) {
            // Left chunk of A, so the new right edge is B's left edge
            out.mRight = b.mLeft;
        } else if (rightArea > topArea && rightArea > bottomArea) {
            // Right chunk of A, so the new left edge is B's right edge
            out.mLeft = b.mRight;
        } else if (topArea > bottomArea) {
            // Top chunk of A, so the new bottom edge is B's top edge
            out.mBottom = b.mTop;
        } else {
            // Bottom chunk of A, so the new top edge is B's bottom edge
            assert (bottomArea > 0.f);
            out.mTop = b.mBottom;
        }

        // If we have 1 valid area, the disjoint shape is representable as a rectangle.
        assert (!intersects(out, b));
        return positiveCount == 1;
    }

    /**
     * If the rectangle specified by left,top,right,bottom intersects this
     * rectangle, return true and set this rectangle to that intersection,
     * otherwise return false and do not change this rectangle. Note: To
     * just test for intersection, use {@link #intersects(Rect2ic, Rect2ic)}.
     *
     * @param left   the left side of the rectangle being intersected with this
     *               rectangle
     * @param top    the top of the rectangle being intersected with this rectangle
     * @param right  the right side of the rectangle being intersected with this
     *               rectangle.
     * @param bottom the bottom of the rectangle being intersected with this
     *               rectangle.
     * @return true if the specified rectangle and this rectangle intersect
     * (and this rectangle is then set to that intersection) else
     * return false and do not change this rectangle.
     */
    public final boolean intersect(int left, int top, int right, int bottom) {
        int tmpL = Math.max(mLeft, left);
        int tmpT = Math.max(mTop, top);
        int tmpR = Math.min(mRight, right);
        int tmpB = Math.min(mBottom, bottom);
        if (tmpR <= tmpL || tmpB <= tmpT) {
            return false;
        }
        mLeft = tmpL;
        mTop = tmpT;
        mRight = tmpR;
        mBottom = tmpB;
        return true;
    }

    /**
     * If the specified rectangle intersects this rectangle, return true and set
     * this rectangle to that intersection, otherwise return false and do not
     * change this rectangle. To just test for intersection, use intersects().
     *
     * @param r the rectangle being intersected with this rectangle.
     * @return true if the specified rectangle and this rectangle intersect
     * (and this rectangle is then set to that intersection) else
     * return false and do not change this rectangle.
     */
    public final boolean intersect(@NonNull Rect2ic r) {
        return intersect(r.mLeft, r.mTop, r.mRight, r.mBottom);
    }

    /**
     * If the specified rectangle intersects this rectangle, set this rectangle to that
     * intersection, otherwise set this rectangle to the empty rectangle.
     *
     * @see #inset(int, int, int, int) but without checking if the rects overlap.
     */
    public final void intersectNoCheck(int left, int top, int right, int bottom) {
        mLeft = Math.max(mLeft, left);
        mTop = Math.max(mTop, top);
        mRight = Math.min(mRight, right);
        mBottom = Math.min(mBottom, bottom);
    }

    /**
     * If the specified rectangle intersects this rectangle, set this rectangle to that
     * intersection, otherwise set this rectangle to the empty rectangle.
     *
     * @see #inset(int, int, int, int) but without checking if the rects overlap.
     */
    public final void intersectNoCheck(@NonNull Rect2ic r) {
        intersectNoCheck(r.mLeft, r.mTop, r.mRight, r.mBottom);
    }

    /**
     * If rectangles a and b intersect, return true and set this rectangle to
     * that intersection, otherwise return false and do not change this
     * rectangle. To just test for intersection, use intersects().
     *
     * @param a the first rectangle being intersected with
     * @param b the second rectangle being intersected with
     * @return true if the two specified rectangles intersect. If they do, set
     * this rectangle to that intersection. If they do not, return
     * false and do not change this rectangle.
     */
    public final boolean intersect(@NonNull Rect2ic a, @NonNull Rect2ic b) {
        int tmpL = Math.max(a.mLeft, b.mLeft);
        int tmpT = Math.max(a.mTop, b.mTop);
        int tmpR = Math.min(a.mRight, b.mRight);
        int tmpB = Math.min(a.mBottom, b.mBottom);
        if (tmpR <= tmpL || tmpB <= tmpT) {
            return false;
        }
        mLeft = tmpL;
        mTop = tmpT;
        mRight = tmpR;
        mBottom = tmpB;
        return true;
    }

    /**
     * Returns true if the two specified rectangles intersect. In no event are
     * either of the rectangles modified. To record the intersection,
     * use {@link #intersect(Rect2ic)} or {@link #intersect(Rect2ic, Rect2ic)}.
     *
     * @param a the first rectangle being tested for intersection
     * @param b the second rectangle being tested for intersection
     * @return true if the two specified rectangles intersect. In no event are
     * either of the rectangles modified.
     */
    public static boolean intersects(@NonNull Rect2ic a, @NonNull Rect2ic b) {
        int tmpL = Math.max(a.mLeft, b.mLeft);
        int tmpT = Math.max(a.mTop, b.mTop);
        int tmpR = Math.min(a.mRight, b.mRight);
        int tmpB = Math.min(a.mBottom, b.mBottom);
        return tmpR > tmpL && tmpB > tmpT;
    }

    /**
     * Update this Rect to enclose itself and the specified rectangle. If the
     * specified rectangle is empty, nothing is done. If this rectangle is empty
     * it is set to the specified rectangle.
     *
     * @param left   the left edge being unioned with this rectangle
     * @param top    the top edge being unioned with this rectangle
     * @param right  the right edge being unioned with this rectangle
     * @param bottom the bottom edge being unioned with this rectangle
     */
    public final void join(int left, int top, int right, int bottom) {
        // do nothing if the params are empty
        if (left >= right || top >= bottom) {
            return;
        }
        if (mLeft < mRight && mTop < mBottom) {
            if (mLeft > left) mLeft = left;
            if (mTop > top) mTop = top;
            if (mRight < right) mRight = right;
            if (mBottom < bottom) mBottom = bottom;
        } else {
            // if we are empty, just assign
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
        }
    }

    /**
     * Update this Rect to enclose itself and the specified rectangle. If the
     * specified rectangle is empty, nothing is done. If this rectangle is empty
     * it is set to the specified rectangle.
     *
     * @param r the rectangle being unioned with this rectangle
     */
    public final void join(@NonNull Rect2ic r) {
        join(r.mLeft, r.mTop, r.mRight, r.mBottom);
    }

    /**
     * Update this Rect to enclose itself and the specified rectangle. If the
     * specified rectangle is empty, nothing is done. If this rectangle is empty
     * it is set to the specified rectangle. No check is performed to see if
     * either rectangle is empty.
     *
     * @param left   the left edge being unioned with this rectangle
     * @param top    the top edge being unioned with this rectangle
     * @param right  the right edge being unioned with this rectangle
     * @param bottom the bottom edge being unioned with this rectangle
     */
    public final void joinNoCheck(int left, int top, int right, int bottom) {
        mLeft = Math.min(mLeft, left);
        mTop = Math.min(mTop, top);
        mRight = Math.max(mRight, right);
        mBottom = Math.max(mBottom, bottom);
    }

    /**
     * Update this Rect to enclose itself and the specified rectangle. If the
     * specified rectangle is empty, nothing is done. If this rectangle is empty
     * it is set to the specified rectangle. No check is performed to see if
     * either rectangle is empty.
     *
     * @param r the rectangle being unioned with this rectangle
     */
    public final void joinNoCheck(@NonNull Rect2ic r) {
        joinNoCheck(r.mLeft, r.mTop, r.mRight, r.mBottom);
    }

    /**
     * Update this Rect to enclose itself and the [x,y] coordinate.
     *
     * @param x The x coordinate of the point to add to the rectangle
     * @param y The y coordinate of the point to add to the rectangle
     */
    public final void join(int x, int y) {
        if (mLeft < mRight && mTop < mBottom) {
            if (x < mLeft) {
                mLeft = x;
            } else if (x > mRight) {
                mRight = x;
            }
            if (y < mTop) {
                mTop = y;
            } else if (y > mBottom) {
                mBottom = y;
            }
        } else {
            // still empty
            mLeft = mRight = x;
            mTop = mBottom = y;
        }
    }

    /**
     * Swap top/bottom or left/right if there are flipped (i.e. left > right
     * and/or top > bottom). This can be called if the edges are computed
     * separately, and may have crossed over each other. If the edges are
     * already correct (i.e. left <= right and top <= bottom) then nothing is done.
     */
    public final void sort() {
        if (mLeft > mRight) {
            int temp = mLeft;
            mLeft = mRight;
            mRight = temp;
        }
        if (mTop > mBottom) {
            int temp = mTop;
            mTop = mBottom;
            mBottom = temp;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Rect2i r)) {
            return false;
        }
        return mLeft == r.mLeft && mTop == r.mTop && mRight == r.mRight && mBottom == r.mBottom;
    }

    @Override
    public int hashCode() {
        int result = mLeft;
        result = 31 * result + mTop;
        result = 31 * result + mRight;
        result = 31 * result + mBottom;
        return result;
    }

    @Override
    public String toString() {
        return "Rect2i(" + mLeft + ", " +
                mTop + ", " + mRight +
                ", " + mBottom + ")";
    }
}

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
 * Interface to a read-only view of a rectangle in integer coordinates.
 * This does not mean that the rectangle is immutable, it only implies that
 * a method should not change the state of the rectangle.
 * <p>
 * {@code Rect2i const &rect}
 *
 * @author BloCamLimb
 * @see Rect2i
 */
public sealed abstract class Rect2ic permits Rect2i {

    protected int mLeft;
    protected int mTop;
    protected int mRight;
    protected int mBottom;

    /**
     * Returns true if left is equal to or greater than right, or if top is equal
     * to or greater than bottom. Call sort() to reverse rectangles with negative
     * width() or height().
     *
     * @return true if width() or height() are zero or negative
     */
    @Contract(pure = true)
    public final boolean isEmpty() {
        return mRight <= mLeft || mBottom <= mTop;
    }

    /**
     * Returns true if left is equal to or less than right, or if top is equal
     * to or less than bottom. Call sort() to reverse rectangles with negative
     * width() or height().
     *
     * @return true if width() or height() are zero or positive
     */
    @Contract(pure = true)
    public final boolean isSorted() {
        return mLeft <= mRight && mTop <= mBottom;
    }

    /**
     * Returns the rectangle's left.
     */
    @Contract(pure = true)
    public final int x() {
        return mLeft;
    }

    /**
     * Return the rectangle's top.
     */
    @Contract(pure = true)
    public final int y() {
        return mTop;
    }

    /**
     * Returns the rectangle's left.
     */
    @Contract(pure = true)
    public final int left() {
        return mLeft;
    }

    /**
     * Return the rectangle's top.
     */
    @Contract(pure = true)
    public final int top() {
        return mTop;
    }

    /**
     * Return the rectangle's right.
     */
    @Contract(pure = true)
    public final int right() {
        return mRight;
    }

    /**
     * Return the rectangle's bottom.
     */
    @Contract(pure = true)
    public final int bottom() {
        return mBottom;
    }

    /**
     * @return the rectangle's width. This does not check for a valid rectangle
     * (i.e. left <= right) so the result may be negative.
     */
    @Contract(pure = true)
    public final int width() {
        return mRight - mLeft;
    }

    /**
     * @return the rectangle's height. This does not check for a valid rectangle
     * (i.e. top <= bottom) so the result may be negative.
     */
    @Contract(pure = true)
    public final int height() {
        return mBottom - mTop;
    }

    /**
     * Copy the coordinates from this into r.
     *
     * @param dst the rectangle to store
     */
    @Contract(mutates = "param")
    public void store(@NonNull Rect2i dst) {
        dst.mLeft = mLeft;
        dst.mTop = mTop;
        dst.mRight = mRight;
        dst.mBottom = mBottom;
    }

    /**
     * Copy the coordinates from this into r.
     *
     * @param dst the rectangle to store
     */
    @Contract(mutates = "param")
    public void store(@NonNull Rect2f dst) {
        dst.mLeft = mLeft;
        dst.mTop = mTop;
        dst.mRight = mRight;
        dst.mBottom = mBottom;
    }

    /**
     * Returns true if this rectangle intersects the specified rectangle.
     * In no event is this rectangle modified. To record the intersection,
     * use intersect().
     *
     * @param left   the left side of the rectangle being tested for intersection
     * @param top    the top of the rectangle being tested for intersection
     * @param right  the right side of the rectangle being tested for
     *               intersection
     * @param bottom the bottom of the rectangle being tested for intersection
     * @return true if the specified rectangle intersects this rectangle. In
     * no event is this rectangle modified.
     */
    public final boolean intersects(int left, int top, int right, int bottom) {
        int tmpL = Math.max(mLeft, left);
        int tmpT = Math.max(mTop, top);
        int tmpR = Math.min(mRight, right);
        int tmpB = Math.min(mBottom, bottom);
        return tmpR > tmpL && tmpB > tmpT;
    }

    /**
     * Returns true if this rectangle intersects the specified rectangle.
     * In no event is this rectangle modified. To record the intersection,
     * use intersect().
     *
     * @param r the rectangle being tested for intersection
     * @return true if the specified rectangle intersects this rectangle. In
     * no event is this rectangle modified.
     */
    public final boolean intersects(@NonNull Rect2ic r) {
        return intersects(r.mLeft, r.mTop, r.mRight, r.mBottom);
    }

    /**
     * Returns true if (x,y) is inside the rectangle. The left and top are
     * considered to be inside, while the right and bottom are not. This means
     * that for a (x,y) to be contained: left <= x < right and top <= y < bottom.
     * An empty rectangle never contains any point.
     *
     * @param x the X coordinate of the point being tested for containment
     * @param y the Y coordinate of the point being tested for containment
     * @return true if (x,y) are contained by the rectangle, where containment
     * means left <= x < right and top <= y < bottom
     */
    public final boolean contains(int x, int y) {
        return x >= mLeft && x < mRight && y >= mTop && y < mBottom;
    }

    /**
     * Returns true if (x,y) is inside the rectangle. The left and top are
     * considered to be inside, while the right and bottom are not. This means
     * that for a (x,y) to be contained: left <= x < right and top <= y < bottom.
     * An empty rectangle never contains any point.
     *
     * @param x the X coordinate of the point being tested for containment
     * @param y the Y coordinate of the point being tested for containment
     * @return true if (x,y) are contained by the rectangle, where containment
     * means left <= x < right and top <= y < bottom
     */
    public final boolean contains(float x, float y) {
        return x >= mLeft && x < mRight && y >= mTop && y < mBottom;
    }

    /**
     * Returns true if the 4 specified sides of a rectangle are inside or equal
     * to this rectangle. i.e. is this rectangle a superset of the specified
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param left   the left side of the rectangle being tested for containment
     * @param top    the top of the rectangle being tested for containment
     * @param right  the right side of the rectangle being tested for containment
     * @param bottom the bottom of the rectangle being tested for containment
     * @return true if the 4 specified sides of a rectangle are inside or
     * equal to this rectangle
     */
    public final boolean contains(int left, int top, int right, int bottom) {
        // check for empty first
        return mLeft < mRight && mTop < mBottom
                // now check for containment
                && mLeft <= left && mTop <= top
                && mRight >= right && mBottom >= bottom;
    }

    /**
     * Returns true if the specified rectangle r is inside or equal to this
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param r the rectangle being tested for containment.
     * @return true if the specified rectangle r is inside or equal to this
     * rectangle
     */
    public final boolean contains(Rect2ic r) {
        // check for empty first
        // now check for containment
        return mLeft < mRight && mTop < mBottom && mLeft <= r.mLeft && mTop <= r.mTop && mRight >= r.mRight && mBottom >= r.mBottom;
    }

    /**
     * Returns true if the 4 specified sides of a rectangle are inside or equal
     * to this rectangle. i.e. is this rectangle a superset of the specified
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param left   the left side of the rectangle being tested for containment
     * @param top    the top of the rectangle being tested for containment
     * @param right  the right side of the rectangle being tested for containment
     * @param bottom the bottom of the rectangle being tested for containment
     * @return true if the 4 specified sides of a rectangle are inside or
     * equal to this rectangle
     */
    public final boolean contains(float left, float top, float right, float bottom) {
        // check for empty first
        return mLeft < mRight && mTop < mBottom
                // now check for containment
                && mLeft <= left && mTop <= top
                && mRight >= right && mBottom >= bottom;
    }

    /**
     * Returns true if the specified rectangle r is inside or equal to this
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param r the rectangle being tested for containment.
     * @return true if the specified rectangle r is inside or equal to this
     * rectangle
     */
    public final boolean contains(Rect2fc r) {
        // check for empty first
        return mLeft < mRight && mTop < mBottom
                // now check for containment
                && mLeft <= r.mLeft && mTop <= r.mTop && mRight >= r.mRight && mBottom >= r.mBottom;
    }
}

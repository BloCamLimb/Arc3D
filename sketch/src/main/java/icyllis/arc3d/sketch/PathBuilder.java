/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.RefCnt;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.util.Arrays;

public final class PathBuilder {

    public static final int
            ADD_PATH_APPEND = 0;

    public static final int
            ADD_PATH_EXTEND = 1;

    // if you don't know how big the first element is, allocating it in advance with a known size
    // may be a waste, so delay the allocation
    byte[] mVerbs = PathData.EMPTY_VERBS;
    float[] mCoords = PathData.EMPTY_COORDS; // x0 y0 x1 y1 x2 y2 ...

    int mVerbSize;
    int mCoordSize;

    @Path.SegmentMask
    byte mSegmentMask;
    private int mLastMoveToIndex; // in floats, not persistent

    private byte mConvexity;
    private byte mWindingRule;
    private boolean mIsVolatile;

    /**
     * Creates an empty PathBuilder with a default fill rule of {@link Path#WIND_NON_ZERO}.
     */
    public PathBuilder() {
        reset();
    }

    /**
     * Creates an empty PathBuilder with the specified fill rule.
     */
    public PathBuilder(int rule) {
        reset();
        setWindingRule(rule);
    }

    /**
     * Returns the rule used to fill path.
     *
     * @return current fill rule
     */
    public int getWindingRule() {
        return mWindingRule;
    }

    /**
     * Sets the rule used to fill path. <var>rule</var> must be either {@link Path#WIND_NON_ZERO}
     * or {@link Path#WIND_EVEN_ODD}. The default state is {@link Path#WIND_NON_ZERO}.
     */
    public PathBuilder setWindingRule(int rule) {
        if (rule == Path.WIND_NON_ZERO || rule == Path.WIND_EVEN_ODD) {
            mWindingRule = (byte) rule;
            return this;
        }

        throw new IllegalArgumentException("Unknown winding rule: " + rule);
    }

    public PathBuilder setIsVolatile(boolean isVolatile) {
        mIsVolatile = isVolatile;
        return this;
    }

    /**
     * Resets the path builder to its initial state, clears points and verbs,
     * sets fill rule to {@link Path#WIND_NON_ZERO} and sets volatile to false.
     * <p>
     * Preserves internal storage.
     *
     * @return this
     */
    public PathBuilder reset() {
        mVerbSize = 0;
        mCoordSize = 0;
        mSegmentMask = 0;
        mLastMoveToIndex = -1;
        mWindingRule = Path.WIND_NON_ZERO;
        mIsVolatile = false;
        mConvexity = Path.CONVEXITY_UNKNOWN;

        return this;
    }

    public PathBuilder set(@NonNull Path path) {
        reset();

        // there's fast path that replaces this builder
        addPath(path, null, ADD_PATH_APPEND);

        mWindingRule = path.mWindingRule;
        mIsVolatile = path.mIsVolatile;

        return this;
    }

    /**
     * Adds a point to the path by moving to the specified point {@code (x,y)}.
     * A new contour begins at {@code (x,y)}.
     * <p>
     * If the previous verb was a "move" verb,
     * then this just replaces the point value of that move, otherwise it appends a new
     * "move" verb to the builder using the point.
     * <p>
     * Thus, each contour can only have 1 move verb in it (the last one specified).
     *
     * @param x the specified X coordinate
     * @param y the specified Y coordinate
     * @return this
     */
    public PathBuilder moveTo(float x, float y) {
        if (mVerbSize != 0 && mVerbs[mVerbSize - 1] == Path.VERB_MOVE) {
            mCoords[mCoordSize - 2] = x;
            mCoords[mCoordSize - 1] = y;

            assert mConvexity == Path.CONVEXITY_UNKNOWN;
            assert mLastMoveToIndex == mCoordSize - 2;
        } else {
            mLastMoveToIndex = mCoordSize;

            incReserve(1, 1);
            mVerbs[mVerbSize++] = Path.VERB_MOVE;

            mCoords[mCoordSize++] = x;
            mCoords[mCoordSize++] = y;

            mConvexity = Path.CONVEXITY_UNKNOWN;
        }

        return this;
    }

    private void ensureMove() {
        if (mVerbSize == 0) {
            moveTo(0, 0);
        } else if (mVerbs[mVerbSize - 1] == Path.VERB_CLOSE) {
            moveTo(mCoords[mLastMoveToIndex], mCoords[mLastMoveToIndex + 1]);
        }
    }

    /**
     * Adds a point to the path by drawing a straight line from the
     * last point to the new specified point {@code (x,y)}.
     * <p>
     * If builder is empty, last point is set to (0, 0) before adding line.
     * If last verb is close, last point is set to last move point before adding line.
     *
     * @param x the X coordinate of the end point
     * @param y the Y coordinate of the end point
     * @return this
     */
    public PathBuilder lineTo(float x, float y) {
        ensureMove();

        incReserve(1, 1);
        mVerbs[mVerbSize++] = Path.VERB_LINE;

        mCoords[mCoordSize++] = x;
        mCoords[mCoordSize++] = y;

        mSegmentMask |= Path.SEGMENT_LINE;

        return this;
    }

    /**
     * Adds a curved segment, defined by two new points, to the path by
     * drawing a quadratic Bézier curve that intersects both the last
     * point and the specified point {@code (x2,y2)}, using the specified
     * point {@code (x1,y1)} as a quadratic control point.
     * <p>
     * If builder is empty, last point is set to (0, 0) before adding quad.
     * If last verb is close, last point is set to last move point before adding quad.
     *
     * @param x1 the X coordinate of the quadratic control point
     * @param y1 the Y coordinate of the quadratic control point
     * @param x2 the X coordinate of the final end point
     * @param y2 the Y coordinate of the final end point
     * @return this
     */
    public PathBuilder quadTo(float x1, float y1, float x2, float y2) {
        ensureMove();

        incReserve(2, 1);
        mVerbs[mVerbSize++] = Path.VERB_QUAD;

        int pi = mCoordSize;
        mCoords[pi] = x1;
        mCoords[pi + 1] = y1;
        mCoords[pi + 2] = x2;
        mCoords[pi + 3] = y2;
        mCoordSize = pi + 4;

        mSegmentMask |= Path.SEGMENT_QUAD;

        return this;
    }

    /**
     * @see #quadTo(float, float, float, float)
     */
    public PathBuilder quadTo(float @NonNull [] pts, int off) {
        return quadTo(pts[off], pts[off+1], pts[off+2], pts[off+3]);
    }

    /**
     * Adds a curved segment, defined by three new points, to the path by
     * drawing a cubic Bézier curve that intersects both the current
     * point and the specified point {@code (x3,y3)}, using the specified
     * points {@code (x1,y1)} and {@code (x2,y2)} as cubic control points.
     * <p>
     * If builder is empty, last point is set to (0, 0) before adding cubic.
     * If last verb is close, last point is set to last move point before adding cubic.
     *
     * @param x1 the X coordinate of the first cubic control point
     * @param y1 the Y coordinate of the first cubic control point
     * @param x2 the X coordinate of the second cubic control point
     * @param y2 the Y coordinate of the second cubic control point
     * @param x3 the X coordinate of the final end point
     * @param y3 the Y coordinate of the final end point
     * @return this
     */
    public PathBuilder cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        ensureMove();

        incReserve(3, 1);
        mVerbs[mVerbSize++] = Path.VERB_CUBIC;

        int pi = mCoordSize;
        mCoords[pi] = x1;
        mCoords[pi + 1] = y1;
        mCoords[pi + 2] = x2;
        mCoords[pi + 3] = y2;
        mCoords[pi + 4] = x3;
        mCoords[pi + 5] = y3;
        mCoordSize = pi + 6;

        mSegmentMask |= Path.SEGMENT_CUBIC;

        return this;
    }

    /**
     * @see #cubicTo(float, float, float, float, float, float)
     */
    public PathBuilder cubicTo(float @NonNull [] pts, int off) {
        return cubicTo(pts[off], pts[off+1], pts[off+2], pts[off+3], pts[off+4], pts[off+5]);
    }

    /**
     * Closes the current contour by drawing a straight line back to
     * the point of the last {@link #moveTo}.
     * <p>
     * Open and closed contour draw the same with {@link Paint#FILL}.
     * With {@link Paint#STROKE}, open contour draws {@link Paint.Cap}
     * at contour start and end; closed contour draws {@link Paint.Join}
     * at contour start and end.
     * <p>
     * If the path builder is empty, or is already closed then this method
     * has no effect.
     *
     * @return this
     */
    public PathBuilder close() {
        // If this is a 2nd 'close', we just ignore it
        if (mVerbSize != 0 && mVerbs[mVerbSize - 1] != Path.VERB_CLOSE) {
            incReserve(0, 1);
            mVerbs[mVerbSize++] = Path.VERB_CLOSE;
        }

        return this;
    }

    //////////////////////

    /**
     * Relative version of "move to".
     * <p>
     * Adds beginning of contour relative to last point.
     * If path builder is empty, starts contour at (dx, dy).
     * Otherwise, start contour at last point offset by (dx, dy).
     * If last verb is close, last point is set to last move point.
     *
     * @param dx offset from last point to contour start on x-axis
     * @param dy offset from last point to contour start on y-axis
     * @return this
     */
    public PathBuilder moveToRel(float dx, float dy) {
        float lastX = 0;
        float lastY = 0;
        if (mCoordSize != 0) {
            assert mLastMoveToIndex >= 0;
            if (mVerbs[mVerbSize - 1] == Path.VERB_CLOSE) {
                lastX = mCoords[mLastMoveToIndex];
                lastY = mCoords[mLastMoveToIndex + 1];
            } else {
                lastX = mCoords[mCoordSize - 2];
                lastY = mCoords[mCoordSize - 1];
            }
        }
        return moveTo(lastX + dx, lastY + dy);
    }

    /**
     * Relative version of "line to".
     * <p>
     * Adds a line from the last point to the specified vector (dx, dy).
     * Line end is last point plus vector (dx, dy).
     * If builder is empty, last point is set to (0, 0) before adding line.
     * If last verb is close, last point is set to last move point before adding line.
     *
     * @param dx the offset from last point to line end on x-axis
     * @param dy the offset from last point to line end on y-axis
     */
    public PathBuilder lineToRel(float dx, float dy) {
        ensureMove();

        float baseX = mCoords[mCoordSize - 2];
        float baseY = mCoords[mCoordSize - 1];

        return lineTo(baseX + dx, baseY + dy);
    }

    /**
     * Relative version of "quad to".
     * <p>
     * Adds quad from last point towards vector (dx1, dy1), to vector (dx2, dy2).
     * Quad control is last point plus vector (dx1, dy1).
     * Quad end is last point plus vector (dx2, dy2).
     * If builder is empty, last point is set to (0, 0) before adding quad.
     * If last verb is close, last point is set to last move point before adding quad.
     *
     * @param dx1 offset from last point to quad control on x-axis
     * @param dy1 offset from last point to quad control on y-axis
     * @param dx2 offset from last point to quad end on x-axis
     * @param dy2 offset from last point to quad end on y-axis
     */
    public PathBuilder quadToRel(float dx1, float dy1,
                                 float dx2, float dy2) {
        ensureMove();

        float baseX = mCoords[mCoordSize - 2];
        float baseY = mCoords[mCoordSize - 1];

        return quadTo(baseX + dx1, baseY + dy1, baseX + dx2, baseY + dy2);
    }

    /**
     * Relative version of "cubic to".
     * <p>
     * Adds cubic from last point towards vector (dx1, dy1), vector (dx2, dy2),
     * to vector (dx3, dy3).
     * Cubic first control is last point plus vector (dx1, dy1).
     * Cubic second control is last point plus vector (dx2, dy2).
     * Cubic end is last point plus vector (dx3, dy3).
     * If builder is empty, last point is set to (0, 0) before adding cubic.
     * If last verb is close, last point is set to last move point before adding cubic.
     *
     * @param dx1 offset from last point to first cubic control on x-axis
     * @param dy1 offset from last point to first cubic control on y-axis
     * @param dx2 offset from last point to second cubic control on x-axis
     * @param dy2 offset from last point to second cubic control on y-axis
     * @param dx3 offset from last point to cubic end on x-axis
     * @param dy3 offset from last point to cubic end on y-axis
     */
    public PathBuilder cubicToRel(float dx1, float dy1,
                                  float dx2, float dy2,
                                  float dx3, float dy3) {
        ensureMove();

        float baseX = mCoords[mCoordSize - 2];
        float baseY = mCoords[mCoordSize - 1];

        return cubicTo(baseX + dx1, baseY + dy1, baseX + dx2, baseY + dy2, baseX + dx3, baseY + dy3);
    }

    /**
     * Adds a {@link Path} to this path builder, transformed by matrix. Transformed curves may
     * have different verbs, and points.
     * <p>
     * If mode is {@link #ADD_PATH_APPEND}, src verb array and point array are added as-is.
     * If mode is {@link #ADD_PATH_EXTEND}, add line before appending verbs, and points.
     * <p>
     * Calling this method will not affect the {@link #getWindingRule()} of this builder.
     *
     * @param src    path verbs and points to add
     * @param matrix the transform applied to src points
     * @param mode   one of modes as described above
     * @return this
     */
    public PathBuilder addPath(@NonNull Path src, @Nullable Matrixc matrix,
                               @MagicConstant(intValues = {ADD_PATH_APPEND, ADD_PATH_EXTEND}) int mode) {
        return addPathData(src.mPathData, matrix, mode);
    }

    public PathBuilder addPathData(@Nullable PathData src, @Nullable Matrixc matrix,
                                   @MagicConstant(intValues = {ADD_PATH_APPEND, ADD_PATH_EXTEND}) int mode) {
        if (src == null || src.isEmpty()) {
            return this;
        }

        // since the first verb is always move, this will be replaced by the new path in append mode
        boolean canReplaceThis = mVerbSize == 0 ||
                (mode == ADD_PATH_APPEND && mVerbSize == 1);
        if (canReplaceThis && (matrix == null || matrix.isIdentity())) {
            // replace fast path

            mVerbSize = 0;
            mCoordSize = 0;

            incReserve(src.countPoints(), src.countVerbs());

            System.arraycopy(src.mVerbs, 0, mVerbs, 0, src.mVerbs.length);
            System.arraycopy(src.mCoords, 0, mCoords, 0, src.mCoords.length);

            mVerbSize = src.mVerbs.length;
            mCoordSize = src.mCoords.length;

            mSegmentMask = src.mSegmentMask;

            mLastMoveToIndex = findLastMoveToIndex(mVerbs, mVerbSize, mCoordSize);
            assert mLastMoveToIndex < mCoordSize;

            mConvexity = src.getRawConvexity();

            return this;
        }


        mConvexity = Path.CONVEXITY_UNKNOWN;

        if (mode == ADD_PATH_APPEND && (matrix == null || !matrix.hasPerspective())) {
            int lastMoveToIndex = findLastMoveToIndex(src.mVerbs, src.mVerbs.length, src.mCoords.length);
            assert lastMoveToIndex >= 0;
            mLastMoveToIndex = lastMoveToIndex + mCoordSize;

            incReserve(src.countPoints(), src.countVerbs());

            System.arraycopy(src.mVerbs, 0, mVerbs, mVerbSize, src.mVerbs.length);
            if (matrix != null) {
                matrix.mapPoints(src.mCoords, 0, mCoords, mCoordSize, src.countPoints());
            } else {
                System.arraycopy(src.mCoords, 0, mCoords, mCoordSize, src.mCoords.length);
            }

            mVerbSize += src.mVerbs.length;
            mCoordSize += src.mCoords.length;

            mSegmentMask |= src.mSegmentMask;

            return this;
        }

        // general case for extend mode and/or perspective

        if (matrix != null && matrix.hasPerspective()) {
            //TODO not implemented yet, because projected bezier is no longer a bezier
            assert false;
        }

        int startCoordPos = mCoordSize;

        var iter = src.new RawIterator();
        boolean firstVerb = true;
        byte verb;
        while ((verb = iter.next()) != Path.VERB_DONE) {
            switch (verb) {
                case Path.VERB_MOVE -> {
                    if (firstVerb && mode == ADD_PATH_EXTEND && !isEmpty()) {
                        ensureMove(); // In case last contour is closed
                        float mappedX = iter.x0();
                        float mappedY = iter.y0();
                        if (matrix != null && !matrix.isIdentity()) {
                            float[] p = {mappedX, mappedY};
                            matrix.mapPoint(p);
                            mappedX = p[0];
                            mappedY = p[1];
                        }
                        float lastX = mCoords[mCoordSize - 2];
                        float lastY = mCoords[mCoordSize - 1];
                        // don't add lineTo if it is degenerate
                        if (lastX != mappedX || lastY != mappedY) {
                            lineTo(iter.x0(), iter.y0());
                        }
                    } else {
                        moveTo(iter.x0(), iter.y0());
                    }
                }
                case Path.VERB_LINE ->
                        lineTo(iter.x1(), iter.y1());
                case Path.VERB_QUAD ->
                        quadTo(iter.x1(), iter.y1(), iter.x2(), iter.y2());
                case Path.VERB_CUBIC ->
                        cubicTo(iter.x1(), iter.y1(), iter.x2(), iter.y2(), iter.x3(), iter.y3());
                case Path.VERB_CLOSE ->
                        close();
            }
            firstVerb = false;
        }

        if (matrix != null && !matrix.isIdentity()) {
            matrix.mapPoints(mCoords, startCoordPos, mCoords, startCoordPos, (mCoordSize - startCoordPos) >> 1);
        }

        return this;
    }

    public PathBuilder addShape(java.awt.@NonNull Shape shape, @Nullable AffineTransform transform) {
        if (shape instanceof Path) {
            Matrix matrix = transform != null ? Matrix.make(transform) : null;
            return addPath((Path) shape, matrix, ADD_PATH_APPEND);
        }

        //TODO handle more special types

        addGeneral(shape.getPathIterator(transform));
        return this;
    }

    void reversePathTo(@NonNull PathBuilder src) {
        if (src != this) {
            reversePathTo(src.mVerbs, src.mVerbSize, src.mCoords, src.mCoordSize);
        }
    }

    void reversePathTo(@NonNull Path src) {
        var data = src.mPathData;
        reversePathTo(data.mVerbs, data.mVerbs.length, data.mCoords, data.mCoords.length);
    }

    // ignore the last point of the contour
    // there must be moveTo() for the contour
    void reversePathTo(byte[] vs, int verbSize, float[] cs, int coordSize) {
        int vi = verbSize;
        int ci = coordSize - 2;
        ITR:
        while (vi != 0) {
            switch (vs[--vi]) {
                case Path.VERB_MOVE -> {
                    assert vi == 0 && ci == 0;
                    break ITR;
                }
                case Path.VERB_LINE -> {
                    lineTo(
                            cs[ci - 2], cs[ci - 1]
                    );
                    ci -= 2;
                }
                case Path.VERB_QUAD -> {
                    quadTo(
                            cs[ci - 2], cs[ci - 1],
                            cs[ci - 4], cs[ci - 3]
                    );
                    ci -= 4;
                }
                case Path.VERB_CUBIC -> {
                    cubicTo(
                            cs[ci - 2], cs[ci - 1],
                            cs[ci - 4], cs[ci - 3],
                            cs[ci - 6], cs[ci - 5]
                    );
                    ci -= 6;
                }
                default -> {
                    assert false;
                }
            }
        }
    }

    void addGeneral(@NonNull PathIterator pi) {
        float[] coords = new float[6];
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO ->
                        moveTo(coords[0], coords[1]);
                case PathIterator.SEG_LINETO ->
                        lineTo(coords[0], coords[1]);
                case PathIterator.SEG_QUADTO ->
                        quadTo(coords[0], coords[1], coords[2], coords[3]);
                case PathIterator.SEG_CUBICTO ->
                        cubicTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                case PathIterator.SEG_CLOSE ->
                        close();
            }
            pi.next();
        }
    }

    public PathBuilder offset(float dx, float dy) {
        if (dx != 0 || dy != 0) {
            for (int i = 0; i < mCoordSize; i += 2) {
                mCoords[i] += dx;
                mCoords[i + 1] += dy;
            }
        }
        return this;
    }

    public PathBuilder transform(@NonNull Matrixc matrix) {
        if (matrix.isIdentity() || isEmpty()) {
            return this;
        }
        if (matrix.isTranslate()) {
            return offset(matrix.getTranslateX(), matrix.getTranslateY());
        }

        //TODO update special types and convexity
        // and handle perspective transform correctly

        matrix.mapPoints(mCoords, mCoordSize >> 1);

        return this;
    }

    /**
     * Grows the internal storage to contain additional space.
     * May improve performance by reducing the number of allocations for subsequent calls
     * that add points and verbs.
     *
     * @param incPoints number of additional points and verbs to reserve
     */
    public void incReserve(int incPoints) {
        incReserve(incPoints, incPoints);
    }

    /**
     * Grows the internal storage to contain additional space.
     * May improve performance by reducing the number of allocations for subsequent calls
     * that add points and verbs.
     *
     * @param incPoints number of additional points to reserve
     * @param incVerbs  number of additional verbs to reserve
     */
    public void incReserve(int incPoints, int incVerbs) {
        assert incVerbs >= 0 && incPoints >= 0;
        if (mVerbSize > mVerbs.length - incVerbs) { // prevent overflow
            mVerbs = Path.growVerbs(mVerbs, incVerbs - mVerbs.length + mVerbSize);
        }
        int incCoords = incPoints << 1;
        assert incCoords >= 0;
        if (mCoordSize > mCoords.length - incCoords) { // prevent overflow
            mCoords = Path.growCoords(mCoords, incCoords - mCoords.length + mCoordSize);
        }
    }

    public int countPoints() {
        assert mCoordSize % 2 == 0;
        return mCoordSize >> 1;
    }

    /**
     * Returns true if path builder has no point and verb. {@link #reset()},
     * makes path builder empty.
     *
     * @return true if the path builder contains no verb
     */
    public boolean isEmpty() {
        assert (mVerbSize == 0) == (mCoordSize == 0);
        return mVerbSize == 0;
    }

    /**
     * Returns false for any coordinate value of infinity or NaN.
     *
     * @return true if all point coordinates are finite
     */
    public boolean isFinite() {
        for (int i = 0; i < mCoordSize; i++) {
            if (!Float.isFinite(mCoords[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean computeBounds(@NonNull Rect2f dest) {
        return Path.getTrimmedBounds(mVerbs, mVerbSize, mCoords, mCoordSize, dest);
    }

    public void forEach(@NonNull PathConsumer action) {
        int n = mVerbSize;
        if (n != 0) {
            byte[] vs = mVerbs;
            float[] cs = mCoords;
            int vi = 0;
            int ci = 0;
            ITR:
            do {
                switch (vs[vi++]) {
                    case Path.VERB_MOVE -> {
                        if (vi == n) {
                            break ITR;
                        }
                        action.moveTo(
                                cs[ci++], cs[ci++]
                        );
                    }
                    case Path.VERB_LINE -> action.lineTo(
                            cs[ci++], cs[ci++]
                    );
                    case Path.VERB_QUAD -> {
                        action.quadTo(cs, ci);
                        ci += 4;
                    }
                    case Path.VERB_CUBIC -> {
                        action.cubicTo(cs, ci);
                        ci += 6;
                    }
                    case Path.VERB_CLOSE -> action.close();
                }
            } while (vi < n);
        }
        action.done();
    }

    @NonNull
    public Path build() {
        return new Path(buildData(), mWindingRule, mIsVolatile);
    }

    @Nullable
    public PathData buildData() {
        if (isEmpty()) {
            return PathData.empty();
        }

        Rect2f bounds = new Rect2f();
        if (!computeBounds(bounds)) {
            return null;
        }

        byte[] verbs = Arrays.copyOf(mVerbs, mVerbSize);
        float[] coords = Arrays.copyOf(mCoords, mCoordSize);

        return new PathData(verbs, coords,
                bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                mSegmentMask, mConvexity);
    }

    /*
     * Returns the index (in floats) of the last moveTo() point, based on the verbs.
     * If verbs is empty / coordCount == 0, then this returns -1.
     */
    static int findLastMoveToIndex(byte[] verbs, int verbSize,
                                   int coordSize) {
        if (verbSize == 0) {
            assert coordSize == 0;
            return -1;
        }

        // Input is from builder so the sequence is validated.
        assert verbs[0] == Path.VERB_MOVE;
        assert coordSize > 0;
        assert coordSize % 2 == 0;

        int coordIndex = coordSize - 2;
        for (int i = verbSize - 1; i >= 0; i--) {
            byte verb = verbs[i];
            if (verb == Path.VERB_MOVE) {
                break;
            }

            coordIndex -= switch (verb) {
                case Path.VERB_LINE -> 2;
                case Path.VERB_QUAD -> 4;
                case Path.VERB_CUBIC -> 6;
                case Path.VERB_CLOSE -> 0;
                default -> throw new AssertionError(verb);
            };
        }

        assert coordIndex >= 0;
        return coordIndex;
    }
}

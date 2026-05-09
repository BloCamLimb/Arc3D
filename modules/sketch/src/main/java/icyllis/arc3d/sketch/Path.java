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

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2fc;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.awt.geom.AffineTransform;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * The {@link Path} object contains mutable path elements.
 * <p>
 * Path may be empty, or contain one or more verbs that outline a figure.
 * Path always starts with a move verb to a Cartesian coordinate, and may be
 * followed by additional verbs that add lines or curves. Adding a close verb
 * makes the geometry into a continuous loop, a closed contour. Path may
 * contain any number of contours, each beginning with a move verb.
 * <p>
 * Path contours may contain only a move verb, or may also contain lines,
 * quadratic Béziers, and cubic Béziers. Path contours may be open or closed.
 * <p>
 * When used to draw a filled area, Path describes whether the fill is inside or
 * outside the geometry. Path also describes the winding rule used to fill
 * overlapping contours.
 * <p>
 * Note: Path lazily computes metrics likes bounds and convexity. Call
 * {@link #updateBoundsCache()} to make path thread safe.
 * <p>
 * Note: Path also implements AWT Shape for convenience, but only a
 * few methods are actually implemented.
 */
public class Path implements Shape, java.awt.Shape {

    /**
     * The fill rule constant for specifying an even-odd rule
     * for determining the interior of a path.<br>
     * The even-odd rule specifies that a point lies inside the
     * path if a ray drawn in any direction from that point to
     * infinity is crossed by path segments an odd number of times.
     */
    public static final int WIND_EVEN_ODD = PathIterator.WIND_EVEN_ODD;
    /**
     * The fill rule constant for specifying a non-zero rule
     * for determining the interior of a path.<br>
     * The non-zero rule specifies that a point lies inside the
     * path if a ray drawn in any direction from that point to
     * infinity is crossed by path segments a different number
     * of times in the counter-clockwise direction than the
     * clockwise direction.
     */
    public static final int WIND_NON_ZERO = PathIterator.WIND_NON_ZERO;

    /**
     * Primitive commands of path segments.
     */
    public static final byte
            VERB_MOVE = PathIterator.SEG_MOVETO,   // returns 1 point
            VERB_LINE = PathIterator.SEG_LINETO,   // returns 1 point
            VERB_QUAD = PathIterator.SEG_QUADTO,   // returns 2 points
            VERB_CUBIC = PathIterator.SEG_CUBICTO, // returns 3 points
            VERB_CLOSE = PathIterator.SEG_CLOSE; // returns 0 points
    @ApiStatus.Internal
    public static final byte
            VERB_DONE = VERB_CLOSE + 1;

    /**
     * Clockwise direction for adding closed contours, assumes the origin is top left, y-down.
     */
    public static final int DIRECTION_CW = 0;
    /**
     * Counter-clockwise direction for adding closed contours, assumes the origin is top left, y-down.
     */
    public static final int DIRECTION_CCW = 1;

    /**
     * Segment constants correspond to each drawing verb type in path; for
     * instance, if path contains only lines, only the Line bit is set.
     *
     * @see #getSegmentMask()
     */
    @MagicConstant(flags = {SEGMENT_LINE, SEGMENT_QUAD, SEGMENT_CUBIC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SegmentMask {
    }

    public static final int
            SEGMENT_LINE = 1,
            SEGMENT_QUAD = 1 << 1,
            SEGMENT_CUBIC = 1 << 3;

    static final byte CONVEXITY_CONVEX_CW = 0;
    static final byte CONVEXITY_CONVEX_CCW = 1;
    static final byte CONVEXITY_CONVEX_DEGENERATE = 2;
    static final byte CONVEXITY_CONCAVE = 3;
    static final byte CONVEXITY_UNKNOWN = 4;

    static final byte FIRST_DIRECTION_CW = DIRECTION_CW;
    static final byte FIRST_DIRECTION_CCW = DIRECTION_CCW;
    static final byte FIRST_DIRECTION_UNKNOWN = 2;

    // This is a singleton instance which Path uses to signify that its pathdata is in error:
    // either because the inputs were invalid (e.g. bad verbs), or its coordintes were non-finite
    // (either from the client, or after a makeTransform() call).
    private static final PathData ERROR = new PathData(
            PathData.EMPTY_VERBS, PathData.EMPTY_COORDS,
            0, 0, 0, 0,
            (byte) 0, Path.CONVEXITY_CONVEX_DEGENERATE);

    static {
        // This must be a different object from PathData.empty(), isFinite() relies on this.
        assert ERROR != PathData.empty();
    }

    public static final int APPROXIMATE_ARC_WITH_CUBICS = 0;
    public static final int APPROXIMATE_CONIC_WITH_QUADS = 1;

    @NonNull
    PathData mPathData;

    byte mWindingRule;
    boolean mIsVolatile;

    /**
     * Creates an empty Path with a default fill rule of {@link #WIND_NON_ZERO}.
     */
    public Path() {
        mPathData = PathData.empty();
        mWindingRule = WIND_NON_ZERO;
    }

    public Path(int rule) {
        mPathData = PathData.empty();
        setWindingRule(rule);
    }

    /**
     * Creates a copy of an existing Path object.
     * <p>
     * Internally, the two paths share reference values. The underlying
     * verb array and point array will not be copied.
     */
    public Path(@NonNull Path other) {
        mPathData = other.mPathData;
        mWindingRule = other.mWindingRule;
        mIsVolatile = other.mIsVolatile;
    }

    /**
     * Remember: null PathData means invalid, instead of empty path.
     * <p>
     * API user must use factories to create Path instances.
     *
     * @hidden
     */
    @ApiStatus.Internal
    protected Path(@Nullable PathData data, int rule, boolean isVolatile) {
        mPathData = data != null ? data : ERROR;
        assert rule == Path.WIND_NON_ZERO || rule == Path.WIND_EVEN_ODD;
        mWindingRule = (byte) rule;
        mIsVolatile = isVolatile;
    }

    /**
     * Returns the rule used to fill path.
     *
     * @return current fill rule
     */
    @Override
    public int getWindingRule() {
        return mWindingRule;
    }

    /**
     * Sets the rule used to fill path. <var>rule</var> is either {@link #WIND_NON_ZERO}
     * or {@link #WIND_EVEN_ODD} .
     */
    public void setWindingRule(int rule) {
        if (rule == Path.WIND_NON_ZERO || rule == Path.WIND_EVEN_ODD) {
            mWindingRule = (byte) rule;
            return;
        }

        throw new IllegalArgumentException("Unknown winding rule: " + rule);
    }

    /**
     * Creates a copy of an existing Path object.
     * <p>
     * Internally, the two paths share reference values. The underlying
     * verb array and point array will not be copied.
     */
    public void set(@NonNull Path other) {
        if (other != this) {
            mPathData = other.mPathData;
            mWindingRule = other.mWindingRule;
            mIsVolatile = other.mIsVolatile;
        }
    }

    /**
     * Resets the path to its initial state, clears points and verbs and
     * sets fill rule to {@link #WIND_NON_ZERO}.
     */
    public void reset() {
        mPathData = PathData.empty();
        mWindingRule = WIND_NON_ZERO;
        mIsVolatile = false;
    }

    /**
     * Returns true if path has no point and verb. {@link #reset()},
     * makes path empty.
     *
     * @return true if the path contains no verb
     */
    public boolean isEmpty() {
        return mPathData.isEmpty();
    }

    /**
     * Returns false for any coordinate value of infinity or NaN.
     *
     * @return true if all point coordinates are finite
     */
    public boolean isFinite() {
        return mPathData != ERROR;
    }

    //TODO
    /**
     * Transforms verb array, Point array, and weight by matrix.
     * transform may change verbs and increase their number.
     * Path is replaced by transformed data.
     *
     * @param matrix Matrix to apply to Path
     */
    public void transform(@NonNull Matrixc matrix) {
        transform(matrix, this);
    }

    /**
     * Transforms verb array, point array, and weight by matrix.
     * transform may change verbs and increase their number.
     * Transformed Path replaces dst; if dst is null, original data
     * is replaced.
     *
     * @param matrix Matrix to apply to Path
     * @param dst    overwritten, transformed copy of Path; may be null
     */
    public void transform(@NonNull Matrixc matrix, @Nullable Path dst) {
        if (matrix.isIdentity()) {
            if (dst != null && dst != this) {
                dst.set(this);
            }
            return;
        }

        if (dst == null) {
            dst = this;
        }

        if (matrix.hasPerspective()) {
            //TODO
        } else {
            /*mPathRef.createTransformedCopy(matrix, dst);

            if (this != dst) {
                dst.mLastMoveToIndex = mLastMoveToIndex;
                dst.mWindingRule = mWindingRule;
            }*/
        }
    }

    /**
     * Returns the number of verbs added to path.
     *
     * @return size of verb list
     */
    public int countVerbs() {
        return mPathData.countVerbs();
    }

    /**
     * Returns the number of points (x,y pairs) in path.
     *
     * @return size of point list
     */
    public int countPoints() {
        return mPathData.countPoints();
    }

    /**
     * Compatibility-only, do not use.
     *
     * @see #getBounds(Rect2f)
     */
    @Deprecated
    @Override
    public java.awt.@NonNull Rectangle getBounds() {
        return getBounds2D().getBounds();
    }

    /**
     * @see #getBounds(Rect2f)
     */
    @Override
    public @NonNull Rectangle2D getBounds2D() {
        var data = mPathData;
        return new Rectangle2D.Float(data.mLeft, data.mTop,
                data.mRight - data.mLeft, data.mBottom - data.mTop);
    }

    /**
     * Returns minimum and maximum axes values of path's 'trimmed' points.
     * <p>
     * The trimmed points are all of the points in the path, except the path
     * having more than one contour, and the last contour containing only a
     * MOVE verb. In that case the trailing MOVE point is ignored when
     * computing the bounds.
     * <p>
     * Returns empty if path contains no points or is not finite.
     * <p>
     * This method returns a cached result; it is recalculated only after
     * this path is altered.
     * <p>
     * Return value is stored to <var>dest</var> rectangle, always overwriting
     * the previous values.
     * <p>
     * It's safe to call this method from multiple threads simultaneously.
     *
     * @param dest the destination rectangle to store the bounds to
     * @see #isFinite()
     */
    @Override
    public void getBounds(@NonNull Rect2f dest) {
        mPathData.getBounds(dest);
    }

    @Override
    public boolean contains(double x, double y) {
        //TODO
        return false;
    }

    @Override
    public boolean contains(Point2D p) {
        return contains(p.getX(), p.getY());
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        //TODO
        return false;
    }

    @Override
    public boolean contains(Rectangle2D r) {
        //TODO
        return false;
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return false;
    }

    @Override
    public boolean intersects(Rectangle2D r) {
        return false;
    }

    /**
     * Updates internal bounds so that subsequent calls to {@link #getBounds()}
     * are instantaneous. Unaltered copies of path may also access cached bounds
     * through {@link #getBounds()}.
     * <p>
     * For now, identical to calling {@link #getBounds()} and ignoring the returned
     * value.
     * <p>
     * Call to prepare path subsequently drawn from multiple threads, to avoid
     * a race condition where each draw separately computes the bounds.
     */
    public void updateBoundsCache() {
    }

    /**
     * Returns a mask, where each set bit corresponds to a Segment constant
     * if path contains one or more verbs of that type.
     * <p>
     * This method returns a cached result; it is very fast.
     *
     * @return Segment bits or zero
     */
    @SegmentMask
    public int getSegmentMask() {
        return mPathData.mSegmentMask;
    }

    @Override
    public boolean contains(float x, float y) {
        //TODO to be implemented
        return false;
    }

    @Override
    public boolean contains(float left, float top, float right, float bottom) {
        //TODO to be implemented
        return false;
    }

    @Override
    public boolean contains(@NonNull Rect2fc rect) {
        //TODO to be implemented
        return false;
    }

    @Override
    public @NonNull PathIterator getPathIterator() {
        return this.new Iterator();
    }

    @Override
    public @NonNull PathIterator getPathIterator(@Nullable AffineTransform at) {
        return at != null ? this.new TxIterator(at) : this.new Iterator();
    }

    @Override
    public @NonNull PathIterator getPathIterator(@Nullable AffineTransform at,
                                                 double flatness) {
        return new FlatteningPathIterator(getPathIterator(at), flatness);
    }

    private class Iterator implements PathIterator {

        private final int count = countVerbs();
        private int verbPos;
        private int coordPos;

        @Override
        public int getWindingRule() {
            return mWindingRule;
        }

        @Override
        public boolean isDone() {
            return verbPos == count;
        }

        @Override
        public void next() {
            byte verb = mPathData.mVerbs[verbPos++];
            coordPos += switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
        }

        @Override
        public int currentSegment(float[] coords) {
            byte verb = mPathData.mVerbs[verbPos];
            int numCoords = switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
            if (numCoords > 0) {
                System.arraycopy(mPathData.mCoords, coordPos,
                        coords, 0, numCoords);
            }
            return verb;
        }

        @Override
        public int currentSegment(double[] coords) {
            byte verb = mPathData.mVerbs[verbPos];
            int numCoords = switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
            if (numCoords > 0) {
                for (int i = 0; i < numCoords; i++) {
                    coords[i] = mPathData.mCoords[coordPos + i];
                }
            }
            return verb;
        }
    }

    private class TxIterator implements PathIterator {

        private final AffineTransform affine;
        private final int count = countVerbs();
        private int verbPos;
        private int coordPos;

        private TxIterator(AffineTransform affine) {
            this.affine = affine;
        }

        @Override
        public int getWindingRule() {
            return mWindingRule;
        }

        @Override
        public boolean isDone() {
            return verbPos == count;
        }

        @Override
        public void next() {
            byte verb = mPathData.mVerbs[verbPos++];
            coordPos += switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
        }

        @Override
        public int currentSegment(float[] coords) {
            byte verb = mPathData.mVerbs[verbPos];
            int numCoords = switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
            if (numCoords > 0) {
                affine.transform(mPathData.mCoords, coordPos,
                        coords, 0, numCoords / 2);
            }
            return verb;
        }

        @Override
        public int currentSegment(double[] coords) {
            byte verb = mPathData.mVerbs[verbPos];
            int numCoords = switch (verb) {
                case VERB_MOVE,VERB_LINE -> 2;
                case VERB_QUAD -> 4;
                case VERB_CUBIC -> 6;
                default -> 0;
            };
            if (numCoords > 0) {
                affine.transform(mPathData.mCoords, coordPos,
                        coords, 0, numCoords / 2);
            }
            return verb;
        }
    }

    /**
     * Iterates the Path and feeds the given consumer.
     */
    @Override
    public void forEach(@NonNull PathConsumer action) {
        int n = countVerbs();
        if (n != 0) {
            byte[] vs = mPathData.mVerbs;
            float[] cs = mPathData.mCoords;
            int vi = 0;
            int ci = 0;
            ITR:
            do {
                switch (vs[vi++]) {
                    case VERB_MOVE -> {
                        if (vi == n) {
                            break ITR;
                        }
                        action.moveTo(
                                cs[ci++], cs[ci++]
                        );
                    }
                    case VERB_LINE -> action.lineTo(
                            cs[ci++], cs[ci++]
                    );
                    case VERB_QUAD -> {
                        action.quadTo(cs, ci);
                        ci += 4;
                    }
                    case VERB_CUBIC -> {
                        action.cubicTo(cs, ci);
                        ci += 6;
                    }
                    case VERB_CLOSE -> action.close();
                }
            } while (vi < n);
        }
        action.done();
    }

    /**
     * Returns the estimated byte size of path object in memory.
     * This method does not take into account whether internal storage is shared or not.
     */
    public long estimatedByteSize() {
        long size = 24;
        size += mPathData.estimatedByteSize();
        return size;
    }

    /**
     * @hidden
     */
    @ApiStatus.Internal
    public final @NonNull PathData getPathData() {
        return mPathData;
    }

    @Override
    public int hashCode() {
        int hash = mPathData.hashCode();
        hash = 31 * hash + mWindingRule;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Path other) {
            return mWindingRule == other.mWindingRule && mPathData.equals(other.mPathData);
        }
        return false;
    }

    // the state of the convex computation
    static class ConvexState {

    }

    int computeConvexity() {
        if (!isFinite()) {
            return CONVEXITY_CONCAVE;
        }
        return 0;
    }


    static byte @NonNull[] growVerbs(byte @NonNull[] old, int minGrow) {
        final int oldCap = old.length, grow;
        if (oldCap < 10) {
            grow = 10 - oldCap;
        } else if (oldCap > 500) {
            grow = Math.max(250, oldCap >> 3); // 1.125x
        } else {
            grow = oldCap >> 1; // 1.5x
        }
        int newCap = oldCap + Math.max(grow, minGrow); // may overflow
        if (newCap < 0) {
            newCap = oldCap + minGrow; // may overflow
            if (newCap < 0) {
                throw new IllegalStateException("Path is too big " + oldCap + " + " + minGrow);
            }
            newCap = Integer.MAX_VALUE;
        }
        return Arrays.copyOf(old, newCap);
    }


    static float @NonNull[] growCoords(float @NonNull[] old, int minGrow) {
        final int oldCap = old.length, grow;
        if (oldCap < 20) {
            grow = 20 - oldCap;
        } else if (oldCap > 1000) {
            // align down to 2
            grow = Math.max(500, (oldCap >> 4) << 1); // 1.125x
        } else {
            // align down to 2
            grow = (oldCap >> 2) << 1; // 1.5x
        }
        assert oldCap % 2 == 0 && minGrow % 2 == 0;
        int newCap = oldCap + Math.max(grow, minGrow); // may overflow
        if (newCap < 0) {
            newCap = oldCap + minGrow; // may overflow
            if (newCap < 0) {
                throw new IllegalStateException("Path is too big " + oldCap + " + " + minGrow);
            }
            // align down to 2
            newCap = Integer.MAX_VALUE - 1;
        }
        return Arrays.copyOf(old, newCap);
    }

    // Returns false if the bounds is not finite
    static boolean getTrimmedBounds(byte @NonNull[] verbs, int verbSize,
                                    float @NonNull[] coords, int coordSize,
                                    @NonNull Rect2f bounds) {
        // Does a trailing kMove verb contribute to the bounds?
        // - only if it is the only verb in the path
        // - otherwise we ignore it when computing bounds
        if (verbSize > 1 && verbs[verbSize - 1] == VERB_MOVE) {
            assert coordSize > 0;
            // While trailing moves do not contribute to the bounds, we still reject them.
            if (!Float.isFinite(coords[coordSize - 2]) ||
                    !Float.isFinite(coords[coordSize - 1])) {
                return false;
            }
            coordSize -= 2;
        }
        assert coordSize % 2 == 0;
        return bounds.setBounds(coords, 0, coordSize >> 1);
    }

}

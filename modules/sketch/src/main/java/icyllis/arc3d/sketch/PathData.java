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

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.Rect2f;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Immutable container for path geometry data: points, verbs; and cached values
 * that computed from above two arrays: bounds, segment masks; and lazily computed
 * values: convexity.
 * <p>
 * All of the public factories check for valid input
 * <ul>
 * <li> valid verb sequence</li>
 * <li> corresponding # points</li>
 * <li> finite point values</li>
 * </ul>
 * If any of these checks fail, null is returned.
 * <p>
 * A valid sequence of verbs (and corresponding points) is:
 * <pre>
 *  Any number of contours (0 or more)
 *  ... Each contour must begin with a single Move verb
 *      ... followed by any number of segments: lines, quads, cubics (0 or more)
 *      ... followed by 0 or 1 Close verb
 * </pre>
 * A minor exception to these rules, is that the last contour may end with a single
 * Move verb -- this will be included in the resulting PathData, but will be ignored in
 * computing 'trimmed' bounds, {@link #getBounds(Rect2f)}.
 * <p>
 * Given a valid verb sequence, there must be the corresponding number of points
 * to match. If there are more or fewer, null is returned.
 */
@ThreadSafe
public final class PathData {

    static final byte[] EMPTY_VERBS = {};
    static final float[] EMPTY_COORDS = {};

    private static final PathData EMPTY = new PathData(
            EMPTY_VERBS, EMPTY_COORDS,
            0, 0, 0, 0,
            (byte) 0, Path.CONVEXITY_CONVEX_DEGENERATE);

    final byte[] mVerbs;
    final float[] mCoords; // x0 y0 x1 y1 x2 y2 ...

    final transient float mLeft, mTop, mRight, mBottom;

    @Path.SegmentMask
    final transient byte mSegmentMask;

    // mutable, but harmless race
    private transient byte mConvexity;

    PathData(byte[] verbs, float[] coords,
             float left, float top, float right, float bottom,
             byte segmentMask, byte convexity) {
        assert valid_data(verbs, coords);

        // put convexity first so the initial value is visible to other threads
        // without a safe publication, since the following fields are final
        mConvexity = convexity;

        mSegmentMask = segmentMask;
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
        mVerbs = verbs;
        mCoords = coords;
    }

    @NonNull
    public static PathData empty() {
        return EMPTY;
    }

    static boolean valid_data(byte[] verbs, float[] coords) {
        if (verbs.length == 0) {
            return coords.length == 0;
        }

        if (verbs[0] != Path.VERB_MOVE) {
            return false;
        }

        byte prev = Path.VERB_MOVE;
        int numCoords = 2;

        for (int i = 1; i < verbs.length; i++) {
            byte curr = verbs[i];

            switch (curr) {
                case Path.VERB_MOVE:
                case Path.VERB_LINE:
                    numCoords += 2;
                    break;
                case Path.VERB_QUAD:
                    numCoords += 4;
                    break;
                case Path.VERB_CUBIC:
                    numCoords += 6;
                    break;
                case Path.VERB_CLOSE:
                    break;
                default:
                    return false;
            }

            // Previous verb             Valid next verb
            // -----------------------------------------
            // Move                  --> not Move
            // Line/Quad/Conic/Cubic --> *
            // Close                 --> Move
            //
            if (prev == Path.VERB_MOVE && curr == Path.VERB_MOVE) {
                return false;
            }
            if (prev == Path.VERB_CLOSE && curr != Path.VERB_MOVE) {
                return false;
            }

            prev = curr;
        }

        return coords.length == numCoords;
    }

    public boolean isEmpty() {
        assert (mVerbs.length == 0) == (mCoords.length == 0);
        return mVerbs.length == 0;
    }

    public int countVerbs() {
        return mVerbs.length;
    }

    public int countPoints() {
        assert mCoords.length % 2 == 0;
        return mCoords.length >> 1;
    }

    public byte getVerb(int index) {
        return mVerbs[index];
    }

    public float getPointX(int index) {
        return mCoords[index * 2];
    }

    public float getPointY(int index) {
        return mCoords[index * 2 + 1];
    }

    public void getVerbs(int index, byte[] dst, int offset, int count) {
        System.arraycopy(mVerbs, index, dst, offset, count);
    }

    public void getPoints(int index, float[] dst, int offset, int count) {
        System.arraycopy(mCoords, index << 1, dst, offset, count << 1);
    }

    public ByteBuffer getVerbs() {
        return ByteBuffer.wrap(mVerbs).asReadOnlyBuffer();
    }

    public FloatBuffer getPoints() {
        return FloatBuffer.wrap(mCoords).asReadOnlyBuffer();
    }

    public float getLeft() {
        return mLeft;
    }

    public float getTop() {
        return mTop;
    }

    public float getRight() {
        return mRight;
    }

    public float getBottom() {
        return mBottom;
    }

    public byte getSegmentMask() {
        return mSegmentMask;
    }

    public void getBounds(@NonNull Rect2f dest) {
        dest.set(mLeft, mTop, mRight, mBottom);
    }

    public byte getRawConvexity() {
        return mConvexity;
    }

    public long estimatedByteSize() {
        if (this == EMPTY) {
            return 0;
        }
        long size = 12 + 8 + 8 + 16 + 4;
        if (mVerbs != EMPTY_VERBS) {
            size += 16 + MathUtil.align8(mVerbs.length);
        }
        if (mCoords != EMPTY_COORDS) {
            assert mCoords.length % 2 == 0;
            size += 16 + ((long) mCoords.length << 2);
        }
        return size;
    }

    /**
     * Not well-defined, should not be used.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        for (byte verb : mVerbs) {
            hash = 11 * hash + verb;
        }
        for (float coord : mCoords) {
            hash = 11 * hash + Float.floatToIntBits(coord);
        }
        return hash;
    }

    /**
     * Returns true if two {@link PathData} have equivalent path segments.
     * That is, they have equal verb array and point array. Floating-point coordinates
     * follow IEEE comparison semantics: any comparison involving NaN evaluates as unequal,
     * including NaN with itself, whereas +0.0f and −0.0f compare equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof PathData that) {
            if (mSegmentMask != that.mSegmentMask) {
                // quick reject
                return false;
            }
            float[] aPts = mCoords;
            float[] bPts = that.mCoords;
            if (aPts.length != bPts.length) {
                // quick reject
                return false;
            }
            if (!Arrays.equals(mVerbs, that.mVerbs)) {
                return false;
            }
            // use IEEE comparison rather than memory comparison
            for (int i = 0; i < aPts.length; i++) {
                if (aPts[i] != bPts[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Low-level access to path elements.
     */
    @ApiStatus.Internal
    public class RawIterator {

        private final int count = countVerbs();
        private int verbPos;
        private int coordPos;
        private int coordOff;
        private int coordInc;

        public boolean hasNext() {
            return verbPos < count;
        }

        public byte next() {
            if (verbPos == count) {
                return Path.VERB_DONE;
            }
            byte verb = mVerbs[verbPos++];
            coordPos += coordInc;
            coordInc = switch (verb) {
                case Path.VERB_MOVE, Path.VERB_LINE -> 2;
                case Path.VERB_QUAD -> 4;
                case Path.VERB_CUBIC -> 6;
                case Path.VERB_CLOSE -> 0;
                default -> throw new AssertionError(verb);
            };
            // -2 is used to peek the start point of a segment
            coordOff = verb == Path.VERB_MOVE ? 0 : -2;
            return verb;
        }

        //TODO use arraycopy
        public float x0() {
            return mCoords[coordPos + coordOff];
        }

        public float y0() {
            return mCoords[coordPos + coordOff + 1];
        }

        public float x1() {
            return mCoords[coordPos + coordOff + 2];
        }

        public float y1() {
            return mCoords[coordPos + coordOff + 3];
        }

        public float x2() {
            return mCoords[coordPos + coordOff + 4];
        }

        public float y2() {
            return mCoords[coordPos + coordOff + 5];
        }

        public float x3() {
            return mCoords[coordPos + coordOff + 6];
        }

        public float y3() {
            return mCoords[coordPos + coordOff + 7];
        }
    }
}

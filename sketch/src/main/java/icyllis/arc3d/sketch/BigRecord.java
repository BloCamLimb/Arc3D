/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.core.Matrix4c;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.core.SharedPtr;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * This class represents a sequence of recorded Canvas calls, used for replay and
 * optimization.
 * <p>
 * It does not provide optimal replay performance at the data-structure level, but
 * {@link RecordCanvas} processes and converts parameters into the lowest-level,
 * most efficient form consumable by Canvas, improving performance in certain
 * scenarios (such as multiple replay for texts). It's also useful for capture
 * and debugging purposes, and it supports random access to the i-th command.
 */
public final class BigRecord extends RefCnt {

    /**
     * A recorded command/operation.
     */
    public static abstract class Rec {
        // the fields of the subclass may be mutable for optimization purposes;
        // once completed, we can make this thread safe by a safe publication.

        public void close() {
        }

        public abstract void draw(@NonNull Canvas canvas,
                                  @NonNull Matrix4c initialCTM, // used by SetMatrix
                                  Object[] drawables);          // used by DrawDrawable
    }

    //@formatter:off
    static final Rec [] EMPTY = {};
    @Nullable Rec @NonNull [] mRecords = EMPTY;
    int mCount;
    //@formatter:on

    public BigRecord() {
    }

    @Override
    protected void deallocate() {
        final Rec[] records = mRecords;
        final int count = mCount;
        for (int i = 0; i < count; i++) {
            Rec old;
            if ((old = records[i]) != null)
                old.close();
            records[i] = null;
        }
    }

    /**
     * @return the number of canvas commands
     */
    public int count() {
        return mCount;
    }

    /**
     * Note: null value means no-op.
     *
     * @return the i-th command
     */
    public @Nullable Rec get(int i) {
        assert i < mCount;
        return mRecords[i];
    }

    public void append(@NonNull Rec command) {
        int s = mCount;
        if (s == mRecords.length)
            grow();
        mRecords[s] = command;
        mCount = s + 1;
    }

    /**
     * Replace the i-th command.
     * <p>
     * Null means no-op, but preserves order and op index.
     */
    public void replace(int i, @Nullable Rec command) {
        assert i < mCount;
        Rec old;
        if ((old = mRecords[i]) != null)
            old.close();
        mRecords[i] = command;
    }

    private void grow() {
        int oldCap = mRecords.length;
        if (oldCap > 0) {
            mRecords = Arrays.copyOf(mRecords, oldCap + (oldCap >> 1));
        } else {
            mRecords = new Rec[10];
        }
    }

    /**
     * Remove any noops (nulls).
     * <p>
     * May change count() and the indices of ops, but preserves their order.
     */
    public void compact() {
        // in-place remove_if
        // since we only remove null values, nothing needs to be closed
        final Rec[] records = mRecords;
        final int count = mCount;
        int j = 0;

        for (int i = 0; i < count; ++i) {
            Rec v;
            if ((v = records[i]) != null) {
                records[j++] = v;
            }
        }

        for (int i = j; i < count; i++)
            records[i] = null;
        mCount = j;
    }

    public void draw(@NonNull Canvas canvas, Object[] drawables) {
        int restoreCount = canvas.save();

        Matrix4 initialCTM = new Matrix4();
        canvas.getLocalToDevice(initialCTM);

        final Rec[] records = mRecords;
        final int count = mCount;
        for (int i = 0; i < count; ++i) {
            Rec v;
            if ((v = records[i]) != null) {
                v.draw(canvas, initialCTM, drawables);
            }
        }

        canvas.restoreToCount(restoreCount);
    }

    public static final class Save extends Rec {
        public static final Save INSTANCE = new Save();

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.save();
        }
    }

    public static final class Restore extends Rec {
        public static final Restore INSTANCE = new Restore();

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.restore();
        }
    }

    public static final class Translate extends Rec {
        public float dx, dy, dz;

        public Translate(float dx, float dy, float dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.translate(dx, dy, dz);
        }
    }

    public static final class Scale extends Rec {
        public float sx, sy, sz;

        public Scale(float sx, float sy, float sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.scale(sx, sy, sz);
        }
    }

    public static final class Concat extends Rec {
        public final Matrix matrix;

        public Concat(@NonNull Matrixc matrix) {
            this.matrix = new Matrix(matrix);
            this.matrix.getType(); // precache to make thread safe, this field will be safely published
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.concat(matrix);
        }
    }

    public static final class Concat44 extends Rec {
        public final Matrix4 matrix;

        public Concat44(@NonNull Matrix4c matrix) {
            this.matrix = new Matrix4(matrix);
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.concat(matrix);
        }
    }

    public static final class SetMatrix44 extends Rec {
        public final Matrix4 matrix;

        public SetMatrix44(@NonNull Matrix4c matrix) {
            this.matrix = new Matrix4(matrix);
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            // initialMatrix preConcat this.matrix
            canvas.setMatrix(new Matrix4(initialCTM, matrix));
        }
    }

    public static final class DrawPaint extends Rec {
        public final Paint paint;

        public DrawPaint(@NonNull Paint paint) {
            this.paint = new Paint(paint);
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawPaint(paint);
        }
    }

    public static final class DrawPoints extends Rec {
        public final Paint paint;
        public int mode;
        public final float[] pts;

        public DrawPoints(Paint paint, int mode, float[] pts, int offset, int count) {
            this.paint = new Paint(paint);
            this.mode = mode;
            this.pts = Arrays.copyOfRange(pts, offset, offset + count * 2);
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawPoints(mode, pts, 0, pts.length >> 1, paint);
        }
    }

    public static final class DrawLine extends Rec {
        public final Paint paint;
        public float x0, y0, x1, y1;
        public int cap;
        public float width;

        public DrawLine(Paint paint, float x0, float y0, float x1, float y1,
                        int cap, float width) {
            this.paint = new Paint(paint);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.cap = cap;
            this.width = width;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawLine(x0, y0, x1, y1, cap, width, paint);
        }
    }

    public static final class DrawRect extends Rec {
        public final Paint paint;
        public float left, top, right, bottom;

        public DrawRect(@NonNull Paint paint, @NonNull Rect2fc rect) {
            this.paint = new Paint(paint);
            assert rect.isSorted(); // sorted by Canvas
            left = rect.left();
            top = rect.top();
            right = rect.right();
            bottom = rect.bottom();
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    public static final class DrawRRect extends Rec {
        public final Paint paint;
        public final RRect rrect;

        public DrawRRect(Paint paint, RRect rrect) {
            this.paint = new Paint(paint);
            this.rrect = new RRect(rrect);
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawRRect(rrect, paint);
        }
    }

    public static final class DrawEllipse extends Rec {
        public final Paint paint;
        public float cx, cy, rx, ry;

        public DrawEllipse(Paint paint, float cx, float cy, float rx, float ry) {
            this.paint = new Paint(paint);
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawEllipse(cx, cy, rx, ry, paint);
        }
    }

    public static final class DrawArc extends Rec {
        public final Paint paint;
        public float cx, cy, radius, startAngle, sweepAngle;
        public int cap;
        public float width;

        public DrawArc(Paint paint, float cx, float cy, float radius,
                       float startAngle, float sweepAngle, int cap, float width) {
            this.paint = new Paint(paint);
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.startAngle = startAngle;
            this.sweepAngle = sweepAngle;
            this.cap = cap;
            this.width = width;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawArc(cx, cy, radius, startAngle, sweepAngle, cap, width, paint);
        }
    }

    public static final class DrawPie extends Rec {
        public final Paint paint;
        public float cx, cy, radius, startAngle, sweepAngle;

        public DrawPie(Paint paint, float cx, float cy, float radius, float startAngle, float sweepAngle) {
            this.paint = new Paint(paint);
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.startAngle = startAngle;
            this.sweepAngle = sweepAngle;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawPie(cx, cy, radius, startAngle, sweepAngle, paint);
        }
    }

    public static final class DrawChord extends Rec {
        public final Paint paint;
        public float cx, cy, radius, startAngle, sweepAngle;

        public DrawChord(Paint paint, float cx, float cy, float radius, float startAngle, float sweepAngle) {
            this.paint = new Paint(paint);
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.startAngle = startAngle;
            this.sweepAngle = sweepAngle;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawChord(cx, cy, radius, startAngle, sweepAngle, paint);
        }
    }

    public static final class DrawImageRect extends Rec {
        @Nullable
        public Paint paint;
        @SharedPtr
        public Image image;
        public final Rect2f src;
        public final Rect2f dst;
        public SamplingOptions sampling;
        public int constraint;

        public DrawImageRect(@Nullable Paint paint, @RawPtr Image image, @NonNull Rect2fc src, @NonNull Rect2fc dst,
                             @NonNull SamplingOptions sampling, int constraint) {
            this.paint = paint != null ? new Paint(paint) : null;
            this.image = RefCnt.create(image);
            this.src = new Rect2f(src);
            this.dst = new Rect2f(dst);
            this.sampling = sampling;
            this.constraint = constraint;
        }

        @Override
        public void close() {
            if (paint != null) {
                paint.close();
            }
            image = RefCnt.move(image);
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawImageRect(image, src, dst, sampling, paint, constraint);
        }
    }

    public static final class DrawTextBlob extends Rec {
        public final Paint paint;
        public TextBlob blob;
        public float x, y;

        public DrawTextBlob(Paint paint, TextBlob blob, float x, float y) {
            this.paint = new Paint(paint);
            this.blob = blob;
            this.x = x;
            this.y = y;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawTextBlob(blob, x, y, paint);
        }
    }

    public static final class DrawVertices extends Rec {
        public final Paint paint;
        public Vertices vertices;
        public Blender blender;

        public DrawVertices(Paint paint, Vertices vertices, Blender blender) {
            this.paint = new Paint(paint);
            this.vertices = vertices;
            this.blender = blender;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawVertices(vertices, blender, paint);
        }
    }

    public static final class DrawEdgeAAQuad extends Rec {
        public final Paint paint;
        public @Nullable Rect2f rect;
        public float @Nullable [] clip;
        public int edgeFlags;

        public DrawEdgeAAQuad(Paint paint, @Nullable Rect2fc rect, float @Nullable [] clip, int clipOffset, int edgeFlags) {
            this.paint = new Paint(paint);
            this.rect = rect != null ? new Rect2f(rect) : null;
            this.clip = clip != null ? Arrays.copyOfRange(clip, clipOffset, clipOffset + 8) : null;
            this.edgeFlags = edgeFlags;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawEdgeAAQuad(rect, clip, 0, edgeFlags, paint);
        }
    }

    public static final class DrawBlurredRRect extends Rec {
        public final Paint paint;
        public final RRect rrect;
        public float blurRadius, noiseAlpha;

        public DrawBlurredRRect(Paint paint, RRect rrect, float blurRadius, float noiseAlpha) {
            this.paint = new Paint(paint);
            this.rrect = new RRect(rrect);
            this.blurRadius = blurRadius;
            this.noiseAlpha = noiseAlpha;
        }

        @Override
        public void close() {
            paint.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.drawBlurredRRect(rrect, paint, blurRadius, noiseAlpha);
        }
    }

    public static final class ClipRect extends Rec {
        public float left, top, right, bottom;
        public int op;
        public boolean aa;

        public ClipRect(@NonNull Rect2fc rect, int op, boolean aa) {
            assert rect.isSorted(); // sorted by Canvas
            left = rect.left();
            top = rect.top();
            right = rect.right();
            bottom = rect.bottom();
            this.op = op;
            this.aa = aa;
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         @NonNull Matrix4c initialCTM,
                         Object[] drawables) {
            canvas.clipRect(left, top, right, bottom, op, aa);
        }
    }
}

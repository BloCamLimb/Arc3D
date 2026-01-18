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

import icyllis.arc3d.core.Matrix4c;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.core.SamplingOptions;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static icyllis.arc3d.sketch.BigRecord.*;

/**
 * Canvas for recording into a {@link BigRecord}.
 */
public class RecordCanvas extends NoDrawCanvas {

    @RawPtr
    private BigRecord mRecord;

    public RecordCanvas(int width, int height) {
        super(width, height);
    }

    public void reset(@RawPtr BigRecord record) {
        mRecord = record;
    }

    @Override
    protected void willSave() {
        mRecord.append(Save.INSTANCE);
    }

    @Override
    protected void didRestore() {
        mRecord.append(Restore.INSTANCE);
    }

    @Override
    protected void didTranslate(float dx, float dy, float dz) {
        mRecord.append(new Translate(dx, dy, dz));
    }

    @Override
    protected void didScale(float sx, float sy, float sz) {
        mRecord.append(new Scale(sx, sy, sz));
    }

    @Override
    protected void didConcat(Matrixc matrix) {
        mRecord.append(new Concat(matrix));
    }

    @Override
    protected void didConcat44(Matrix4c matrix) {
        mRecord.append(new Concat44(matrix));
    }

    @Override
    protected void didSetMatrix44(Matrix4c matrix) {
        mRecord.append(new SetMatrix44(matrix));
    }

    @Override
    protected void onDrawPaint(Paint paint) {
        mRecord.append(new DrawPaint(paint));
    }

    @Override
    protected void onDrawPoints(int mode, float[] pts, int offset, int count, Paint paint) {
        mRecord.append(new DrawPoints(paint, mode, pts, offset, count));
    }

    @Override
    protected void onDrawLine(float x0, float y0, float x1, float y1, int cap, float width, Paint paint) {
        mRecord.append(new DrawLine(paint, x0, y0, x1, y1, cap, width));
    }

    @Override
    protected void onDrawRect(Rect2fc rect, Paint paint) {
        mRecord.append(new DrawRect(paint, rect));
    }

    @Override
    protected void onDrawRRect(RRect rrect, Paint paint) {
        mRecord.append(new DrawRRect(paint, rrect));
    }

    @Override
    protected void onDrawEllipse(float cx, float cy, float rx, float ry, Paint paint) {
        mRecord.append(new DrawEllipse(paint, cx, cy, rx, ry));
    }

    @Override
    protected void onDrawArc(float cx, float cy, float radius, float startAngle, float sweepAngle, int cap, float width, Paint paint) {
        mRecord.append(new DrawArc(paint, cx, cy, radius, startAngle, sweepAngle, cap, width));
    }

    @Override
    protected void onDrawPie(float cx, float cy, float radius, float startAngle, float sweepAngle, Paint paint) {
        mRecord.append(new DrawPie(paint, cx, cy, radius, startAngle, sweepAngle));
    }

    @Override
    protected void onDrawChord(float cx, float cy, float radius, float startAngle, float sweepAngle, Paint paint) {
        mRecord.append(new DrawChord(paint, cx, cy, radius, startAngle, sweepAngle));
    }

    @Override
    protected void onDrawImageRect(@RawPtr Image image, Rect2fc src, Rect2fc dst, SamplingOptions sampling, Paint paint,
                                   int constraint) {
        mRecord.append(new DrawImageRect(paint, image, src, dst, sampling, constraint));
    }

    @Override
    protected void onDrawTextBlob(@NonNull TextBlob blob, float originX, float originY, Paint paint) {
        mRecord.append(new DrawTextBlob(paint, blob, originX, originY));
    }

    @Override
    protected void onDrawGlyphRunList(@NonNull GlyphRunList glyphRunList, Paint paint) {
        // the given glyph run list should contain exactly one glyph run, and
        TextBlob blob = glyphRunList.getOrCreateBlob();
        // the method should always create a new text blob that copies the source arrays
        assert blob != null;
        onDrawTextBlob(blob, glyphRunList.mOriginX, glyphRunList.mOriginY, paint);
    }

    @Override
    protected void onDrawVertices(Vertices vertices, Blender blender, Paint paint) {
        mRecord.append(new DrawVertices(paint, vertices, blender));
    }

    @Override
    protected void onDrawEdgeAAQuad(Rect2fc rect, float @Nullable [] clip, int clipOffset, int edgeFlags, Paint paint) {
        mRecord.append(new DrawEdgeAAQuad(paint, rect, clip, clipOffset, edgeFlags));
    }

    @Override
    protected void onDrawBlurredRRect(RRect rr, Paint paint, float blurRadius, float noiseAlpha) {
        mRecord.append(new DrawBlurredRRect(paint, rr, blurRadius, noiseAlpha));
    }

    @Override
    protected void onClipRect(Rect2fc rect, int clipOp, boolean doAA) {
        mRecord.append(new ClipRect(rect, clipOp, doAA));
    }
}

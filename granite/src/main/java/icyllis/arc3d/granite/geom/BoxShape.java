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

package icyllis.arc3d.granite.geom;

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.sketch.Bounded;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Point;
import icyllis.arc3d.sketch.RRect;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class BoxShape implements Bounded {

    /**
     * For line: LeftTop becomes the start point, RightBottom becomes the end point,
     * Radius becomes half the line width.
     */
    public static final int
            kBox_Type = 0,
            kLine_Type = 1,        // butt cap or square cap, and LTBR is projected
            kLineRound_Type = 2,   // round cap, and LTBR is projected
            kBlurBox_Type = 3;

    public float mLeft;
    public float mTop;
    public float mRight;
    public float mBottom;

    public float mRadius;
    public float mBlurRadius;
    public float mNoiseAlpha;

    public int mType;

    public BoxShape() {
    }

    public BoxShape(RRect src) {
        mLeft = src.left();
        mTop = src.top();
        mRight = src.right();
        mBottom = src.bottom();
        mRadius = src.getSimpleRadiusX();
        mType = kBox_Type;
    }

    public void setCircle(float cx, float cy, float radius) {
        mLeft = cx - radius;
        mTop = cy - radius;
        mRight = cx + radius;
        mBottom = cy + radius;
        mRadius = radius;
        mType = kBox_Type;
    }

    public void setLine(float x0, float y0, float x1, float y1,
                        float radius, boolean round) {
        mLeft = x0;
        mTop = y0;
        mRight = x1;
        mBottom = y1;
        mRadius = radius;
        mType = round ? kLineRound_Type : kLine_Type;
    }

    public void setLine(float x0, float y0, float x1, float y1,
                        @Paint.Cap int cap, float width) {
        float radius = width * 0.5f;
        if (cap != Paint.CAP_BUTT) {
            double x = x1 - x0;
            double y = y1 - y0;
            double dmag = Math.sqrt(x * x + y * y);
            double dscale = 1.0 / dmag;
            float newX = (float) (x * dscale);
            float newY = (float) (y * dscale);
            if (Point.isDegenerate(newX, newY)) {
                setCircle(x0, y0, radius);
                if (cap == Paint.CAP_SQUARE) {
                    mRadius = 0;
                }
                return;
            }
            x1 += newX * radius;
            y1 += newY * radius;
            x0 -= newX * radius;
            y0 -= newY * radius;
        }
        setLine(x0, y0, x1, y1, radius, cap == Paint.CAP_ROUND);
    }

    public void setBlur(RRect rr, float blurRadius, float noiseAlpha) {
        assert Float.isFinite(blurRadius) && Float.isFinite(noiseAlpha);
        assert blurRadius > 0 && noiseAlpha >= 0;
        float minDim = Math.min(rr.width(), rr.height());
        mLeft = rr.left();
        mTop = rr.top();
        mRight = rr.right();
        mBottom = rr.bottom();
        // we found that multiplying the radius by 1.25 is closest to a Gaussian blur with a sigma of radius/3
        blurRadius *= 1.25f;
        float radius = rr.getSimpleRadiusX();
        // the closer to a rectangle, the larger the corner radius needs to be
        float t = Math.min(radius / Math.min(minDim, blurRadius), 1.0f);
        radius += MathUtil.lerp(0.36f, 0.09f, t) * blurRadius;
        mRadius = radius;
        mBlurRadius = blurRadius;
        mNoiseAlpha = noiseAlpha;
        mType = kBlurBox_Type;
    }

    @Override
    public void getBounds(@NonNull Rect2f dest) {
        dest.set(mLeft, mTop, mRight, mBottom);
        switch (mType) {
            case kLine_Type, kLineRound_Type -> {
                dest.sort();
                float outset = mRadius;
                dest.outset(outset, outset);
            }
            case kBlurBox_Type -> dest.outset(mBlurRadius, mBlurRadius);
        }
    }

    public void getInnerBounds(@NonNull Rect2f dest) {
        assert mType == kBox_Type;
        float r = mRadius;

        float w = mRight - mLeft;
        float h = mBottom - mTop;
        float dr = r + r;

        float horizArea = (w - dr) * h;
        float vertArea = (h - dr) * w;

        float innerArea = (w - RRect.kInsetScale * dr) * (h - RRect.kInsetScale * dr);

        if (horizArea > vertArea && horizArea > innerArea) {
            // Cut off corners by insetting left and right
            dest.set(mLeft + r, mTop, mRight - r, mBottom);
        } else if (vertArea > innerArea) {
            // Cut off corners by insetting top and bottom
            dest.set(mLeft, mTop + r, mRight, mBottom - r);
        } else if (innerArea > 0.f) {
            // Inset on all sides, scaled to touch
            float inset = RRect.kInsetScale * r;
            dest.set(mLeft + inset,
                    mTop + inset,
                    mRight - inset,
                    mBottom - inset);
        } else {
            assert false;
            dest.setEmpty();
            return;
        }

        assert dest.isSorted() && !dest.isEmpty();
    }
}

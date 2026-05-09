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

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.Shape;
import org.jspecify.annotations.NonNull;

import java.awt.geom.PathIterator;

public final class Rect implements Shape {

    public float mLeft;
    public float mTop;
    public float mRight;
    public float mBottom;

    public Rect() {
    }

    public Rect(@NonNull Rect2fc rect) {
        mLeft = rect.left();
        mTop = rect.top();
        mRight = rect.right();
        mBottom = rect.bottom();
    }

    public Rect(@NonNull Rect2ic rect) {
        mLeft = rect.left();
        mTop = rect.top();
        mRight = rect.right();
        mBottom = rect.bottom();
    }

    public void set(@NonNull Rect2fc rect) {
        mLeft = rect.left();
        mTop = rect.top();
        mRight = rect.right();
        mBottom = rect.bottom();
    }

    public void set(@NonNull Rect2ic rect) {
        mLeft = rect.left();
        mTop = rect.top();
        mRight = rect.right();
        mBottom = rect.bottom();
    }

    @Override
    public void getBounds(@NonNull Rect2f dest) {
        dest.set(mLeft, mTop, mRight, mBottom);
    }

    @Override
    public boolean contains(float x, float y) {
        return x >= mLeft && x < mRight && y >= mTop && y < mBottom;
    }

    @Override
    public boolean contains(float left, float top, float right, float bottom) {
        // check for empty first
        return mLeft < mRight && mTop < mBottom
                // now check for containment
                && mLeft <= left && mTop <= top && mRight >= right && mBottom >= bottom;
    }

    @Override
    public boolean contains(@NonNull Rect2fc r) {
        // check for empty first
        return mLeft < mRight && mTop < mBottom
                // now check for containment
                && mLeft <= r.left() && mTop <= r.top() && mRight >= r.right() && mBottom >= r.bottom();
    }

    @Override
    public int getWindingRule() {
        return Path.WIND_NON_ZERO;
    }

    @Override
    public @NonNull PathIterator getPathIterator() {
        //TODO
        return null;
    }

    @Override
    public String toString() {
        return "Rect(" + mLeft + ", " + mTop + ", "
                + mRight + ", " + mBottom + ")";
    }
}

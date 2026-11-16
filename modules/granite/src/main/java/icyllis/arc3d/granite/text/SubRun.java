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

package icyllis.arc3d.granite.text;

import icyllis.arc3d.granite.GraniteDevice;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * SubRun defines the most basic functionality of a SubRun; the ability to draw, and the
 * ability to be in a list.
 */
public abstract class SubRun {
    SubRun mNext;

    public abstract void draw(float originX, float originY,
                              Paint paint, GraniteDevice device);

    @Contract(pure = true)
    public abstract boolean canReuse(@NonNull Paint paint, @NonNull Matrixc positionMatrix,
                                     float glyphRunListX, float glyphRunListY);

    public abstract long getMemorySize();
}

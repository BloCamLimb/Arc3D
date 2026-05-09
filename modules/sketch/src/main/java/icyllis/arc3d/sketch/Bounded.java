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

import icyllis.arc3d.core.Rect2f;
import org.jspecify.annotations.NonNull;

/**
 * Interface for geometric shapes that have area.
 */
public interface Bounded {

    /**
     * Similar to {@link java.awt.Shape#getBounds2D()}, but stores
     * the result to dest.
     *
     * @param dest the destination rectangle to store the bounds to
     */
    void getBounds(@NonNull Rect2f dest);
}

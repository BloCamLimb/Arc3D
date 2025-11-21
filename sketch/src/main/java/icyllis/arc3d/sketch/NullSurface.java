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

import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.Rect2ic;
import icyllis.arc3d.core.SharedPtr;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class NullSurface extends Surface {

    public NullSurface(int width, int height) {
        super(width, height);
    }

    @Nullable
    @SharedPtr
    public static NullSurface make(int width, int height) {
        if (width < 1 || height < 1) {
            return null;
        }
        return new NullSurface(width, height);
    }

    @Override
    public @NonNull ImageInfo getImageInfo() {
        return ImageInfo.makeUnknown(getWidth(), getHeight());
    }

    @Override
    protected Canvas onNewCanvas() {
        return new NoDrawCanvas(getWidth(), getHeight());
    }

    @Override
    protected @Nullable Image onNewImageSnapshot(@Nullable Rect2ic subset) {
        return null;
    }

    @Override
    protected boolean onCopyOnWrite(int changeMode) {
        return true;
    }
}

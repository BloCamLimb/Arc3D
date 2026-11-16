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

import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.StrikeDesc;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public final class DirectMaskSubRun extends AtlasSubRun {

    /**
     * All params are read-only, copy will be made.
     */
    @Contract(pure = true)
    public DirectMaskSubRun(StrikeDesc strikeDesc,
                            Matrixc creationMatrix,
                            Rect2fc creationBounds,
                            int maskFormat,
                            int[] acceptedGlyphs,
                            int acceptedGlyphOffset,
                            float[] acceptedPositions,
                            int acceptedPositionOffset,
                            int acceptedGlyphCount,
                            GlyphStrikeCache strikeCache) {
        super(strikeDesc, creationMatrix, creationBounds,
                maskFormat, acceptedGlyphs, acceptedGlyphOffset,
                acceptedPositions, acceptedPositionOffset, acceptedGlyphCount,
                true, false, strikeCache);
    }

    @Override
    public boolean canReuse(@NonNull Paint paint, @NonNull Matrixc positionMatrix,
                            float glyphRunListX, float glyphRunListY) {
        // direct mask is always created from/reused by an exact position matrix,
        // no need for a second check
        return true;
    }
}

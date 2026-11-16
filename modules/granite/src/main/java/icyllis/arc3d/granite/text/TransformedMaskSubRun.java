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

import icyllis.arc3d.core.MathUtil;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.StrikeDesc;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public final class TransformedMaskSubRun extends AtlasSubRun {

    private final float mMinScaleFactor;
    private final float mMaxScaleFactor;

    /**
     * All params are read-only, copy will be made.
     */
    @Contract(pure = true)
    public TransformedMaskSubRun(StrikeDesc strikeDesc,
                                 Matrixc creationMatrix,
                                 Rect2fc creationBounds,
                                 int maskFormat,
                                 int[] acceptedGlyphs,
                                 int acceptedGlyphOffset,
                                 float[] acceptedPositions,
                                 int acceptedPositionOffset,
                                 int acceptedGlyphCount,
                                 float minScaleFactor,
                                 float maxScaleFactor,
                                 GlyphStrikeCache strikeCache) {
        super(strikeDesc, creationMatrix, creationBounds,
                maskFormat, acceptedGlyphs, acceptedGlyphOffset,
                acceptedPositions, acceptedPositionOffset, acceptedGlyphCount,
                false, true, strikeCache);
        mMinScaleFactor = minScaleFactor;
        mMaxScaleFactor = maxScaleFactor;
    }

    @Override
    public boolean canReuse(@NonNull Paint paint, @NonNull Matrixc positionMatrix,
                            float glyphRunListX, float glyphRunListY) {
        // We want to reuse the subrun that scales to the range 0.5 to 1.5 relative to the
        // creation matrix.
        float scaleFactor = 1;
        if (positionMatrix.hasPerspective()) {
            float maxAreaScale = positionMatrix.differentialAreaScale(
                    glyphRunListX, glyphRunListY
            );
            if (Float.isFinite(maxAreaScale) && maxAreaScale > MathUtil.PATH_TOLERANCE) {
                scaleFactor = (float) Math.sqrt(maxAreaScale);
            }
        } else {
            scaleFactor = positionMatrix.getMaxScale();
        }
        return mMinScaleFactor < scaleFactor && scaleFactor <= mMaxScaleFactor;
    }
}

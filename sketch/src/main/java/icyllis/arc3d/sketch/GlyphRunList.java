/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.core.Rect2fc;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * GlyphRunList provides a buffer view to GlyphRuns and additional drawing info.
 */
@ApiStatus.Internal
public class GlyphRunList {

    /**
     * Raw pointer view.
     */
    public GlyphRun[] mGlyphRuns;
    public int mGlyphRunCount;

    /**
     * Raw pointer, nullable.
     */
    public TextBlob mOriginalTextBlob;

    private final Rect2f mSourceBounds = new Rect2f();
    public float mOriginX;
    public float mOriginY;

    public GlyphRunList() {
    }

    public void set(GlyphRun[] glyphRuns, int glyphRunCount,
                    @Nullable TextBlob blob,
                    Rect2fc bounds, float originX, float originY) {
        mGlyphRuns = glyphRuns;
        mGlyphRunCount = glyphRunCount;
        mOriginalTextBlob = blob;
        mSourceBounds.set(bounds);
        mOriginX = originX;
        mOriginY = originY;
    }

    @NonNull
    public Rect2fc getSourceBounds() {
        return mSourceBounds;
    }

    public void getSourceBoundsWithOrigin(@NonNull Rect2f bounds) {
        mSourceBounds.store(bounds);
        bounds.offset(mOriginX, mOriginY);
    }

    public int maxGlyphRunSize() {
        int size = 0;
        for (int i = 0; i < mGlyphRunCount; i++) {
            size = Math.max(mGlyphRuns[i].mGlyphCount, size);
        }
        return size;
    }

    /**
     * Release heavy buffers.
     */
    public void clear() {
        if (mGlyphRuns != null) {
            for (int i = 0; i < mGlyphRunCount; i++) {
                mGlyphRuns[i].clear();
            }
            mGlyphRuns = null;
        }
        mOriginalTextBlob = null;
    }

    // should return non-null
    public TextBlob getOrCreateBlob() {
        if (mOriginalTextBlob != null) {
            return mOriginalTextBlob;
        }
        var builder = new TextBlob.Builder();
        // when there is no original TextBlob, there should only be one run, guaranteed by Canvas
        assert mGlyphRunCount == 1;
        var run = mGlyphRuns[0];
        var runCount = run.mGlyphCount;
        assert runCount > 0; // guaranteed by Canvas
        var runBuffer = builder.allocRunPos(run.font(), runCount, mSourceBounds);
        runBuffer.addGlyphs(run.mGlyphs, 0, runCount);
        runBuffer.addPositions(run.mPositions, 0, runCount);
        var result = builder.build();
        assert result != null; // this should not return null, since there's one run
        return result;
    }
}

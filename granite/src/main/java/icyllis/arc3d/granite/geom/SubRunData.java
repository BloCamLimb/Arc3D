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

package icyllis.arc3d.granite.geom;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.granite.SubRunContainer;
import icyllis.arc3d.sketch.Bounded;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Matrixc;
import org.jspecify.annotations.NonNull;

public class SubRunData implements Bounded {

    private final SubRunContainer.AtlasSubRun mSubRun;
    private final Matrix mSubRunToLocal;
    private final int mFilter;
    private final int mStartGlyphIndex;
    private final int mGlyphCount;

    // subRunToLocal is affine, no copy
    public SubRunData(SubRunContainer.AtlasSubRun subRun,
                      Matrix subRunToLocal, int filter,
                      int startGlyphIndex, int glyphCount) {
        mSubRun = subRun;
        mSubRunToLocal = subRunToLocal;
        mFilter = filter;
        mStartGlyphIndex = startGlyphIndex;
        mGlyphCount = glyphCount;
    }

    public SubRunContainer.AtlasSubRun getSubRun() {
        return mSubRun;
    }

    // affine matrix
    public Matrixc getSubRunToLocal() {
        return mSubRunToLocal;
    }

    /**
     * @see icyllis.arc3d.engine.SamplerDesc
     */
    public int getFilter() {
        return mFilter;
    }

    public int getStartGlyphIndex() {
        return mStartGlyphIndex;
    }

    public int getGlyphCount() {
        return mGlyphCount;
    }

    public Rect2fc getBounds() {
        return mSubRun.getBounds();
    }

    @Override
    public void getBounds(@NonNull Rect2f dest) {
        mSubRun.getBounds().store(dest);
    }
}

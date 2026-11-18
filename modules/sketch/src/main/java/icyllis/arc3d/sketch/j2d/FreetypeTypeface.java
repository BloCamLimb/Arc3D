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

package icyllis.arc3d.sketch.j2d;

import icyllis.arc3d.sketch.ScalerContext;
import icyllis.arc3d.sketch.StrikeDesc;
import icyllis.arc3d.sketch.Typeface;
import org.jspecify.annotations.NonNull;

import java.awt.Font;

/**
 * Wraps a JDK Font2D.
 */
public class FreetypeTypeface extends Typeface {

    private final Font mFont;

    /**
     * This method should take Font2D as input, but it's private.
     * The AWT font object must represent only a family and style,
     * its size should be 1 by default. The font style may be algorithmic
     * that is provided by JDK.
     */
    public FreetypeTypeface(Font font) {
        mFont = font;
    }

    public Font getFont() {
        return mFont;
    }

    @NonNull
    @Override
    protected ScalerContext onCreateScalerContext(StrikeDesc desc) {
        return new FreetypeScalerContext(this, desc);
    }

    @Override
    protected void onFilterStrikeDesc(StrikeDesc.Lookup desc) {
        // subpixel positioning requires linear metrics (no font hinting)
        if ((desc.getFlags() & StrikeDesc.kLinearMetrics_Flag) == 0) {
            desc.setFlags(desc.getFlags() & ~StrikeDesc.kSubpixelPositioning_Flag);
        }
    }
}

/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2026 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.granite.DrawAtlas;
import icyllis.arc3d.sketch.Glyph;
import icyllis.arc3d.sketch.Mask;
import icyllis.arc3d.engine.Engine;
import org.jspecify.annotations.NonNull;

/**
 * This class holds information for a glyph about its location in atlas.
 */
public class BakedGlyph extends DrawAtlas.AtlasLocator {

    /**
     * Bilerp is unnecessary for most cases, but if required, the same packed glyph ID cannot distinguish
     * between the two, even though the bilerp case doesn't use sub-pixel positioning.
     * <p>
     * CPU packed glyph ID uses [0,16) bits, kSubPixelX [22, 24) bits,
     * [24,32) bits. We use one extra bit (21) to store whether the glyph has
     * additional one-pixel border for bilinear interpolation, see {@link Glyph#kBilerpGlyphBorder}.
     */
    public static final int kBilerpBorderShift = Glyph.kSubPixelXShift - 1;
    public static final int kBilerpBorderMask = 1 << kBilerpBorderShift;

    public final int key;

    public BakedGlyph(int key) {
        this.key = key;
    }

    public int getPackedID() {
        return key & ~kBilerpBorderMask;
    }

    public boolean hasBilerpBorder() {
        return (key & kBilerpBorderMask) != 0;
    }

    public static int packKey(int packedGlyphID, boolean bilerpBorder) {
        return packedGlyphID | (bilerpBorder ? kBilerpBorderMask : 0);
    }

    public static int chooseMaskFormat(byte mask) {
        // promote B/W to Alpha8
        return switch (mask) {
            case Mask.kBW_Format, Mask.kA8_Format, Mask.kSDF_Format -> Engine.MASK_FORMAT_A8;
            case Mask.kARGB32_Format -> Engine.MASK_FORMAT_ARGB;
            default -> throw new AssertionError();
        };
    }

    public static int chooseMaskFormat(@NonNull Glyph glyph) {
        return chooseMaskFormat(glyph.getMaskFormat());
    }
}

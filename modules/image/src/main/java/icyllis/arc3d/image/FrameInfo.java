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

package icyllis.arc3d.image;

/**
 * Information about the individual animation frames in an animated image.
 */
public class FrameInfo {

    public static final int
            DISPOSAL_NONE = 1,
            DISPOSAL_BACKGROUND = 2,
            DISPOSAL_PREVIOUS = 3;

    public static final int
            BLEND_SRC_OVER = 0,
            BLEND_SRC = 1;

    /**
     * The frame delay in milliseconds.
     * <p>
     * If the value is 0, the decoder should render the next frame as quickly as possible.
     */
    public int delay;

    public int disposal;
    public int blend;

    /**
     * The bounds updated by this frame.
     */
    public int frameLeft, frameTop, frameWidth, frameHeight;
    /**
     * Whether the updated frame has transparency.
     * This does not mean the current output buffer.
     */
    public boolean hasAlphaWithinBounds;
}

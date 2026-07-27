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

public final class GIF {

    public static final byte[] APP_NETSCAPE2_0 = {
            'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E',
            '2', '.', '0'
    };

    public static final byte[] APP_ANIMEXTS1_0 = {
            'A', 'N', 'I', 'M', 'E', 'X', 'T', 'S',
            '1', '.', '0'
    };

    // this was part of ICC standard in 2004, but not listed in 2022
    public static final byte[] APP_ICC = {
            'I', 'C', 'C', 'R', 'G', 'B', 'G', '1',
            '0', '1', '2'
    };

    public static final byte[] APP_XMP = {
            'X', 'M', 'P', ' ', 'D', 'a', 't', 'a',
            'X', 'M', 'P'
    };

    static final int[] interlaceOffset = { 0, 4, 2, 1 };
    static final int[] interlaceStep = { 8, 8, 4, 2 };
}

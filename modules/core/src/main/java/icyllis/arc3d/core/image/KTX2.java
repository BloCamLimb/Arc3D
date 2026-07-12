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

package icyllis.arc3d.core.image;

public class KTX2 {

    public static final byte[] FILE_SIGNATURE = {
            (byte) 0xAB, (byte) 0x4B, (byte) 0x54, (byte) 0x58,
            (byte) 0x20, (byte) 0x32, (byte) 0x30, (byte) 0xBB,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
}

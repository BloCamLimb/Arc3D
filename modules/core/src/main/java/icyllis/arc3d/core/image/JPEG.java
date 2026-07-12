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

public final class JPEG {

    public static final byte[] FILE_SIGNATURE = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    // http://ns.adobe.com/xap/1.0/\0
    public static final byte[] XMP_STANDARD_SIGNATURE = {
            'h', 't', 't', 'p', ':', '/', '/', 'n', 's', '.', 'a', 'd', 'o', 'b', 'e', '.', 'c', 'o',
            'm', '/', 'x', 'a', 'p', '/', '1', '.', '0', '/', '\0'
    };
    public static final byte[] XMP_EXTENDED_SIGNATURE = {
            'h', 't', 't', 'p', ':', '/', '/', 'n', 's', '.', 'a', 'd', 'o', 'b', 'e', '.', 'c', 'o',
            'm', '/', 'x', 'm', 'p', '/', 'e', 'x', 't', 'e', 'n', 's', 'i', 'o', 'n', '/', '\0'
    };

    public static final byte[] ISO_21496_1_SIGNATURE = {
            'u', 'r', 'n', ':', 'i', 's', 'o', ':', 's', 't',
            'd', ':', 'i', 's', 'o', ':', 't', 's', ':', '2',
            '1', '4', '9', '6', ':', '-', '1', '\0'
    };
}

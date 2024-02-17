/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.compiler.spirv;

public enum SPIRVVersion {
    /**
     * SPIR-V version 1.0 for OpenGL 4.6.
     */
    VERSION_1_0(0x00010000),
    /**
     * SPIR-V version 1.3 for Vulkan 1.1.
     */
    VERSION_1_3(0x00010300),
    /**
     * SPIR-V version 1.6 for Vulkan 1.3.
     */
    VERSION_1_6(0x00010600);

    public final int mVersionNumber;

    SPIRVVersion(int versionNumber) {
        mVersionNumber = versionNumber;
    }
}
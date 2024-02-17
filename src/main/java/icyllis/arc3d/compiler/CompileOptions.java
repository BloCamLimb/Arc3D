/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.compiler;

/**
 * Holds the options for compiling an executable program.
 */
public class CompileOptions {

    public boolean mForceHighPrecision;

    /**
     * Force no short-circuit operators.
     * <p>
     * For '&&', '||' and '?:', always evaluate both sides and use OpSelect.
     * No branching is usually more performant.
     * <p>
     * Use with caution, may have side effects.
     */
    public boolean mNoShortCircuit;

    public boolean mOptimize = true;
}
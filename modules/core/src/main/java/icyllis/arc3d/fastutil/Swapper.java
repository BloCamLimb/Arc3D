/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2010-2026 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.fastutil;

/** An object that can swap elements whose position is specified by integers.
 *
 * @see Arrays#quickSort(int, int, IntComparator, Swapper)
 */
@FunctionalInterface
public interface Swapper {
	/** Swaps the data at the given positions.
	 *
	 * @param a the first position to swap.
	 * @param b the second position to swap.
	 */
	void swap(int a, int b);
}

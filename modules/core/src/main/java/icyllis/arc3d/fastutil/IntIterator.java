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

package icyllis.arc3d.fastutil;

import java.util.Iterator;
import java.util.PrimitiveIterator;

/**
 * A type-specific {@link Iterator}; provides an additional method to avoid (un)boxing, and the
 * possibility to skip elements.
 *
 * @see Iterator
 */
public interface IntIterator extends PrimitiveIterator.OfInt {

	/**
	 * Skips the given number of elements.
	 *
	 * <p>
	 * The effect of this call is exactly the same as that of calling {@link #next()} for {@code n}
	 * times (possibly stopping if {@link #hasNext()} becomes false).
	 *
	 * @param n the number of elements to skip.
	 * @return the number of elements actually skipped.
	 * @see Iterator#next()
	 */
	default int skip(final int n) {
		if (n < 0) throw new IllegalArgumentException("Argument must be nonnegative: " + n);
		int i = n;
		while (i-- != 0 && hasNext()) nextInt();
		return n - i - 1;
	}
}

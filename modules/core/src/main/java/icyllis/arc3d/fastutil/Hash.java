/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2002-2026 BloCamLimb <pocamelards@gmail.com>
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

/** Basic data for all hash-based classes. */

public interface Hash {

	/** The initial default size of a hash table. */
	int DEFAULT_INITIAL_SIZE = 16;
	/** The default load factor of a hash table. */
	float DEFAULT_LOAD_FACTOR = .75f;
	/** The load factor for a (usually small) table that is meant to be particularly fast. */
	float FAST_LOAD_FACTOR = .5f;
	/** The load factor for a (usually very small) table that is meant to be extremely fast. */
	float VERY_FAST_LOAD_FACTOR = .25f;
}

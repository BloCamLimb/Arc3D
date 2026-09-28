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

/**
 * A type-specific {@link Stack}; provides some additional methods that use polymorphism to avoid
 * (un)boxing.
 */
public interface LongStack {
	/**
	 * Pushes the given object on the stack.
	 * 
	 * @param k the object to push on the stack.
	 * @see Stack#push(Object)
	 */
	void push(long k);

    boolean isEmpty();

	/**
	 * Pops the top off the stack.
	 *
	 * @return the top of the stack.
	 * @see Stack#pop()
	 */
	long popLong();

	/**
	 * Peeks at the top of the stack (optional operation).
	 * 
	 * @return the top of the stack.
	 * @see Stack#top()
	 */
	long topLong();

	/**
	 * Peeks at an element on the stack (optional operation).
	 * 
	 * @param i an index from the stop of the stack (0 represents the top).
	 * @return the {@code i}-th element on the stack.
	 * @see Stack#peek(int)
	 */
	long peekLong(int i);
}

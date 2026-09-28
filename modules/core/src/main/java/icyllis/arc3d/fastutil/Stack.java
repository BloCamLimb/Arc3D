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

import java.util.NoSuchElementException;

/** A stack.
 *
 * <p>A stack must provide the classical {@link #push(Object)} and
 * {@link #pop()} operations, but may be also <em>peekable</em>
 * to some extent: it may provide just the {@link #top()} function,
 * or even a more powerful {@link #peek(int)} method that provides
 * access to all elements on the stack (indexed from the top, which
 * has index 0).
 *
 * @param <K> the types of elements in the stack.
 */

public interface Stack<K> {

	/** Pushes the given object on the stack.
	 *
	 * @param o the object that will become the new top of the stack.
	 */

	void push(K o);

	/** Pops the top off the stack.
	 *
	 * @return the top of the stack.
	 * @throws NoSuchElementException if the stack is empty.
	 */

	K pop();

	/** Checks whether the stack is empty.
	 *
	 * @return true if the stack is empty.
	 */

	boolean isEmpty();

	/** Peeks at the top of the stack (optional operation).
	 *
	 * <p>This default implementation returns {@link #peek(int) peek(0)}.
	 *
	 * @return the top of the stack.
	 * @throws NoSuchElementException if the stack is empty.
	 */

	default K top()  {
		return peek(0);
	}

	/** Peeks at an element on the stack (optional operation).
	 *
	 * <p>This default implementation just throws an {@link UnsupportedOperationException}.
	 *
	 * @param i an index from the stop of the stack (0 represents the top).
	 * @return the {@code i}-th element on the stack.
	 * @throws IndexOutOfBoundsException if the designated element does not exist..
	 */

	default K peek(final int i) {
		throw new UnsupportedOperationException();
	}
}

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

/*
 * Copyright (C) 2002-2024 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package icyllis.arc3d.core.util;

import java.util.ListIterator;

/**
 * A type-specific bidirectional iterator that is also a {@link ListIterator}.
 *
 * <p>
 * This interface merges the methods provided by a {@link ListIterator} and a type-specific
 * {@link it.unimi.dsi.fastutil.BidirectionalIterator}. Moreover, it provides type-specific versions
 * of {@link ListIterator#add(Object) add()} and {@link ListIterator#set(Object) set()}.
 *
 * @see java.util.ListIterator
 * @see it.unimi.dsi.fastutil.BidirectionalIterator
 */
public interface LongListIterator extends LongIterator {

    /** Returns whether there is a previous element.
     *
     * @return whether there is a previous element.
     * @see java.util.ListIterator#hasPrevious()
     */

    boolean hasPrevious();

    /**
     * Returns the previous element as a primitive type.
     *
     * @return the previous element in the iteration.
     * @see java.util.ListIterator#previous()
     */
    long previousLong();

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to {@link #next}. (Returns list size if the list
     * iterator is at the end of the list.)
     *
     * @return the index of the element that would be returned by a
     *         subsequent call to {@code next}, or list size if the list
     *         iterator is at the end of the list
     */
    int nextIndex();

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to {@link #previous}. (Returns -1 if the list
     * iterator is at the beginning of the list.)
     *
     * @return the index of the element that would be returned by a
     *         subsequent call to {@code previous}, or -1 if the list
     *         iterator is at the beginning of the list
     */
    int previousIndex();

    /**
     * Moves back for the given number of elements.
     *
     * <p>
     * The effect of this call is exactly the same as that of calling {@link #previous()} for {@code n}
     * times (possibly stopping if {@link #hasPrevious()} becomes false).
     *
     * @param n the number of elements to skip back.
     * @return the number of elements actually skipped.
     * @see #previous()
     */
    default int back(final int n) {
        int i = n;
        while (i-- != 0 && hasPrevious()) previousLong();
        return n - i - 1;
    }

	/**
	 * Replaces the last element returned by {@link #next} or {@link #previous} with the specified
	 * element (optional operation).
	 * 
	 * @param k the element used to replace the last element returned.
	 *
	 *            <p>
	 *            This default implementation just throws an {@link UnsupportedOperationException}.
	 * @see ListIterator#set(Object)
	 */
	default void set(final long k) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Inserts the specified element into the list (optional operation).
	 *
	 * <p>
	 * This default implementation just throws an {@link UnsupportedOperationException}.
	 * 
	 * @param k the element to insert.
	 * @see ListIterator#add(Object)
	 */
	default void add(final long k) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Removes from the underlying collection the last element returned by this iterator (optional
	 * operation).
	 *
	 * <p>
	 * This default implementation just throws an {@link UnsupportedOperationException}.
	 * 
	 * @see ListIterator#remove()
	 */
	@Override
	default void remove() {
		throw new UnsupportedOperationException();
	}
}

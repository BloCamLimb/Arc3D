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

import java.util.Arrays;
import java.util.List;

/**
 * A type-specific {@link List}; provides some additional methods that use polymorphism to avoid
 * (un)boxing.
 *
 * <p>
 * Note that this type-specific interface extends {@link Comparable}: it is expected that
 * implementing classes perform a lexicographical comparison using the standard operator "less then"
 * for primitive types, and the usual {@link Comparable#compareTo(Object) compareTo()} method for
 * objects.
 *
 * <p>
 * Additionally, this interface strengthens {@link #iterator()}, {@link #listIterator()},
 * {@link #listIterator(int)} and {@link #subList(int,int)}. The former had been already
 * strengthened upstream, but unfortunately {@link List} re-specifies it.
 *
 * <p>
 * Besides polymorphic methods, this interfaces specifies methods to copy into an array or remove
 * contiguous sublists. Although the abstract implementation of this interface provides simple,
 * one-by-one implementations of these methods, it is expected that concrete implementation override
 * them with optimized versions.
 *
 * @see List
 */
public interface LongList extends LongCollection {
	/**
	 * Returns a type-specific iterator on the elements of this list.
	 *
	 * @apiNote This specification strengthens the one given in {@link List#iterator()}. It would not be
	 *          normally necessary, but {@link java.lang.Iterable#iterator()} is bizarrily re-specified
	 *          in {@link List}.
	 *          <p>
	 *          Also, this is generally the only {@code iterator} method subclasses should override.
	 *
	 * @return an iterator on the elements of this list.
	 */
	@Override
	LongListIterator iterator();

	/**
	 * Returns a type-specific list iterator on the list.
	 *
	 * @see List#listIterator()
	 */
	LongListIterator listIterator();

	/**
	 * Returns a type-specific list iterator on the list starting at a given index.
	 *
	 * @see List#listIterator(int)
	 */
	LongListIterator listIterator(int index);

	/**
	 * Sets the size of this list.
	 *
	 * <p>
	 * If the specified size is smaller than the current size, the last elements are discarded.
	 * Otherwise, they are filled with 0/{@code null}/{@code false}.
	 *
	 * @param size the new size.
	 */
	void size(int size);

	/**
	 * Copies (hopefully quickly) elements of this type-specific list into the given array.
	 *
	 * @param from the start index (inclusive).
	 * @param a the destination array.
	 * @param offset the offset into the destination array where to store the first element copied.
	 * @param length the number of elements to be copied.
	 */
	void getElements(int from, long a[], int offset, int length);

	/**
	 * Removes (hopefully quickly) elements of this type-specific list.
	 *
	 * @param from the start index (inclusive).
	 * @param to the end index (exclusive).
	 */
	void removeElements(int from, int to);

	/**
	 * Add (hopefully quickly) elements to this type-specific list.
	 *
	 * @param index the index at which to add elements.
	 * @param a the array containing the elements.
	 */
	void addElements(int index, long a[]);

	/**
	 * Add (hopefully quickly) elements to this type-specific list.
	 *
	 * @param index the index at which to add elements.
	 * @param a the array containing the elements.
	 * @param offset the offset of the first element to add.
	 * @param length the number of elements to add.
	 */
	void addElements(int index, long a[], int offset, int length);

	/**
	 * Set (hopefully quickly) elements to match the array given.
	 * 
	 * @param a the array containing the elements.
	 * @since 8.3.0
	 */
	default void setElements(long a[]) {
		setElements(0, a);
	}

	/**
	 * Set (hopefully quickly) elements to match the array given.
	 * 
	 * @param index the index at which to start setting elements.
	 * @param a the array containing the elements.
	 * @since 8.3.0
	 */
	default void setElements(int index, long a[]) {
		setElements(index, a, 0, a.length);
	}

	/**
	 * Set (hopefully quickly) elements to match the array given.
	 *
	 * Sets each in this list to the corresponding elements in the array, as if by
	 * 
	 * <pre>
	 * ListIterator iter = listIterator(index);
	 * int i = 0;
	 * while (i &lt; length) {
	 * 	iter.next();
	 * 	iter.set(a[offset + i++]);
	 * }
	 * </pre>
	 * 
	 * However, the exact implementation may be more efficient, taking into account whether random
	 * access is faster or not, or at the discretion of subclasses, abuse internals.
	 *
	 * @param index the index at which to start setting elements.
	 * @param a the array containing the elements
	 * @param offset the offset of the first element to add.
	 * @param length the number of elements to add.
	 * @since 8.3.0
	 */
	default void setElements(int index, long a[], int offset, int length) {
		// We can't use AbstractList#ensureIndex, sadly.
		if (index < 0) throw new IndexOutOfBoundsException("Index (" + index + ") is negative");
		if (index > size()) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than list size (" + (size()) + ")");
		LongArrays.ensureOffsetLength(a, offset, length);
		if (index + length > size()) throw new IndexOutOfBoundsException("End index (" + (index + length) + ") is greater than list size (" + size() + ")");
		LongListIterator iter = listIterator(index);
		int i = 0;
		while (i < length) {
			iter.nextLong();
			iter.set(a[offset + i++]);
		}
	}

	/**
	 * Appends the specified element to the end of this list (optional operation).
	 * 
	 * @see List#add(Object)
	 */
	@Override
	boolean add(long key);

	/**
	 * Inserts the specified element at the specified position in this list (optional operation).
	 * 
	 * @see List#add(int,Object)
	 */
	void add(int index, long key);

	/**
	 * Inserts all of the elements in the specified type-specific collection into this type-specific
	 * list at the specified position (optional operation).
	 * 
	 * @see List#addAll(int,java.util.Collection)
	 */
	boolean addAll(int index, LongCollection c);

	/**
	 * Replaces the element at the specified position in this list with the specified element (optional
	 * operation).
	 * 
	 * @see List#set(int,Object)
	 */
	long set(int index, long k);

	/**
	 * Replaces each element of this list with the result of applying the operator to that element.
	 * 
	 * @param operator the operator to apply to each element.
	 * @see java.util.List#replaceAll
	 */
	default void replaceAll(final java.util.function.LongUnaryOperator operator) {
		final LongListIterator iter = listIterator();
		while (iter.hasNext()) {
			iter.set(operator.applyAsLong(iter.nextLong()));
		}
	}

	/**
	 * Returns the element at the specified position in this list.
	 * 
	 * @see List#get(int)
	 */
	long getLong(int index);

	/**
	 * Returns the index of the first occurrence of the specified element in this list, or -1 if this
	 * list does not contain the element.
	 * 
	 * @see List#indexOf(Object)
	 */
	int indexOf(long k);

	/**
	 * Returns the index of the last occurrence of the specified element in this list, or -1 if this
	 * list does not contain the element.
	 * 
	 * @see List#lastIndexOf(Object)
	 */
	int lastIndexOf(long k);

	/**
	 * Removes the element at the specified position in this list (optional operation).
	 * 
	 * @see List#remove(int)
	 */
	long removeLong(int index);

	/**
	 * Inserts all of the elements in the specified type-specific list into this type-specific list at
	 * the specified position (optional operation).
	 * 
	 * @apiNote This method exists only for the sake of efficiency: override are expected to use
	 *          {@link #getElements}/{@link #addElements}.
	 * @implSpec This method delegates to the one accepting a collection, but it might be implemented
	 *           more efficiently.
	 * @see List#addAll(int,Collection)
	 */
	default boolean addAll(int index, LongList l) {
		return addAll(index, (LongCollection)l);
	}

	/**
	 * Appends all of the elements in the specified type-specific list to the end of this type-specific
	 * list (optional operation).
	 * 
	 * @implSpec This method delegates to the index-based version, passing {@link #size()} as first
	 *           argument.
	 * @see List#addAll(Collection)
	 */
	default boolean addAll(LongList l) {
		return addAll(size(), l);
	}

	/**
	 * Sort a list using a type-specific comparator.
	 *
	 * <p>
	 * Pass {@code null} to sort using natural ordering.
	 * 
	 * @see List#sort(java.util.Comparator)
	 *
	 * @implSpec The default implementation dumps the elements into an array using {@link #toArray()},
	 *           sorts the array, then replaces all elements using the {@link #setElements} function.
	 *
	 *           <p>
	 *           It is possible for this method to call {@link #unstableSort} if it can determine that
	 *           the results of a stable and unstable sort are completely equivalent. This means if you
	 *           override {@link #unstableSort}, it should <em>not</em> call this method unless you
	 *           override this method as well.
	 *
	 * @since 8.3.0
	 */
	default void sort() {
        long[] elements = toLongArray();
        Arrays.sort(elements);
        setElements(elements);
	}
}

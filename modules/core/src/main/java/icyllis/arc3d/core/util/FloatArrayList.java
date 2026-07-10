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
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.NoSuchElementException;

/**
 * A type-specific array-based list; provides some additional methods that use polymorphism to avoid
 * (un)boxing.
 *
 * <p>
 * This class implements a lightweight, fast, open, optimized, reuse-oriented version of array-based
 * lists. Instances of this class represent a list with an array that is enlarged as needed when new
 * entries are created (by increasing its current length by 50%), but is <em>never</em> made smaller
 * (even on a {@link #clear()}). A family of {@linkplain #trim() trimming methods} lets you control
 * the size of the backing array; this is particularly useful if you reuse instances of this class.
 * Range checks are equivalent to those of {@code java.util}'s classes, but they are delayed as much
 * as possible. The backing array is exposed by the {@link #elements()} method.
 *
 * <p>
 * This class implements the bulk methods {@code removeElements()}, {@code addElements()} and
 * {@code getElements()} using high-performance system calls (e.g.,
 * {@link System#arraycopy(Object,int,Object,int,int) System.arraycopy()}) instead of expensive
 * loops.
 *
 * @see java.util.ArrayList
 */
public class FloatArrayList implements Cloneable, java.io.Serializable {
	private static final long serialVersionUID = -7046029254386353130L;
	/** The initial default capacity of an array list. */
	public static final int DEFAULT_INITIAL_CAPACITY = 10;
	/** The backing array. */
	protected transient float[] a;
	/** The current actual size of the list (never greater than the backing-array length). */
	protected int size;

	/**
	 * Ensures that the component type of the given array is the proper type. This is irrelevant for
	 * primitive types, so it will just do a trivial copy. But for Reference types, you can have a
	 * {@code String[]} masquerading as an {@code Object[]}, which is a case we need to prepare for
	 * because we let the user give an array to use directly with {@link #wrap}.
	 */

	private static final float[] copyArraySafe(float[] a, int length) {
		if (length == 0) return FloatArrays.EMPTY_ARRAY;
		return java.util.Arrays.copyOf(a, length);
	}

	private static final float[] copyArrayFromSafe(FloatArrayList l) {
		return copyArraySafe(l.a, l.size);
	}

	/**
	 * Creates a new array list using a given array.
	 *
	 * <p>
	 * This constructor is only meant to be used by the wrapping methods.
	 *
	 * @param a the array that will be used to back this array list.
	 */
	protected FloatArrayList(final float[] a, @SuppressWarnings("unused") boolean wrapped) {
		this.a = a;
	}

	private void initArrayFromCapacity(final int capacity) {
		if (capacity < 0) throw new IllegalArgumentException("Initial capacity (" + capacity + ") is negative");
		if (capacity == 0) a = FloatArrays.EMPTY_ARRAY;
		else a = new float[capacity];
	}

	/**
	 * Creates a new array list with given capacity.
	 *
	 * @param capacity the initial capacity of the array list (may be 0).
	 */
	public FloatArrayList(final int capacity) {
		initArrayFromCapacity(capacity);
	}

	/** Creates a new array list with {@link #DEFAULT_INITIAL_CAPACITY} capacity. */

	public FloatArrayList() {
		a = FloatArrays.DEFAULT_EMPTY_ARRAY; // We delay allocation
	}

	/**
	 * Creates a new array list and fills it with a given type-specific list.
	 *
	 * @param l a type-specific list that will be used to fill the array list.
	 */
	public FloatArrayList(final FloatArrayList l) {
        a = copyArrayFromSafe(l);
        size = a.length;
	}

	/**
	 * Creates a new array list and fills it with the elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the array list.
	 */
	public FloatArrayList(final float[] a) {
		this(a, 0, a.length);
	}

	/**
	 * Creates a new array list and fills it with the elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the array list.
	 * @param offset the first element to use.
	 * @param length the number of elements to use.
	 */
	public FloatArrayList(final float[] a, final int offset, final int length) {
		this(length);
		System.arraycopy(a, offset, this.a, 0, length);
		size = length;
	}

	/**
	 * Creates a new array list and fills it with the elements returned by an iterator..
	 *
	 * @param i an iterator whose returned elements will fill the array list.
	 */
	public FloatArrayList(final Iterator<? extends Float> i) {
		this();
		while (i.hasNext()) this.add((i.next()).floatValue());
	}

	/**
	 * Returns the backing array of this list.
	 *
	 * @return the backing array.
	 */
	public float[] elements() {
		return a;
	}

	/**
	 * Wraps a given array into an array list of given size.
	 *
	 * <p>
	 * Note it is guaranteed that the type of the array returned by {@link #elements()} will be the same
	 * (see the comments in the class documentation).
	 *
	 * @param a an array to wrap.
	 * @param length the length of the resulting array list.
	 * @return a new array list of the given size, wrapping the given array.
	 */
	public static FloatArrayList wrap(final float[] a, final int length) {
		if (length > a.length) throw new IllegalArgumentException("The specified length (" + length + ") is greater than the array size (" + a.length + ")");
		final FloatArrayList l = new FloatArrayList(a, true);
		l.size = length;
		return l;
	}

	/**
	 * Wraps a given array into an array list.
	 *
	 * <p>
	 * Note it is guaranteed that the type of the array returned by {@link #elements()} will be the same
	 * (see the comments in the class documentation).
	 *
	 * @param a an array to wrap.
	 * @return a new array list wrapping the given array.
	 */
	public static FloatArrayList wrap(final float[] a) {
		return wrap(a, a.length);
	}

	/**
	 * Creates a new empty array list.
	 *
	 * @return a new empty array list.
	 */
	public static FloatArrayList of() {
		return new FloatArrayList();
	}

	/**
	 * Creates an array list using an array of elements.
	 *
	 * @param init a the array the will become the new backing array of the array list.
	 * @return a new array list backed by the given array.
	 * @see #wrap
	 */

	public static FloatArrayList of(final float... init) {
		return wrap(init);
	}

	/**
	 * Ensures that this array list can contain the given number of entries without resizing.
	 *
	 * @param capacity the new minimum capacity for this array list.
	 */

	public void ensureCapacity(final int capacity) {
		if (capacity <= a.length || (a == FloatArrays.DEFAULT_EMPTY_ARRAY && capacity <= DEFAULT_INITIAL_CAPACITY)) return;
		a = FloatArrays.ensureCapacity(a, capacity, size);
		assert size <= a.length;
	}

	/**
	 * Grows this array list, ensuring that it can contain the given number of entries without resizing,
	 * and in case increasing the current capacity at least by a factor of 50%.
	 *
	 * @param capacity the new minimum capacity for this array list.
	 */

	private void grow(int capacity) {
		if (capacity <= a.length) return;
		if (a != FloatArrays.DEFAULT_EMPTY_ARRAY) capacity = (int)Math.max(Math.min((long)a.length + (a.length >> 1), icyllis.arc3d.core.util.Arrays.MAX_ARRAY_SIZE), capacity);
		else if (capacity < DEFAULT_INITIAL_CAPACITY) capacity = DEFAULT_INITIAL_CAPACITY;
		a = FloatArrays.forceCapacity(a, capacity, size);
		assert size <= a.length;
	}

	public void add(final int index, final float k) {
		ensureIndex(index);
		grow(size + 1);
		if (index != size) System.arraycopy(a, index, a, index + 1, size - index);
		a[index] = k;
		size++;
		assert size <= a.length;
	}

	public boolean add(final float k) {
		grow(size + 1);
		a[size++] = k;
		assert size <= a.length;
		return true;
	}

	public float getFloat(final int index) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		return a[index];
	}

	public int indexOf(final float k) {
		final float[] a = this.a;
		for (int i = 0; i < size; i++) if ((Float.floatToRawIntBits(k) == Float.floatToRawIntBits(a[i]))) return i;
		return -1;
	}

	public int lastIndexOf(final float k) {
		final float[] a = this.a;
		for (int i = size; i-- != 0;) if ((Float.floatToRawIntBits(k) == Float.floatToRawIntBits(a[i]))) return i;
		return -1;
	}

	public float removeFloat(final int index) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		final float[] a = this.a;
		final float old = a[index];
		size--;
		if (index != size) System.arraycopy(a, index + 1, a, index, size - index);
		assert size <= a.length;
		return old;
	}

	public boolean rem(final float k) {
		int index = indexOf(k);
		if (index == -1) return false;
		removeFloat(index);
		assert size <= a.length;
		return true;
	}

	public float set(final int index, final float k) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		float old = a[index];
		a[index] = k;
		return old;
	}

	public void clear() {
		size = 0;
		assert size <= a.length;
	}

	public int size() {
		return size;
	}

	public void size(final int size) {
		if (size > a.length) a = FloatArrays.forceCapacity(a, size, this.size);
		if (size > this.size) Arrays.fill(a, this.size, size, (0));
		this.size = size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Trims this array list so that the capacity is equal to the size.
	 *
	 * @see java.util.ArrayList#trimToSize()
	 */
	public void trim() {
		trim(0);
	}

	/**
	 * Trims the backing array if it is too large.
	 *
	 * If the current array length is smaller than or equal to {@code n}, this method does nothing.
	 * Otherwise, it trims the array length to the maximum between {@code n} and {@link #size()}.
	 *
	 * <p>
	 * This method is useful when reusing lists. {@linkplain #clear() Clearing a list} leaves the array
	 * length untouched. If you are reusing a list many times, you can call this method with a typical
	 * size to avoid keeping around a very large array just because of a few large transient lists.
	 *
	 * @param n the threshold for the trimming.
	 */

	public void trim(final int n) {
		// TODO: use Arrays.trim() and preserve type only if necessary
		if (n >= a.length || size == a.length) return;
		final float t[] = new float[Math.max(n, size)];
		System.arraycopy(a, 0, t, 0, size);
		a = t;
		assert size <= a.length;
	}

	/**
	 * Copies element of this type-specific list into the given array using optimized system calls.
	 *
	 * @param from the start index (inclusive).
	 * @param a the destination array.
	 * @param offset the offset into the destination array where to store the first element copied.
	 * @param length the number of elements to be copied.
	 */
	public void getElements(final int from, final float[] a, final int offset, final int length) {
		FloatArrays.ensureOffsetLength(a, offset, length);
		System.arraycopy(this.a, from, a, offset, length);
	}

	/**
	 * Removes elements of this type-specific list using optimized system calls.
	 *
	 * @param from the start index (inclusive).
	 * @param to the end index (exclusive).
	 */
	public void removeElements(final int from, final int to) {
		icyllis.arc3d.core.util.Arrays.ensureFromTo(size, from, to);
		System.arraycopy(a, to, a, from, size - to);
		size -= (to - from);
	}

	/**
	 * Adds elements to this type-specific list using optimized system calls.
	 *
	 * @param index the index at which to add elements.
	 * @param a the array containing the elements.
	 * @param offset the offset of the first element to add.
	 * @param length the number of elements to add.
	 */
	public void addElements(final int index, final float[] a, final int offset, final int length) {
		ensureIndex(index);
		FloatArrays.ensureOffsetLength(a, offset, length);
		grow(size + length);
		System.arraycopy(this.a, index, this.a, index + length, size - index);
		System.arraycopy(a, offset, this.a, index, length);
		size += length;
	}

	/**
	 * Sets elements to this type-specific list using optimized system calls.
	 *
	 * @param index the index at which to start setting elements.
	 * @param a the array containing the elements.
	 * @param offset the offset of the first element to add.
	 * @param length the number of elements to add.
	 */
	public void setElements(final int index, final float[] a, final int offset, final int length) {
		ensureIndex(index);
		FloatArrays.ensureOffsetLength(a, offset, length);
		if (index + length > size) throw new IndexOutOfBoundsException("End index (" + (index + length) + ") is greater than list size (" + size + ")");
		System.arraycopy(a, offset, this.a, index, length);
	}

	public boolean addAll(final int index, final FloatArrayList l) {
		ensureIndex(index);
		final int n = l.size();
		if (n == 0) return false;
		grow(size + n);
		System.arraycopy(a, index, a, index + n, size - index);
		l.getElements(0, a, index, n);
		size += n;
		assert size <= a.length;
		return true;
	}

	public float[] toArray(float[] a) {
		if (a == null || a.length < size) a = java.util.Arrays.copyOf(a, size);
		System.arraycopy(this.a, 0, a, 0, size);
		return a;
	}

	@Override

	public FloatArrayList clone() {
		FloatArrayList cloned = null;
		// Test for fastpath we can do if exactly an ArrayList
		if (getClass() == FloatArrayList.class) {
			// Preserve backwards compatibility and make new list have Object[] even if it was wrapped from some
			// subclass.
			cloned = new FloatArrayList(copyArraySafe(a, size), false);
			cloned.size = size;
		} else {
			try {
				cloned = (FloatArrayList)super.clone();
			} catch (CloneNotSupportedException err) {
				// Can't happen
				throw new InternalError(err);
			}
			// Preserve backwards compatibility and make new list have Object[] even if it was wrapped from some
			// subclass.
			cloned.a = copyArraySafe(a, size);
		}
		return cloned;
	}

	/**
	 * Compares this type-specific array list to another one.
	 *
	 * @apiNote This method exists only for sake of efficiency. The implementation inherited from the
	 *          abstract implementation would already work.
	 *
	 * @param l a type-specific array list.
	 * @return true if the argument contains the same elements of this type-specific array list.
	 */
	public boolean equals(final FloatArrayList l) {
		// TODO When minimum version of Java becomes Java 9, use the Arrays.equals which takes bounds, which
		// is vectorized.
		if (l == this) return true;
		int s = size();
		if (s != l.size()) return false;
		final float[] a1 = a;
		final float[] a2 = l.a;
		if (a1 == a2 && s == l.size()) return true;
		while (s-- != 0) if (a1[s] != a2[s]) return false;
		return true;
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (o == null) return false;
		if (!(o instanceof java.util.List)) return false;
		if (o instanceof FloatArrayList) {
			// Safe cast because we are only going to take elements from other list, never give them
			return equals((FloatArrayList)o);
		}
		return super.equals(o);
	}

	/**
	 * Compares this array list to another array list.
	 *
	 * @apiNote This method exists only for sake of efficiency. The implementation inherited from the
	 *          abstract implementation would already work.
	 *
	 * @param l an array list.
	 * @return a negative integer, zero, or a positive integer as this list is lexicographically less
	 *         than, equal to, or greater than the argument.
	 */

	public int compareTo(final FloatArrayList l) {
		final int s1 = size(), s2 = l.size();
		final float[] a1 = a, a2 = l.a;
		if (a1 == a2 && s1 == s2) return 0;
		// TODO When minimum version of Java becomes Java 9, use Arrays.compare, which vectorizes.
		float e1, e2;
		int r, i;
		for (i = 0; i < s1 && i < s2; i++) {
			e1 = a1[i];
			e2 = a2[i];
			if ((r = (Float.compare((e1), (e2)))) != 0) return r;
		}
		return i < s2 ? -1 : (i < s1 ? 1 : 0);
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		s.defaultWriteObject();
		final float[] a = this.a;
		for (int i = 0; i < size; i++) s.writeFloat(a[i]);
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();
		final float[] a = this.a = new float[size];
		for (int i = 0; i < size; i++) a[i] = s.readFloat();
	}

    /**
     * Ensures that the given index is nonnegative and not greater than the list size.
     *
     * @param index an index.
     * @throws IndexOutOfBoundsException if the given index is negative or greater than the list size.
     */
    protected void ensureIndex(final int index) {
        Objects.checkIndex(index, size() + 1);
    }
}

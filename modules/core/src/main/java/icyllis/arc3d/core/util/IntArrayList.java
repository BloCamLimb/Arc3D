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
import java.util.List;
import java.util.ListIterator;
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
public class IntArrayList extends AbstractIntCollection implements IntList, IntStack, RandomAccess, Cloneable, java.io.Serializable {
	private static final long serialVersionUID = -7046029254386353130L;
	/** The initial default capacity of an array list. */
	public static final int DEFAULT_INITIAL_CAPACITY = 10;
	/** The backing array. */
	protected transient int[] a;
	/** The current actual size of the list (never greater than the backing-array length). */
	protected int size;

	/**
	 * Ensures that the component type of the given array is the proper type. This is irrelevant for
	 * primitive types, so it will just do a trivial copy. But for Reference types, you can have a
	 * {@code String[]} masquerading as an {@code Object[]}, which is a case we need to prepare for
	 * because we let the user give an array to use directly with {@link #wrap}.
	 */

	private static final int[] copyArraySafe(int[] a, int length) {
		if (length == 0) return IntArrays.EMPTY_ARRAY;
		return java.util.Arrays.copyOf(a, length);
	}

	private static final int[] copyArrayFromSafe(IntArrayList l) {
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
	protected IntArrayList(final int[] a, @SuppressWarnings("unused") boolean wrapped) {
		this.a = a;
	}

	private void initArrayFromCapacity(final int capacity) {
		if (capacity < 0) throw new IllegalArgumentException("Initial capacity (" + capacity + ") is negative");
		if (capacity == 0) a = IntArrays.EMPTY_ARRAY;
		else a = new int[capacity];
	}

	/**
	 * Creates a new array list with given capacity.
	 *
	 * @param capacity the initial capacity of the array list (may be 0).
	 */
	public IntArrayList(final int capacity) {
		initArrayFromCapacity(capacity);
	}

	/** Creates a new array list with {@link #DEFAULT_INITIAL_CAPACITY} capacity. */

	public IntArrayList() {
		a = IntArrays.DEFAULT_EMPTY_ARRAY; // We delay allocation
	}

	/**
	 * Creates a new array list and fills it with a given type-specific collection.
	 *
	 * @param c a type-specific collection that will be used to fill the array list.
	 */
	public IntArrayList(final IntCollection c) {
		if (c instanceof IntArrayList) {
			a = copyArrayFromSafe((IntArrayList)c);
			size = a.length;
		} else {
			initArrayFromCapacity(c.size());
			if (c instanceof IntList) {
				((IntList)c).getElements(0, a, 0, size = c.size());
			} else {
				size = IntIterators.unwrap(c.iterator(), a);
			}
		}
	}

	/**
	 * Creates a new array list and fills it with a given type-specific list.
	 *
	 * @param l a type-specific list that will be used to fill the array list.
	 */
	public IntArrayList(final IntList l) {
		if (l instanceof IntArrayList) {
			a = copyArrayFromSafe((IntArrayList)l);
			size = a.length;
		} else {
			initArrayFromCapacity(l.size());
			l.getElements(0, a, 0, size = l.size());
		}
	}

	/**
	 * Creates a new array list and fills it with the elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the array list.
	 */
	public IntArrayList(final int[] a) {
		this(a, 0, a.length);
	}

	/**
	 * Creates a new array list and fills it with the elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the array list.
	 * @param offset the first element to use.
	 * @param length the number of elements to use.
	 */
	public IntArrayList(final int[] a, final int offset, final int length) {
		this(length);
		System.arraycopy(a, offset, this.a, 0, length);
		size = length;
	}

	/**
	 * Creates a new array list and fills it with the elements returned by an iterator..
	 *
	 * @param i an iterator whose returned elements will fill the array list.
	 */
	public IntArrayList(final Iterator<? extends Integer> i) {
		this();
		while (i.hasNext()) this.add((i.next()).intValue());
	}

	/**
	 * Creates a new array list and fills it with the elements returned by a type-specific iterator..
	 *
	 * @param i a type-specific iterator whose returned elements will fill the array list.
	 */
	public IntArrayList(final IntIterator i) {
		this();
		while (i.hasNext()) this.add(i.nextInt());
	}

	/**
	 * Returns the backing array of this list.
	 *
	 * @return the backing array.
	 */
	public int[] elements() {
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
	public static IntArrayList wrap(final int[] a, final int length) {
		if (length > a.length) throw new IllegalArgumentException("The specified length (" + length + ") is greater than the array size (" + a.length + ")");
		final IntArrayList l = new IntArrayList(a, true);
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
	public static IntArrayList wrap(final int[] a) {
		return wrap(a, a.length);
	}

	/**
	 * Creates a new empty array list.
	 *
	 * @return a new empty array list.
	 */
	public static IntArrayList of() {
		return new IntArrayList();
	}

	/**
	 * Creates an array list using an array of elements.
	 *
	 * @param init a the array the will become the new backing array of the array list.
	 * @return a new array list backed by the given array.
	 * @see #wrap
	 */

	public static IntArrayList of(final int... init) {
		return wrap(init);
	}

	/**
	 * Collects the result of a primitive {@code Stream} into a new ArrayList.
	 *
	 * <p>
	 * This method performs a terminal operation on the given {@code Stream}
	 *
	 * @apiNote Taking a primitive stream instead of returning something like a
	 *          {@link java.util.stream.Collector Collector} is necessary because there is no primitive
	 *          {@code Collector} equivalent in the Java API.
	 */
	public static IntArrayList toList(java.util.stream.IntStream stream) {
		return stream.collect(IntArrayList::new, IntArrayList::add, IntArrayList::addAll);
	}

	/**
	 * Ensures that this array list can contain the given number of entries without resizing.
	 *
	 * @param capacity the new minimum capacity for this array list.
	 */

	public void ensureCapacity(final int capacity) {
		if (capacity <= a.length || (a == IntArrays.DEFAULT_EMPTY_ARRAY && capacity <= DEFAULT_INITIAL_CAPACITY)) return;
		a = IntArrays.ensureCapacity(a, capacity, size);
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
		if (a != IntArrays.DEFAULT_EMPTY_ARRAY) capacity = (int)Math.max(Math.min((long)a.length + (a.length >> 1), icyllis.arc3d.core.util.Arrays.MAX_ARRAY_SIZE), capacity);
		else if (capacity < DEFAULT_INITIAL_CAPACITY) capacity = DEFAULT_INITIAL_CAPACITY;
		a = IntArrays.forceCapacity(a, capacity, size);
		assert size <= a.length;
	}

	@Override
	public void add(final int index, final int k) {
		ensureIndex(index);
		grow(size + 1);
		if (index != size) System.arraycopy(a, index, a, index + 1, size - index);
		a[index] = k;
		size++;
		assert size <= a.length;
	}

	@Override
	public boolean add(final int k) {
		grow(size + 1);
		a[size++] = k;
		assert size <= a.length;
		return true;
	}

	@Override
	public int getInt(final int index) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		return a[index];
	}

	@Override
	public int indexOf(final int k) {
		final int[] a = this.a;
		for (int i = 0; i < size; i++) if (((k) == (a[i]))) return i;
		return -1;
	}

	@Override
	public int lastIndexOf(final int k) {
		final int[] a = this.a;
		for (int i = size; i-- != 0;) if (((k) == (a[i]))) return i;
		return -1;
	}

	@Override
	public int removeInt(final int index) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		final int[] a = this.a;
		final int old = a[index];
		size--;
		if (index != size) System.arraycopy(a, index + 1, a, index, size - index);
		assert size <= a.length;
		return old;
	}

	@Override
	public boolean rem(final int k) {
		int index = indexOf(k);
		if (index == -1) return false;
		removeInt(index);
		assert size <= a.length;
		return true;
	}

	@Override
	public int set(final int index, final int k) {
		if (index >= size) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than or equal to list size (" + size + ")");
		int old = a[index];
		a[index] = k;
		return old;
	}

	@Override
	public void clear() {
		size = 0;
		assert size <= a.length;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void size(final int size) {
		if (size > a.length) a = IntArrays.forceCapacity(a, size, this.size);
		if (size > this.size) Arrays.fill(a, this.size, size, (0));
		this.size = size;
	}

	@Override
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
		final int t[] = new int[Math.max(n, size)];
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
	@Override
	public void getElements(final int from, final int[] a, final int offset, final int length) {
		IntArrays.ensureOffsetLength(a, offset, length);
		System.arraycopy(this.a, from, a, offset, length);
	}

	/**
	 * Removes elements of this type-specific list using optimized system calls.
	 *
	 * @param from the start index (inclusive).
	 * @param to the end index (exclusive).
	 */
	@Override
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
	@Override
	public void addElements(final int index, final int[] a, final int offset, final int length) {
		ensureIndex(index);
		IntArrays.ensureOffsetLength(a, offset, length);
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
	@Override
	public void setElements(final int index, final int[] a, final int offset, final int length) {
		ensureIndex(index);
		IntArrays.ensureOffsetLength(a, offset, length);
		if (index + length > size) throw new IndexOutOfBoundsException("End index (" + (index + length) + ") is greater than list size (" + size + ")");
		System.arraycopy(a, offset, this.a, index, length);
	}

	@Override
	public void forEach(final java.util.function.IntConsumer action) {
		final int[] a = this.a;
		for (int i = 0; i < size; ++i) {
			action.accept(a[i]);
		}
	}

	@Override
	public boolean addAll(int index, final IntCollection c) {
		if (c instanceof IntList) {
			return addAll(index, (IntList)c);
		}
		ensureIndex(index);
		int n = c.size();
		if (n == 0) return false;
		grow(size + n);
		System.arraycopy(a, index, a, index + n, size - index);
		final IntIterator i = c.iterator();
		size += n;
		while (n-- != 0) a[index++] = i.nextInt();
		assert size <= a.length;
		return true;
	}

	@Override
	public boolean addAll(final int index, final IntList l) {
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

	@Override
	public boolean removeAll(final IntCollection c) {
		final int[] a = this.a;
		int j = 0;
		for (int i = 0; i < size; i++) if (!c.contains(a[i])) a[j++] = a[i];
		final boolean modified = size != j;
		size = j;
		return modified;
	}

	@Override
	public boolean removeIf(final java.util.function.IntPredicate filter) {
		final int[] a = this.a;
		int j = 0;
		for (int i = 0; i < size; i++) if (!filter.test(a[i])) a[j++] = a[i];
		final boolean modified = size != j;
		size = j;
		return modified;
	}

	@Override
	public int[] toArray(int[] a) {
		if (a == null || a.length < size) a = java.util.Arrays.copyOf(a, size);
		System.arraycopy(this.a, 0, a, 0, size);
		return a;
	}

    @Override
    public IntListIterator iterator() {
        return listIterator();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation delegates to {@link #listIterator(int) listIterator(0)}.
     */
    public IntListIterator listIterator() {
        return listIterator(0);
    }

	public IntListIterator listIterator(final int index) {
		ensureIndex(index);
		return new IntListIterator() {
			int pos = index, last = -1;

			@Override
			public boolean hasNext() {
				return pos < size;
			}

			@Override
			public boolean hasPrevious() {
				return pos > 0;
			}

			@Override
			public int nextInt() {
				if (!hasNext()) throw new NoSuchElementException();
				return a[last = pos++];
			}

			@Override
			public int previousInt() {
				if (!hasPrevious()) throw new NoSuchElementException();
				return a[last = --pos];
			}

			@Override
			public int nextIndex() {
				return pos;
			}

			@Override
			public int previousIndex() {
				return pos - 1;
			}

			@Override
			public void add(int k) {
				IntArrayList.this.add(pos++, k);
				last = -1;
			}

			@Override
			public void set(int k) {
				if (last == -1) throw new IllegalStateException();
				IntArrayList.this.set(last, k);
			}

			@Override
			public void remove() {
				if (last == -1) throw new IllegalStateException();
				IntArrayList.this.removeInt(last);
				/* If the last operation was a next(), we are removing an element *before* us, and we must decrease pos correspondingly. */
				if (last < pos) pos--;
				last = -1;
			}

			@Override
			public void forEachRemaining(final java.util.function.IntConsumer action) {
				final int[] a = IntArrayList.this.a;
				while (pos < size) {
					action.accept(a[last = pos++]);
				}
			}

			@Override
			public int back(int n) {
				if (n < 0) throw new IllegalArgumentException("Argument must be nonnegative: " + n);
				final int remaining = pos;
				if (n < remaining) {
					pos -= n;
				} else {
					n = remaining;
					pos = 0;
				}
				last = pos;
				return n;
			}

			@Override
			public int skip(int n) {
				if (n < 0) throw new IllegalArgumentException("Argument must be nonnegative: " + n);
				final int remaining = size - pos;
				if (n < remaining) {
					pos += n;
				} else {
					n = remaining;
					pos = size;
				}
				last = pos - 1;
				return n;
			}
		};
	}

	@Override
	public void sort(final IntComparator comp) {
		if (comp == null) {
			IntArrays.stableSort(a, 0, size);
		} else {
			IntArrays.stableSort(a, 0, size, comp);
		}
	}

	@Override
	public void unstableSort(final IntComparator comp) {
		if (comp == null) {
			IntArrays.unstableSort(a, 0, size);
		} else {
			IntArrays.unstableSort(a, 0, size, comp);
		}
	}

	@Override

	public IntArrayList clone() {
		IntArrayList cloned = null;
		// Test for fastpath we can do if exactly an ArrayList
		if (getClass() == IntArrayList.class) {
			// Preserve backwards compatibility and make new list have Object[] even if it was wrapped from some
			// subclass.
			cloned = new IntArrayList(copyArraySafe(a, size), false);
			cloned.size = size;
		} else {
			try {
				cloned = (IntArrayList)super.clone();
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
	public boolean equals(final IntArrayList l) {
		if (l == this) return true;
		int s = size();
		if (s != l.size()) return false;
		final int[] a1 = a;
		final int[] a2 = l.a;
		if (a1 == a2 && s == l.size()) return true;
		return Arrays.equals(a1, 0, s, a2, 0, l.size());
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (o == null) return false;
		if (!(o instanceof java.util.List)) return false;
		if (o instanceof IntArrayList) {
			// Safe cast because we are only going to take elements from other list, never give them
			return equals((IntArrayList)o);
		}
        final List<?> l = (List<?>)o;
        int s = size();
        if (s != l.size()) return false;
        if (l instanceof IntList) {
            final IntListIterator i1 = listIterator(), i2 = ((IntList)l).listIterator();
            while (s-- != 0) if (i1.nextInt() != i2.nextInt()) return false;
            return true;
        }
        final IntListIterator i1 = listIterator();
        final ListIterator<?> i2 = l.listIterator();
        while (s-- != 0) if (!java.util.Objects.equals(i1.nextInt(), i2.next())) return false;
        return true;
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

	public int compareTo(final IntArrayList l) {
		final int s1 = size(), s2 = l.size();
		final int[] a1 = a, a2 = l.a;
		if (a1 == a2 && s1 == s2) return 0;
		return Arrays.compare(a1, 0, s1, a2, 0, s2);
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		s.defaultWriteObject();
		final int[] a = this.a;
		for (int i = 0; i < size; i++) s.writeInt(a[i]);
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();
		final int[] a = this.a = new int[size];
		for (int i = 0; i < size; i++) a[i] = s.readInt();
	}

    @Override
    public void push(final int o) {
        add(o);
    }

    @Override
    public int popInt() {
        if (isEmpty()) throw new NoSuchElementException();
        return removeInt(size() - 1);
    }

    @Override
    public int topInt() {
        if (isEmpty()) throw new NoSuchElementException();
        return getInt(size() - 1);
    }

    @Override
    public int peekInt(final int i) {
        return getInt(size() - 1 - i);
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation delegates to the analogous method for array fragments.
     */
    @Override
    public void addElements(final int index, final int a[]) {
        addElements(index, a, 0, a.length);
    }

    @Override
    public int[] toIntArray() {
        final int size = size();
        if (size == 0) return IntArrays.EMPTY_ARRAY;
        int[] ret = new int[size];
        getElements(0, ret, 0, size);
        return ret;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation delegates to the type-specific version of
     *           {@link List#addAll(int, Collection)}.
     */
    @Override
    public boolean addAll(final IntCollection c) {
        return addAll(size(), c);
    }

    /**
     * Returns true if this list contains the specified element.
     *
     * @implSpec This implementation delegates to {@code indexOf()}.
     * @see List#contains(Object)
     */
    @Override
    public boolean contains(final int k) {
        return indexOf(k) >= 0;
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

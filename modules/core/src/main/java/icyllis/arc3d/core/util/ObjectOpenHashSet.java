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

import static icyllis.arc3d.core.util.HashCommon.arraySize;
import static icyllis.arc3d.core.util.HashCommon.maxFill;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collector;

/**
 * A type-specific hash set with with a fast, small-footprint implementation.
 *
 * <p>
 * Instances of this class use a hash table to represent a set. The table is filled up to a
 * specified <em>load factor</em>, and then doubled in size to accommodate new entries. If the table
 * is emptied below <em>one fourth</em> of the load factor, it is halved in size; however, the table
 * is never reduced to a size smaller than that at creation time: this approach makes it possible to
 * create sets with a large capacity in which insertions and deletions do not cause immediately
 * rehashing. Moreover, halving is not performed when deleting entries from an iterator, as it would
 * interfere with the iteration process.
 *
 * <p>
 * Note that {@link #clear()} does not modify the hash table size. Rather, a family of
 * {@linkplain #trim() trimming methods} lets you control the size of the table; this is
 * particularly useful if you reuse instances of this class.
 *
 * @see Hash
 * @see HashCommon
 */
public class ObjectOpenHashSet<K> extends AbstractSet<K> implements java.io.Serializable, Cloneable, Hash {
	private static final long serialVersionUID = 0L;
	private static final boolean ASSERTS = false;
	/** The array of keys. */
	protected transient K[] key;
	/** The mask for wrapping a position counter. */
	protected transient int mask;
	/** Whether this set contains the null key. */
	protected transient boolean containsNull;
	/**
	 * The current table size. Note that an additional element is allocated for storing the null key.
	 */
	protected transient int n;
	/** Threshold after which we rehash. It must be the table size times {@link #f}. */
	protected transient int maxFill;
	/** We never resize below this threshold, which is the construction-time {#n}. */
	protected final transient int minN;
	/** Number of entries in the set (including the null key, if present). */
	protected int size;
	/** The acceptable load factor. */
	protected final float f;

	/**
	 * Creates a new hash set.
	 *
	 * <p>
	 * The actual table size will be the least power of two greater than {@code expected}/{@code f}.
	 *
	 * @param expected the expected number of elements in the hash set.
	 * @param f the load factor.
	 */
	@SuppressWarnings("unchecked")
	public ObjectOpenHashSet(final int expected, final float f) {
		if (f <= 0 || f >= 1) throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than 1");
		if (expected < 0) throw new IllegalArgumentException("The expected number of elements must be nonnegative");
		this.f = f;
		minN = n = arraySize(expected, f);
		mask = n - 1;
		maxFill = maxFill(n, f);
		key = (K[])new Object[n + 1];
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
	 *
	 * @param expected the expected number of elements in the hash set.
	 */
	public ObjectOpenHashSet(final int expected) {
		this(expected, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new hash set with initial expected {@link Hash#DEFAULT_INITIAL_SIZE} elements and
	 * {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
	 */
	public ObjectOpenHashSet() {
		this(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new hash set copying a given collection.
	 *
	 * @param c a {@link Collection} to be copied into the new hash set.
	 * @param f the load factor.
	 */
	public ObjectOpenHashSet(final Collection<? extends K> c, final float f) {
		this(c.size(), f);
		addAll(c);
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor copying a given
	 * collection.
	 *
	 * @param c a {@link Collection} to be copied into the new hash set.
	 */
	public ObjectOpenHashSet(final Collection<? extends K> c) {
		this(c, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new hash set using elements provided by a type-specific iterator.
	 *
	 * @param i a type-specific iterator whose elements will fill the set.
	 * @param f the load factor.
	 */
	public ObjectOpenHashSet(final Iterator<? extends K> i, final float f) {
		this(DEFAULT_INITIAL_SIZE, f);
		while (i.hasNext()) add(i.next());
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using elements
	 * provided by a type-specific iterator.
	 *
	 * @param i a type-specific iterator whose elements will fill the set.
	 */
	public ObjectOpenHashSet(final Iterator<? extends K> i) {
		this(i, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new hash set and fills it with the elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the set.
	 * @param offset the first element to use.
	 * @param length the number of elements to use.
	 * @param f the load factor.
	 */
	public ObjectOpenHashSet(final K[] a, final int offset, final int length, final float f) {
		this(length < 0 ? 0 : length, f);
		ObjectArrays.ensureOffsetLength(a, offset, length);
		for (int i = 0; i < length; i++) add(a[offset + i]);
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor and fills it with the
	 * elements of a given array.
	 *
	 * @param a an array whose elements will be used to fill the set.
	 * @param offset the first element to use.
	 * @param length the number of elements to use.
	 */
	public ObjectOpenHashSet(final K[] a, final int offset, final int length) {
		this(a, offset, length, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new hash set copying the elements of an array.
	 *
	 * @param a an array to be copied into the new hash set.
	 * @param f the load factor.
	 */
	public ObjectOpenHashSet(final K[] a, final float f) {
		this(a, 0, a.length, f);
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor copying the elements
	 * of an array.
	 *
	 * @param a an array to be copied into the new hash set.
	 */
	public ObjectOpenHashSet(final K[] a) {
		this(a, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new empty hash set.
	 *
	 * @return a new empty hash set.
	 */
	public static <K> ObjectOpenHashSet<K> of() {
		return new ObjectOpenHashSet<>();
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the given
	 * element.
	 *
	 * @param e the element that the returned set will contain.
	 * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing {@code e}.
	 */
	public static <K> ObjectOpenHashSet<K> of(final K e) {
		ObjectOpenHashSet<K> result = new ObjectOpenHashSet<>(1, DEFAULT_LOAD_FACTOR);
		result.add(e);
		return result;
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the elements
	 * given.
	 *
	 * @param e0 the first element.
	 * @param e1 the second element.
	 * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing {@code e0}
	 *         and {@code e1}.
	 * @throws IllegalArgumentException if there were duplicate entries.
	 */
	public static <K> ObjectOpenHashSet<K> of(final K e0, final K e1) {
		ObjectOpenHashSet<K> result = new ObjectOpenHashSet<>(2, DEFAULT_LOAD_FACTOR);
		result.add(e0);
		if (!result.add(e1)) {
			throw new IllegalArgumentException("Duplicate element: " + e1);
		}
		return result;
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using the elements
	 * given.
	 *
	 * @param e0 the first element.
	 * @param e1 the second element.
	 * @param e2 the third element.
	 * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing
	 *         {@code e0}, {@code e1}, and {@code e2}.
	 * @throws IllegalArgumentException if there were duplicate entries.
	 */
	public static <K> ObjectOpenHashSet<K> of(final K e0, final K e1, final K e2) {
		ObjectOpenHashSet<K> result = new ObjectOpenHashSet<>(3, DEFAULT_LOAD_FACTOR);
		result.add(e0);
		if (!result.add(e1)) {
			throw new IllegalArgumentException("Duplicate element: " + e1);
		}
		if (!result.add(e2)) {
			throw new IllegalArgumentException("Duplicate element: " + e2);
		}
		return result;
	}

	/**
	 * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor using a list of
	 * elements.
	 *
	 * @param a a list of elements that will be used to initialize the new hash set.
	 * @return a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor containing the
	 *         elements of {@code a}.
	 * @throws IllegalArgumentException if a duplicate entry was encountered.
	 */
	@SafeVarargs
	public static <K> ObjectOpenHashSet<K> of(final K... a) {
		ObjectOpenHashSet<K> result = new ObjectOpenHashSet<>(a.length, DEFAULT_LOAD_FACTOR);
		for (K element : a) {
			if (!result.add(element)) {
				throw new IllegalArgumentException("Duplicate element " + element);
			}
		}
		return result;
	}

	// Collector wants a function that returns the collection being added to.
	private ObjectOpenHashSet<K> combine(ObjectOpenHashSet<? extends K> toAddFrom) {
		addAll(toAddFrom);
		return this;
	}

	private static final Collector<Object, ?, ObjectOpenHashSet<Object>> TO_SET_COLLECTOR = Collector.of(ObjectOpenHashSet::new, ObjectOpenHashSet::add, ObjectOpenHashSet::combine, Collector.Characteristics.UNORDERED);

	/** Returns a {@link Collector} that collects a {@code Stream}'s elements into a new hash set. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <K> Collector<K, ?, ObjectOpenHashSet<K>> toSet() {
		return (Collector)TO_SET_COLLECTOR;
	}

	private int realSize() {
		return containsNull ? size - 1 : size;
	}

	/**
	 * Ensures that this set can hold a certain number of elements without rehashing.
	 *
	 * @param capacity a number of elements; there will be no rehashing unless the set
	 *            {@linkplain #size() size} exceeds this number.
	 */
	public void ensureCapacity(final int capacity) {
		final int needed = arraySize(capacity, f);
		if (needed > n) rehash(needed);
	}

	private void tryCapacity(final long capacity) {
		final int needed = (int)Math.min(1 << 30, Math.max(2, HashCommon.nextPowerOfTwo((long)Math.ceil(capacity / f))));
		if (needed > n) rehash(needed);
	}

	@Override
	public boolean addAll(Collection<? extends K> c) {
		// The resulting collection will be at least c.size() big
		if (f <= .5) ensureCapacity(c.size()); // The resulting collection will be sized for c.size() elements
		else tryCapacity(size() + c.size()); // The resulting collection will be tentatively sized for size() + c.size()
												// elements
		return super.addAll(c);
	}

	@Override
	public boolean add(final K k) {
		int pos;
		if (((k) == null)) {
			if (containsNull) return false;
			containsNull = true;
		} else {
			K curr;
			final K[] key = this.key;
			// The starting point.
			if (!((curr = key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) {
				if (((curr).equals(k))) return false;
				while (!((curr = key[pos = (pos + 1) & mask]) == null)) if (((curr).equals(k))) return false;
			}
			key[pos] = k;
		}
		if (size++ >= maxFill) rehash(arraySize(size + 1, f));
		if (ASSERTS) checkTable();
		return true;
	}

	/**
	 * Add a random element if not present, get the existing value if already present.
	 *
	 * This is equivalent to (but faster than) doing a:
	 * 
	 * <pre>
	 * K exist = set.get(k);
	 * if (exist == null) {
	 * 	set.add(k);
	 * 	exist = k;
	 * }
	 * </pre>
	 */
	public K addOrGet(final K k) {
		int pos;
		if (((k) == null)) {
			if (containsNull) return key[n];
			containsNull = true;
		} else {
			K curr;
			final K[] key = this.key;
			// The starting point.
			if (!((curr = key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) {
				if (((curr).equals(k))) return curr;
				while (!((curr = key[pos = (pos + 1) & mask]) == null)) if (((curr).equals(k))) return curr;
			}
			key[pos] = k;
		}
		if (size++ >= maxFill) rehash(arraySize(size + 1, f));
		if (ASSERTS) checkTable();
		return k;
	}

	/**
	 * Shifts left entries with the specified hash code, starting at the specified position, and empties
	 * the resulting free entry.
	 *
	 * @param pos a starting position.
	 */
	protected final void shiftKeys(int pos) {
		// Shift entries with the same hash.
		int last, slot;
		K curr;
		final K[] key = this.key;
		for (;;) {
			pos = ((last = pos) + 1) & mask;
			for (;;) {
				if (((curr = key[pos]) == null)) {
					key[last] = (null);
					return;
				}
				slot = (icyllis.arc3d.core.util.HashCommon.mix((curr).hashCode())) & mask;
				if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
				pos = (pos + 1) & mask;
			}
			key[last] = curr;
		}
	}

	private boolean removeEntry(final int pos) {
		size--;
		shiftKeys(pos);
		if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) rehash(n / 2);
		return true;
	}

	private boolean removeNullEntry() {
		containsNull = false;
		key[n] = (null);
		size--;
		if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) rehash(n / 2);
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean remove(final Object k) {
		if ((((K)k) == null)) {
			if (containsNull) return removeNullEntry();
			return false;
		}
		K curr;
		final K[] key = this.key;
		int pos;
		// The starting point.
		if (((curr = key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) return false;
		if (((k).equals(curr))) return removeEntry(pos);
		while (true) {
			if (((curr = key[pos = (pos + 1) & mask]) == null)) return false;
			if (((k).equals(curr))) return removeEntry(pos);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean contains(final Object k) {
		if ((((K)k) == null)) return containsNull;
		K curr;
		final K[] key = this.key;
		int pos;
		// The starting point.
		if (((curr = key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) return false;
		if (((k).equals(curr))) return true;
		while (true) {
			if (((curr = key[pos = (pos + 1) & mask]) == null)) return false;
			if (((k).equals(curr))) return true;
		}
	}

	/**
	 * Returns the element of this set that is equal to the given key, or {@code null}.
	 * 
	 * @return the element of this set that is equal to the given key, or {@code null}.
	 */
	@SuppressWarnings("unchecked")
	public K get(final Object k) {
		if ((((K)k) == null)) return key[n]; // This is correct independently of the value of containsNull and of the
												// set being custom
		K curr;
		final K[] key = this.key;
		int pos;
		// The starting point.
		if (((curr = key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) return null;
		if (((k).equals(curr))) return curr;
		// There's always an unused entry.
		while (true) {
			if (((curr = key[pos = (pos + 1) & mask]) == null)) return null;
			if (((k).equals(curr))) return curr;
		}
	}

	/**
	 * Removes all elements from this set.
	 *
	 * <p>
	 * To increase object reuse, this method does not change the table size. If you want to reduce the
	 * table size, you must use {@link #trim()}.
	 *
	 */
	@Override
	public void clear() {
		if (size == 0) return;
		size = 0;
		containsNull = false;
		Arrays.fill(key, (null));
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	/** An iterator over a hash set. */
	private final class SetIterator implements Iterator<K> {
		/**
		 * The index of the last entry returned, if positive or zero; initially, {@link #n}. If negative,
		 * the last element returned was that of index {@code - pos - 1} from the {@link #wrapped} list.
		 */
		int pos = n;
		/**
		 * The index of the last entry that has been returned (more precisely, the value of {@link #pos} if
		 * {@link #pos} is positive, or {@link Integer#MIN_VALUE} if {@link #pos} is negative). It is -1 if
		 * either we did not return an entry yet, or the last returned entry has been removed.
		 */
		int last = -1;
		/** A downward counter measuring how many entries must still be returned. */
		int c = size;
		/** A boolean telling us whether we should return the null key. */
		boolean mustReturnNull = ObjectOpenHashSet.this.containsNull;
		/**
		 * A lazily allocated list containing elements that have wrapped around the table because of
		 * removals.
		 */
		ObjectArrayList<K> wrapped;

		@Override
		public boolean hasNext() {
			return c != 0;
		}

		@Override
		public K next() {
			if (!hasNext()) throw new NoSuchElementException();
			c--;
			final K key[] = ObjectOpenHashSet.this.key;
			if (mustReturnNull) {
				mustReturnNull = false;
				last = n;
				return key[n];
			}
			for (;;) {
				if (--pos < 0) {
					// We are just enumerating elements from the wrapped list.
					last = Integer.MIN_VALUE;
					return wrapped.get(-pos - 1);
				}
				if (!((key[pos]) == null)) return key[last = pos];
			}
		}

		/**
		 * Shifts left entries with the specified hash code, starting at the specified position, and empties
		 * the resulting free entry.
		 *
		 * @param pos a starting position.
		 */
		private final void shiftKeys(int pos) {
			// Shift entries with the same hash.
			int last, slot;
			K curr;
			final K[] key = ObjectOpenHashSet.this.key;
			for (;;) {
				pos = ((last = pos) + 1) & mask;
				for (;;) {
					if (((curr = key[pos]) == null)) {
						key[last] = (null);
						return;
					}
					slot = (icyllis.arc3d.core.util.HashCommon.mix((curr).hashCode())) & mask;
					if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
					pos = (pos + 1) & mask;
				}
				if (pos < last) { // Wrapped entry.
					if (wrapped == null) wrapped = new ObjectArrayList<>(2);
					wrapped.add(key[pos]);
				}
				key[last] = curr;
			}
		}

		@Override
		public void remove() {
			if (last == -1) throw new IllegalStateException();
			if (last == n) {
				ObjectOpenHashSet.this.containsNull = false;
				ObjectOpenHashSet.this.key[n] = (null);
			} else if (pos >= 0) shiftKeys(last);
			else {
				// We're removing wrapped entries.
				ObjectOpenHashSet.this.remove(wrapped.set(-pos - 1, null));
				last = -1; // Note that we must not decrement size
				return;
			}
			size--;
			last = -1; // You can no longer remove this entry.
			if (ASSERTS) checkTable();
		}

		@Override
		public void forEachRemaining(final Consumer<? super K> action) {
			final K key[] = ObjectOpenHashSet.this.key;
			if (mustReturnNull) {
				mustReturnNull = false;
				last = n;
				action.accept(key[n]);
				c--;
			}
			while (c != 0) {
				if (--pos < 0) {
					// We are just enumerating elements from the wrapped list.
					last = Integer.MIN_VALUE;
					action.accept(wrapped.get(-pos - 1));
					c--;
				} else if (!((key[pos]) == null)) {
					action.accept(key[last = pos]);
					c--;
				}
			}
		}
	}

	@Override
	public Iterator<K> iterator() {
		return new SetIterator();
	}

	@Override
	public void forEach(final Consumer<? super K> action) {
		final K key[] = this.key;
		if (containsNull) action.accept(key[n]);
		for (int pos = n; pos-- != 0;) if (!((key[pos]) == null)) action.accept(key[pos]);
	}

	/**
	 * Rehashes this set, making the table as small as possible.
	 *
	 * <p>
	 * This method rehashes the table to the smallest size satisfying the load factor. It can be used
	 * when the set will not be changed anymore, so to optimize access speed and size.
	 *
	 * <p>
	 * If the table size is already the minimum possible, this method does nothing.
	 *
	 * @return true if there was enough memory to trim the set.
	 * @see #trim(int)
	 */
	public boolean trim() {
		return trim(size);
	}

	/**
	 * Rehashes this set if the table is too large.
	 *
	 * <p>
	 * Let <var>N</var> be the smallest table size that can hold <code>max(n,{@link #size()})</code>
	 * entries, still satisfying the load factor. If the current table size is smaller than or equal to
	 * <var>N</var>, this method does nothing. Otherwise, it rehashes this set in a table of size
	 * <var>N</var>.
	 *
	 * <p>
	 * This method is useful when reusing sets. {@linkplain #clear() Clearing a set} leaves the table
	 * size untouched. If you are reusing a set many times, you can call this method with a typical size
	 * to avoid keeping around a very large table just because of a few large transient sets.
	 *
	 * @param n the threshold for the trimming.
	 * @return true if there was enough memory to trim the set.
	 * @see #trim()
	 */
	public boolean trim(final int n) {
		final int l = HashCommon.nextPowerOfTwo((int)Math.ceil(n / f));
		if (l >= this.n || size > maxFill(l, f)) return true;
		try {
			rehash(l);
		} catch (OutOfMemoryError cantDoIt) {
			return false;
		}
		return true;
	}

	/**
	 * Rehashes the set.
	 *
	 * <p>
	 * This method implements the basic rehashing strategy, and may be overriden by subclasses
	 * implementing different rehashing strategies (e.g., disk-based rehashing). However, you should not
	 * override this method unless you understand the internal workings of this class.
	 *
	 * @param newN the new size
	 */
	@SuppressWarnings("unchecked")
	protected void rehash(final int newN) {
		final K key[] = this.key;
		final int mask = newN - 1; // Note that this is used by the hashing macro
		final K newKey[] = (K[])new Object[newN + 1];
		int i = n, pos;
		for (int j = realSize(); j-- != 0;) {
			while (((key[--i]) == null));
			if (!((newKey[pos = (icyllis.arc3d.core.util.HashCommon.mix((key[i]).hashCode())) & mask]) == null)) while (!((newKey[pos = (pos + 1) & mask]) == null));
			newKey[pos] = key[i];
		}
		n = newN;
		this.mask = mask;
		maxFill = maxFill(n, f);
		this.key = newKey;
	}

	/**
	 * Returns a deep copy of this set.
	 *
	 * <p>
	 * This method performs a deep copy of this hash set; the data stored in the set, however, is not
	 * cloned. Note that this makes a difference only for object keys.
	 *
	 * @return a deep copy of this set.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public ObjectOpenHashSet<K> clone() {
		ObjectOpenHashSet<K> c;
		try {
			c = (ObjectOpenHashSet<K>)super.clone();
		} catch (CloneNotSupportedException cantHappen) {
			throw new InternalError();
		}
		c.key = key.clone();
		c.containsNull = containsNull;
		return c;
	}

	/**
	 * Returns a hash code for this set.
	 *
	 * This method overrides the generic method provided by the superclass. Since {@code equals()} is
	 * not overriden, it is important that the value returned by this method is the same value as the
	 * one returned by the overriden method.
	 *
	 * @return a hash code for this set.
	 */
	@Override
	public int hashCode() {
		int h = 0;
		final K[] key = ObjectOpenHashSet.this.key;
		for (int j = realSize(), i = 0; j-- != 0;) {
			while (((key[i]) == null)) i++;
			if (this != key[i]) h += ((key[i]).hashCode());
			i++;
		}
		// Zero / null have hash zero.
		return h;
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		final Iterator<K> i = iterator();
		s.defaultWriteObject();
		for (int j = size; j-- != 0;) s.writeObject(i.next());
	}

	@SuppressWarnings("unchecked")
	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();
		n = arraySize(size, f);
		maxFill = maxFill(n, f);
		mask = n - 1;
		final K key[] = this.key = (K[])new Object[n + 1];
		K k;
		for (int i = size, pos; i-- != 0;) {
			k = (K)s.readObject();
			if (((k) == null)) {
				pos = n;
				containsNull = true;
			} else {
				if (!((key[pos = (icyllis.arc3d.core.util.HashCommon.mix((k).hashCode())) & mask]) == null)) while (!((key[pos = (pos + 1) & mask]) == null));
			}
			key[pos] = k;
		}
		if (ASSERTS) checkTable();
	}

	private void checkTable() {
	}
}

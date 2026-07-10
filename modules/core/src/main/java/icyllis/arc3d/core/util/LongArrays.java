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

import java.util.Random;
import java.util.concurrent.ForkJoinPool;

/**
 * A class providing static methods and objects that do useful things with type-specific arrays.
 *
 * <p>
 * In particular, the {@code forceCapacity()}, {@code ensureCapacity()}, {@code grow()},
 * {@code trim()} and {@code setLength()} methods allow to handle arrays much like array lists. This
 * can be very useful when efficiency (or syntactic simplicity) reasons make array lists unsuitable.
 *
 * <p>
 * Note that {@link it.unimi.dsi.fastutil.io.BinIO} and {@link it.unimi.dsi.fastutil.io.TextIO}
 * contain several methods make it possible to load and save arrays of primitive types as sequences
 * of elements in {@link java.io.DataInput} format (i.e., not as objects) or as sequences of lines
 * of text.
 *
 * <h2>Sorting</h2>
 *
 * <p>
 * There are several sorting methods available. The main theme is that of letting you choose the
 * sorting algorithm you prefer (i.e., trading stability of mergesort for no memory allocation in
 * quicksort).
 * 
 * <h3>Parallel operations</h3> Some algorithms provide a parallel version that will by default use
 * the {@linkplain ForkJoinPool#commonPool() common pool}, but this can be overridden by calling the
 * function in a task already in the {@link ForkJoinPool} that the operation should run in. For
 * example, something along the lines of
 * "{@code poolToParallelSortIn.invoke(() -> parallelQuickSort(arrayToSort))}" will run the parallel
 * sort in {@code poolToParallelSortIn} instead of the default pool.
 *
 * Some algorithms also provide an explicit <em>indirect</em> sorting facility, which makes it
 * possible to sort an array using the values in another array as comparator.
 *
 * <p>
 * However, if you wish to let the implementation choose an algorithm for you, both
 * {@link #stableSort} and {@link #unstableSort} methods are available, which dynamically chooses an
 * algorithm based on unspecified criteria (but most likely stability, array size, and array element
 * type).
 *
 * <p>
 * All comparison-based algorithm have an implementation based on a type-specific comparator.
 *
 * <p>
 * As a general rule, sequential radix sort is significantly faster than quicksort or mergesort, in
 * particular on random-looking data. In the parallel case, up to a few cores parallel radix sort is
 * still the fastest, but at some point quicksort exploits parallelism better.
 *
 * <p>
 * If you are fine with not knowing exactly which algorithm will be run (in particular, not knowing
 * exactly whether a support array will be allocated), the dual-pivot parallel sorts in
 * {@link java.util.Arrays} are about 50% faster than the classical single-pivot implementation used
 * here.
 *
 * <p>
 * In any case, if sorting time is important I suggest that you benchmark your sorting load with
 * your data distribution and on your architecture.
 *
 * @see java.util.Arrays
 */
public final class LongArrays {
	private LongArrays() {
	}

	/** A static, final, empty array. */
	public static final long[] EMPTY_ARRAY = {};
	/**
	 * A static, final, empty array to be used as default array in allocations. An object distinct from
	 * {@link #EMPTY_ARRAY} makes it possible to have different behaviors depending on whether the user
	 * required an empty allocation, or we are just lazily delaying allocation.
	 *
	 * @see java.util.ArrayList
	 */
	public static final long[] DEFAULT_EMPTY_ARRAY = {};

	/**
	 * Forces an array to contain the given number of entries, preserving just a part of the array.
	 *
	 * @param array an array.
	 * @param length the new minimum length for this array.
	 * @param preserve the number of elements of the array that must be preserved in case a new
	 *            allocation is necessary.
	 * @return an array with {@code length} entries whose first {@code preserve} entries are the same as
	 *         those of {@code array}.
	 */
	public static long[] forceCapacity(final long[] array, final int length, final int preserve) {
		final long t[] = new long[length];
		System.arraycopy(array, 0, t, 0, preserve);
		return t;
	}

	/**
	 * Ensures that an array can contain the given number of entries.
	 *
	 * <p>
	 * If you cannot foresee whether this array will need again to be enlarged, you should probably use
	 * {@code grow()} instead.
	 *
	 * @param array an array.
	 * @param length the new minimum length for this array.
	 * @return {@code array}, if it contains {@code length} entries or more; otherwise, an array with
	 *         {@code length} entries whose first {@code array.length} entries are the same as those of
	 *         {@code array}.
	 */
	public static long[] ensureCapacity(final long[] array, final int length) {
		return ensureCapacity(array, length, array.length);
	}

	/**
	 * Ensures that an array can contain the given number of entries, preserving just a part of the
	 * array.
	 *
	 * @param array an array.
	 * @param length the new minimum length for this array.
	 * @param preserve the number of elements of the array that must be preserved in case a new
	 *            allocation is necessary.
	 * @return {@code array}, if it can contain {@code length} entries or more; otherwise, an array with
	 *         {@code length} entries whose first {@code preserve} entries are the same as those of
	 *         {@code array}.
	 */
	public static long[] ensureCapacity(final long[] array, final int length, final int preserve) {
		return length > array.length ? forceCapacity(array, length, preserve) : array;
	}

	/**
	 * Grows the given array to the maximum between the given length and the current length increased by
	 * 50%, provided that the given length is larger than the current length.
	 *
	 * <p>
	 * If you want complete control on the array growth, you should probably use
	 * {@code ensureCapacity()} instead.
	 *
	 * @param array an array.
	 * @param length the new minimum length for this array.
	 * @return {@code array}, if it can contain {@code length} entries; otherwise, an array with
	 *         max({@code length},{@code array.length}/&phi;) entries whose first {@code array.length}
	 *         entries are the same as those of {@code array}.
	 */
	public static long[] grow(final long[] array, final int length) {
		return grow(array, length, array.length);
	}

	/**
	 * Grows the given array to the maximum between the given length and the current length increased by
	 * 50%, provided that the given length is larger than the current length, preserving just a part of
	 * the array.
	 *
	 * <p>
	 * If you want complete control on the array growth, you should probably use
	 * {@code ensureCapacity()} instead.
	 *
	 * @param array an array.
	 * @param length the new minimum length for this array.
	 * @param preserve the number of elements of the array that must be preserved in case a new
	 *            allocation is necessary.
	 * @return {@code array}, if it can contain {@code length} entries; otherwise, an array with
	 *         max({@code length},{@code array.length}/&phi;) entries whose first {@code preserve}
	 *         entries are the same as those of {@code array}.
	 */
	public static long[] grow(final long[] array, final int length, final int preserve) {
		if (length > array.length) {
			final int newLength = (int)Math.max(Math.min((long)array.length + (array.length >> 1), Arrays.MAX_ARRAY_SIZE), length);
			final long t[] = new long[newLength];
			System.arraycopy(array, 0, t, 0, preserve);
			return t;
		}
		return array;
	}

	/**
	 * Trims the given array to the given length.
	 *
	 * @param array an array.
	 * @param length the new maximum length for the array.
	 * @return {@code array}, if it contains {@code length} entries or less; otherwise, an array with
	 *         {@code length} entries whose entries are the same as the first {@code length} entries of
	 *         {@code array}.
	 *
	 */
	public static long[] trim(final long[] array, final int length) {
		if (length >= array.length) return array;
		final long t[] = length == 0 ? EMPTY_ARRAY : new long[length];
		System.arraycopy(array, 0, t, 0, length);
		return t;
	}

	/**
	 * Sets the length of the given array.
	 *
	 * @param array an array.
	 * @param length the new length for the array.
	 * @return {@code array}, if it contains exactly {@code length} entries; otherwise, if it contains
	 *         <em>more</em> than {@code length} entries, an array with {@code length} entries whose
	 *         entries are the same as the first {@code length} entries of {@code array}; otherwise, an
	 *         array with {@code length} entries whose first {@code array.length} entries are the same
	 *         as those of {@code array}.
	 *
	 */
	public static long[] setLength(final long[] array, final int length) {
		if (length == array.length) return array;
		if (length < array.length) return trim(array, length);
		return ensureCapacity(array, length);
	}

	/**
	 * Returns a copy of a portion of an array.
	 *
	 * @param array an array.
	 * @param offset the first element to copy.
	 * @param length the number of elements to copy.
	 * @return a new array containing {@code length} elements of {@code array} starting at
	 *         {@code offset}.
	 */
	public static long[] copy(final long[] array, final int offset, final int length) {
		ensureOffsetLength(array, offset, length);
		final long[] a = length == 0 ? EMPTY_ARRAY : new long[length];
		System.arraycopy(array, offset, a, 0, length);
		return a;
	}

	/**
	 * Returns a copy of an array.
	 *
	 * @param array an array.
	 * @return a copy of {@code array}.
	 */
	public static long[] copy(final long[] array) {
		return array.clone();
	}

	/**
	 * Ensures that a range given by its first (inclusive) and last (exclusive) elements fits an array.
	 *
	 * <p>
	 * This method may be used whenever an array range check is needed.
	 *
	 * <p>
	 * In Java 9 and up, this method should be considered deprecated in favor of the
	 * {@link java.util.Objects#checkFromToIndex(int, int, int)} method, which may be intrinsified in
	 * recent JVMs.
	 *
	 * @param a an array.
	 * @param from a start index (inclusive).
	 * @param to an end index (exclusive).
	 * @throws IllegalArgumentException if {@code from} is greater than {@code to}.
	 * @throws ArrayIndexOutOfBoundsException if {@code from} or {@code to} are greater than the array
	 *             length or negative.
	 */
	public static void ensureFromTo(final long[] a, final int from, final int to) {
		Arrays.ensureFromTo(a.length, from, to);
	}

	/**
	 * Ensures that a range given by an offset and a length fits an array.
	 *
	 * <p>
	 * This method may be used whenever an array range check is needed.
	 *
	 * <p>
	 * In Java 9 and up, this method should be considered deprecated in favor of the
	 * {@link java.util.Objects#checkFromIndexSize(int, int, int)} method, which may be intrinsified in
	 * recent JVMs.
	 *
	 * @param a an array.
	 * @param offset a start index.
	 * @param length a length (the number of elements in the range).
	 * @throws IllegalArgumentException if {@code length} is negative.
	 * @throws ArrayIndexOutOfBoundsException if {@code offset} is negative or
	 *             {@code offset}+{@code length} is greater than the array length.
	 */
	public static void ensureOffsetLength(final long[] a, final int offset, final int length) {
		Arrays.ensureOffsetLength(a.length, offset, length);
	}

	/**
	 * Ensures that two arrays are of the same length.
	 *
	 * @param a an array.
	 * @param b another array.
	 * @throws IllegalArgumentException if the two argument arrays are not of the same length.
	 */
	public static void ensureSameLength(final long[] a, final long[] b) {
		if (a.length != b.length) throw new IllegalArgumentException("Array size mismatch: " + a.length + " != " + b.length);
	}

	/**
	 * Searches a range of the specified array for the specified value using the binary search
	 * algorithm. The range must be sorted prior to making this call. If it is not sorted, the results
	 * are undefined. If the range contains multiple elements with the specified value, there is no
	 * guarantee which one will be found.
	 *
	 * @param a the array to be searched.
	 * @param from the index of the first element (inclusive) to be searched.
	 * @param to the index of the last element (exclusive) to be searched.
	 * @param key the value to be searched for.
	 * @return index of the search key, if it is contained in the array; otherwise,
	 *         {@code (-(<i>insertion point</i>) - 1)}. The <i>insertion point</i> is defined as the the
	 *         point at which the value would be inserted into the array: the index of the first element
	 *         greater than the key, or the length of the array, if all elements in the array are less
	 *         than the specified key. Note that this guarantees that the return value will be &ge; 0 if
	 *         and only if the key is found.
	 * @see java.util.Arrays
	 */

	public static int binarySearch(final long[] a, int from, int to, final long key) {
		long midVal;
		to--;
		while (from <= to) {
			final int mid = (from + to) >>> 1;
			midVal = a[mid];
			if (midVal < key) from = mid + 1;
			else if (midVal > key) to = mid - 1;
			else return mid;
		}
		return -(from + 1);
	}

	/**
	 * Searches an array for the specified value using the binary search algorithm. The range must be
	 * sorted prior to making this call. If it is not sorted, the results are undefined. If the range
	 * contains multiple elements with the specified value, there is no guarantee which one will be
	 * found.
	 *
	 * @param a the array to be searched.
	 * @param key the value to be searched for.
	 * @return index of the search key, if it is contained in the array; otherwise,
	 *         {@code (-(<i>insertion point</i>) - 1)}. The <i>insertion point</i> is defined as the the
	 *         point at which the value would be inserted into the array: the index of the first element
	 *         greater than the key, or the length of the array, if all elements in the array are less
	 *         than the specified key. Note that this guarantees that the return value will be &ge; 0 if
	 *         and only if the key is found.
	 * @see java.util.Arrays
	 */
	public static int binarySearch(final long[] a, final long key) {
		return binarySearch(a, 0, a.length, key);
	}

	/**
	 * Shuffles the specified array fragment using the specified pseudorandom number generator.
	 *
	 * @param a the array to be shuffled.
	 * @param from the index of the first element (inclusive) to be shuffled.
	 * @param to the index of the last element (exclusive) to be shuffled.
	 * @param random a pseudorandom number generator.
	 * @return {@code a}.
	 */
	public static long[] shuffle(final long[] a, final int from, final int to, final Random random) {
		for (int i = to - from; i-- != 0;) {
			final int p = random.nextInt(i + 1);
			final long t = a[from + i];
			a[from + i] = a[from + p];
			a[from + p] = t;
		}
		return a;
	}

	/**
	 * Shuffles the specified array using the specified pseudorandom number generator.
	 *
	 * @param a the array to be shuffled.
	 * @param random a pseudorandom number generator.
	 * @return {@code a}.
	 */
	public static long[] shuffle(final long[] a, final Random random) {
		for (int i = a.length; i-- != 0;) {
			final int p = random.nextInt(i + 1);
			final long t = a[i];
			a[i] = a[p];
			a[p] = t;
		}
		return a;
	}

	/**
	 * Reverses the order of the elements in the specified array.
	 *
	 * @param a the array to be reversed.
	 * @return {@code a}.
	 */
	public static long[] reverse(final long[] a) {
		final int length = a.length;
		for (int i = length / 2; i-- != 0;) {
			final long t = a[length - i - 1];
			a[length - i - 1] = a[i];
			a[i] = t;
		}
		return a;
	}

	/**
	 * Reverses the order of the elements in the specified array fragment.
	 *
	 * @param a the array to be reversed.
	 * @param from the index of the first element (inclusive) to be reversed.
	 * @param to the index of the last element (exclusive) to be reversed.
	 * @return {@code a}.
	 */
	public static long[] reverse(final long[] a, final int from, final int to) {
		final int length = to - from;
		for (int i = length / 2; i-- != 0;) {
			final long t = a[from + length - i - 1];
			a[from + length - i - 1] = a[from + i];
			a[from + i] = t;
		}
		return a;
	}
}

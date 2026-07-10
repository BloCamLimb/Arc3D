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

import java.util.Comparator;
import java.util.Objects;
import java.io.Serializable;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;

/**
 * A type-specific {@link Comparator}; provides methods to compare two primitive types both as
 * objects and as primitive types.
 *
 * <p>
 * Note that {@code fastutil} provides a corresponding abstract class that can be used to implement
 * this interface just by specifying the type-specific comparator.
 *
 * @see Comparator
 */
@FunctionalInterface
public interface IntComparator extends Comparator<Integer> {
	/**
	 * Compares its two primitive-type arguments for order. Returns a negative integer, zero, or a
	 * positive integer as the first argument is less than, equal to, or greater than the second.
	 *
	 * @see java.util.Comparator
	 * @return a negative integer, zero, or a positive integer as the first argument is less than, equal
	 *         to, or greater than the second.
	 */
	int compare(int k1, int k2);

	@Override
	default IntComparator reversed() {
		return IntComparators.oppositeComparator(this);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @implSpec This implementation delegates to the corresponding type-specific method.
	 * @deprecated Please use the corresponding type-specific method instead.
	 */
	@Deprecated
	@Override
	default int compare(Integer ok1, Integer ok2) {
		return compare(ok1.intValue(), ok2.intValue());
	}

	/**
	 * Return a new comparator that first uses this comparator, then uses the second comparator if this
	 * comparator compared the two elements as equal.
	 *
	 * @see Comparator#thenComparing(Comparator)
	 */
	default IntComparator thenComparing(IntComparator second) {
		return (IntComparator & java.io.Serializable)(k1, k2) -> {
			int comp = compare(k1, k2);
			return comp == 0 ? second.compare(k1, k2) : comp;
		};
	}

	@Override
	default Comparator<Integer> thenComparing(Comparator<? super Integer> second) {
		if (second instanceof IntComparator) return thenComparing((IntComparator)second);
		return Comparator.super.thenComparing(second);
	}

	/**
	 * Accepts a function that extracts a {@link java.lang.Comparable Comparable} sort key from a
	 * primitive key, and returns a comparator that compares by that sort key.
	 *
	 * <p>
	 * The returned comparator is serializable if the specified function is also serializable.
	 *
	 * @param keyExtractor the function used to extract the {@link Comparable} sort key
	 * @return a comparator that compares by an extracted key
	 * @throws NullPointerException if {@code keyExtractor} is {@code null}
	 */
	static <U extends Comparable<? super U>> IntComparator comparing(IntFunction<? extends U> keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return (IntComparator & Serializable)(k1, k2) -> keyExtractor.apply(k1).compareTo(keyExtractor.apply(k2));
	}

	/**
	 * Accepts a function that extracts a sort key from a primitive key, and returns a comparator that
	 * compares by that sort key using the specified {@link Comparator}.
	 *
	 * <p>
	 * The returned comparator is serializable if the specified function and comparator are both
	 * serializable.
	 *
	 * @param keyExtractor the function used to extract the sort key
	 * @param keyComparator the {@code Comparator} used to compare the sort key
	 * @return a comparator that compares by an extracted key using the specified {@code Comparator}
	 * @throws NullPointerException if {@code keyExtractor} or {@code keyComparator} are {@code null}
	 */
	static <U extends Comparable<? super U>> IntComparator comparing(IntFunction<? extends U> keyExtractor, Comparator<? super U> keyComparator) {
		Objects.requireNonNull(keyExtractor);
		Objects.requireNonNull(keyComparator);
		return (IntComparator & Serializable)(k1, k2) -> keyComparator.compare(keyExtractor.apply(k1), keyExtractor.apply(k2));
	}

	/**
	 * Accepts a function that extracts an {@code long} sort key from a primitive key, and returns a
	 * comparator that compares by that sort key.
	 *
	 * <p>
	 * The returned comparator is serializable if the specified function is also serializable.
	 *
	 * @param keyExtractor the function used to extract the long sort key
	 * @return a comparator that compares by an extracted key
	 * @throws NullPointerException if {@code keyExtractor} is {@code null}
	 */
	static IntComparator comparingLong(IntToLongFunction keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return (IntComparator & Serializable)(k1, k2) -> Long.compare(keyExtractor.applyAsLong(k1), keyExtractor.applyAsLong(k2));
	}

	/**
	 * Accepts a function that extracts an {@code double} sort key from a primitive key, and returns a
	 * comparator that compares by that sort key.
	 *
	 * <p>
	 * The returned comparator is serializable if the specified function is also serializable.
	 *
	 * @param keyExtractor the function used to extract the double sort key
	 * @return a comparator that compares by an extracted key
	 * @throws NullPointerException if {@code keyExtractor} is {@code null}
	 */
	static IntComparator comparingDouble(IntToDoubleFunction keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return (IntComparator & Serializable)(k1, k2) -> Double.compare(keyExtractor.applyAsDouble(k1), keyExtractor.applyAsDouble(k2));
	}
}

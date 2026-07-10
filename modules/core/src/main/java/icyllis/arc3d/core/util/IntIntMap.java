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

import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.Map;

/**
 * A type-specific {@link Map}; provides some additional methods that use polymorphism to avoid
 * (un)boxing, and handling of a default return value.
 *
 * <p>
 * Besides extending the corresponding type-specific {@linkplain it.unimi.dsi.fastutil.Function
 * function}, this interface strengthens {@link Map#entrySet()}, {@link #keySet()} and
 * {@link #values()}. Moreover, a number of methods, such as {@link #size()},
 * {@link #defaultReturnValue()}, etc., are un-defaulted as their function default do not make sense
 * for a map. Maps returning entry sets of type {@link FastEntrySet} support also fast iteration.
 *
 * <p>
 * A submap or subset may or may not have an independent default return value (which however must be
 * initialized to the default return value of the originator).
 *
 * @see Map
 */
public interface IntIntMap {
	/**
	 * An entry set providing fast iteration.
	 *
	 * <p>
	 * In some cases (e.g., hash-based classes) iteration over an entry set requires the creation of a
	 * large number of {@link java.util.Map.Entry} objects. Some {@code fastutil} maps might return
	 * {@linkplain Map#entrySet() entry set} objects of type {@code FastEntrySet}: in this case,
	 * {@link #fastIterator() fastIterator()} will return an iterator that is guaranteed not to create a
	 * large number of objects, <em>possibly by returning always the same entry</em> (of course,
	 * mutated), and {@link #fastForEach(Consumer)} will apply the provided consumer to all elements of
	 * the entry set, <em>which might be represented always by the same entry</em> (of course, mutated).
	 */
	interface FastEntrySet extends Set<Entry> {
		/**
		 * Returns a fast iterator over this entry set; the iterator might return always the same entry
		 * instance, suitably mutated.
		 *
		 * @return a fast iterator over this entry set; the iterator might return always the same
		 *         {@link java.util.Map.Entry} instance, suitably mutated.
		 */
		Iterator<Entry> fastIterator();

		/**
		 * Iterates quickly over this entry set; the iteration might happen always on the same entry
		 * instance, suitably mutated.
		 *
		 * <p>
		 * This default implementation just delegates to {@link #forEach(Consumer)}.
		 *
		 * @param consumer a consumer that will by applied to the entries of this set; the entries might be
		 *            represented by the same entry instance, suitably mutated.
		 * @since 8.1.0
		 */
		default void fastForEach(final Consumer<? super IntIntMap.Entry> consumer) {
			forEach(consumer);
		}
	}


    /**
     * Adds a pair to the map (optional operation).
     *
     * @param key the key.
     * @param value the value.
     * @return the old value, or the {@linkplain #defaultReturnValue() default return value} if no value
     *         was present for the given key.
     * @see Function#put(Object,Object)
     */
    default int put(final int key, final int value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the value to which the given key is mapped.
     *
     * @param key the key.
     * @return the corresponding value, or the {@linkplain #defaultReturnValue() default return value}
     *         if no value was present for the given key.
     * @see Function#get(Object)
     */
    int get(int key);

    /**
     * Removes the mapping with the given key (optional operation).
     *
     * @param key the key.
     * @return the old value, or the {@linkplain #defaultReturnValue() default return value} if no value
     *         was present for the given key.
     * @see Function#remove(Object)
     */
    default int remove(final int key) {
        throw new UnsupportedOperationException();
    }

	/**
	 * Returns the number of key/value mappings in this map. If the map contains more than
	 * {@link Integer#MAX_VALUE} elements, returns {@link Integer#MAX_VALUE}.
	 *
	 * @return the number of key-value mappings in this map.
	 * @see it.unimi.dsi.fastutil.Size64
	 */
	int size();

    default boolean isEmpty() {
        return size() == 0;
    }

	/**
	 * Removes all of the mappings from this map (optional operation). The map will be empty after this
	 * call returns.
	 *
	 * @throws UnsupportedOperationException if the {@link #clear()} operation is not supported by this
	 *             map
	 */
	default void clear() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Sets the default return value (optional operation).
	 *
	 * This value must be returned by type-specific versions of {@code get()}, {@code put()} and
	 * {@code remove()} to denote that the map does not contain the specified key. It must be
	 * 0/{@code false} by default.
	 *
	 * @param rv the new default return value.
	 * @see #defaultReturnValue()
	 */
	void defaultReturnValue(int rv);

	/**
	 * Gets the default return value.
	 *
	 * @return the current default return value.
	 */
	int defaultReturnValue();

	/**
	 * Returns a type-specific set view of the mappings contained in this map.
	 *
	 * <p>
	 * This method is necessary because there is no inheritance along type parameters: it is thus
	 * impossible to strengthen {@link Map#entrySet()} so that it returns an
	 * {@link it.unimi.dsi.fastutil.objects.ObjectSet} of type-specific entries (the latter makes it
	 * possible to access keys and values with type-specific methods).
	 *
	 * @return a type-specific set view of the mappings contained in this map.
	 * @see Map#entrySet()
	 */
	Set<IntIntMap.Entry> int2IntEntrySet();

	/**
	 * {@inheritDoc}
	 * 
	 * @apiNote Note that this specification strengthens the one given in {@link Map#keySet()}.
	 * @return a set view of the keys contained in this map.
	 * @see Map#keySet()
	 */
	IntSet keySet();

	/**
	 * {@inheritDoc}
	 * 
	 * @apiNote Note that this specification strengthens the one given in {@link Map#values()}.
	 * @return a set view of the values contained in this map.
	 * @see Map#values()
	 */
	IntCollection values();

	/**
	 * Returns true if this function contains a mapping for the specified key.
	 *
	 * @param key the key.
	 * @return true if this function associates a value to {@code key}.
	 * @see Map#containsKey(Object)
	 */
	boolean containsKey(int key);

	/**
	 * Returns {@code true} if this map maps one or more keys to the specified value.
	 * 
	 * @see Map#containsValue(Object)
	 */
	boolean containsValue(int value);

	// Defaultable methods
	/**
	 * Performs the given action for each entry in this map until all entries have been processed or the
	 * action throws an exception.
	 *
	 * <p>
	 * This default implementation just delegates to {@code forEach} on the type-specific entry set,
	 * taking care of using {@code fastForEach} if available.
	 *
	 * @see Map#forEach(java.util.function.BiConsumer)
	 */
	default void forEach(final IntIntBiConsumer consumer) {
		final Set<IntIntMap.Entry> entrySet = int2IntEntrySet();
		final Consumer<IntIntMap.Entry> wrappingConsumer = (entry) -> consumer.accept(entry.getIntKey(), entry.getIntValue());
		if (entrySet instanceof FastEntrySet) {
			((FastEntrySet)entrySet).fastForEach(wrappingConsumer);
		} else {
			entrySet.forEach(wrappingConsumer);
		}
	}

	/**
	 * Returns the value to which the specified key is mapped, or the {@code defaultValue} if this map
	 * contains no mapping for the key.
	 *
	 * @param key the key.
	 * @param defaultValue the default mapping of the key.
	 *
	 * @return the value to which the specified key is mapped, or the {@code defaultValue} if this map
	 *         contains no mapping for the key.
	 *
	 * @see java.util.Map#getOrDefault(Object, Object)
	 * @since 8.0.0
	 */
	default int getOrDefault(final int key, final int defaultValue) {
		final int v;
		return ((v = get(key)) != defaultReturnValue() || containsKey(key)) ? v : defaultValue;
	}

	/**
	 * If the specified key is not already associated with a value, associates it with the given value
	 * and returns the {@linkplain #defaultReturnValue() default return value}, else returns the current
	 * value.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param value value to be associated with the specified key.
	 *
	 * @return the previous value associated with the specified key, or the
	 *         {@linkplain #defaultReturnValue() default return value} if there was no mapping for the
	 *         key.
	 *
	 * @see java.util.Map#putIfAbsent(Object, Object)
	 * @since 8.0.0
	 */
	default int putIfAbsent(final int key, final int value) {
		final int v = get(key), drv = defaultReturnValue();
		if (v != drv || containsKey(key)) return v;
		put(key, value);
		return drv;
	}

    default void putAll(IntIntMap m) {
        Iterator<IntIntMap.Entry> i;
        if (m.int2IntEntrySet() instanceof FastEntrySet fastEntrySet) {
            i = fastEntrySet.fastIterator();
        } else {
            i = m.int2IntEntrySet().iterator();
        }
        while (i.hasNext()) {
            final IntIntMap.Entry e = i.next();
            put(e.getIntKey(), e.getIntValue());
        }
    }

	/**
	 * Removes the entry for the specified key only if it is currently mapped to the specified value.
	 *
	 * @param key key with which the specified value is associated.
	 * @param value value expected to be associated with the specified key.
	 *
	 * @return {@code true} if the value was removed.
	 *
	 * @see java.util.Map#remove(Object, Object)
	 * @since 8.0.0
	 */
	default boolean remove(final int key, final int value) {
		final int curValue = get(key);
		if (!((curValue) == (value)) || (curValue == defaultReturnValue() && !containsKey(key))) return false;
		remove(key);
		return true;
	}

	/**
	 * Replaces the entry for the specified key only if currently mapped to the specified value.
	 *
	 * @param key key with which the specified value is associated.
	 * @param oldValue value expected to be associated with the specified key.
	 * @param newValue value to be associated with the specified key.
	 *
	 * @return {@code true} if the value was replaced.
	 *
	 * @see java.util.Map#replace(Object, Object, Object)
	 * @since 8.0.0
	 */
	default boolean replace(final int key, final int oldValue, final int newValue) {
		final int curValue = get(key);
		if (!((curValue) == (oldValue)) || (curValue == defaultReturnValue() && !containsKey(key))) return false;
		put(key, newValue);
		return true;
	}

	/**
	 * Replaces the entry for the specified key only if it is currently mapped to some value.
	 *
	 * @param key key with which the specified value is associated.
	 * @param value value to be associated with the specified key.
	 *
	 * @return the previous value associated with the specified key, or the
	 *         {@linkplain #defaultReturnValue() default return value} if there was no mapping for the
	 *         key.
	 *
	 * @see java.util.Map#replace(Object, Object)
	 * @since 8.0.0
	 */
	default int replace(final int key, final int value) {
		return containsKey(key) ? put(key, value) : defaultReturnValue();
	}

	/**
	 * If the specified key is not already associated with a value, attempts to compute its value using
	 * the given mapping function and enters it into this map.
	 *
	 * <p>
	 * Note that contrarily to the default
	 * {@linkplain java.util.Map#computeIfAbsent(Object, java.util.function.Function)
	 * computeIfAbsent()}, it is not possible to not add a value for a given key, since the
	 * {@code mappingFunction} cannot return {@code null}. If such a behavior is needed, please use the
	 * corresponding <em>nullable</em> version.
	 *
	 * @apiNote all {@code computeIfAbsent()} methods have a different logic based on the argument; no
	 *          delegation is performed, contrarily to other superficially similar methods such as
	 *          {@link java.util.Iterator#forEachRemaining} or {@link java.util.List#replaceAll}.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param mappingFunction the function to compute a value.
	 *
	 * @return the current (existing or computed) value associated with the specified key.
	 *
	 * @see java.util.Map#computeIfAbsent(Object, java.util.function.Function)
	 * @since 8.0.0
	 */
	default int computeIfAbsent(final int key, final java.util.function.IntUnaryOperator mappingFunction) {
		java.util.Objects.requireNonNull(mappingFunction);
		final int v = get(key);
		if (v != defaultReturnValue() || containsKey(key)) return v;
		int newValue = mappingFunction.applyAsInt(key);
		put(key, newValue);
		return newValue;
	}

	/**
	 * If the specified key is not already associated with a value, attempts to compute its value using
	 * the given mapping function and enters it into this map unless it is {@code null}.
	 *
	 * <p>
	 * Note that this version of
	 * {@linkplain java.util.Map#computeIfAbsent(Object, java.util.function.Function) computeIfAbsent()}
	 * should be used only if you plan to return {@code null} in the mapping function.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param mappingFunction the function to compute a value.
	 *
	 * @return the current (existing or computed) value associated with the specified key, or the
	 *         {@linkplain #defaultReturnValue() default return value} if the computed value is
	 *         {@code null}.
	 *
	 * @see java.util.Map#computeIfAbsent(Object, java.util.function.Function)
	 * @since 8.0.0
	 */
	default int computeIfAbsentNullable(final int key, final java.util.function.IntFunction<? extends Integer> mappingFunction) {
		java.util.Objects.requireNonNull(mappingFunction);
		final int v = get(key), drv = defaultReturnValue();
		if (v != drv || containsKey(key)) return v;
		Integer mappedValue = mappingFunction.apply(key);
		if (mappedValue == null) return drv;
		int newValue = (mappedValue).intValue();
		put(key, newValue);
		return newValue;
	}

	/**
	 * If the specified key is not already associated with a value, attempts to compute its value using
	 * the given mapping function and enters it into this map, unless the key is not present in the
	 * given mapping function.
	 *
	 * <p>
	 * This version of {@linkplain java.util.Map#computeIfAbsent(Object, java.util.function.Function)
	 * computeIfAbsent()} uses a type-specific version of {@code fastutil}'s
	 * {@link it.unimi.dsi.fastutil.Function Function}. Since {@link it.unimi.dsi.fastutil.Function
	 * Function} has a {@link it.unimi.dsi.fastutil.Function#containsKey(Object) containsKey()} method,
	 * it is possible to avoid adding a key by having {@code containsKey()} return {@code false} for
	 * that key.
	 *
	 * @apiNote all {@code computeIfAbsent()} methods have a different logic based on the argument; no
	 *          delegation is performed, contrarily to other superficially similar methods such as
	 *          {@link java.util.Iterator#forEachRemaining} or {@link java.util.List#replaceAll}.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param mappingFunction the function to compute a value.
	 *
	 * @return the current (existing or computed) value associated with the specified key.
	 *
	 * @see java.util.Map#computeIfAbsent(Object, java.util.function.Function)
	 * @since 8.0.0
	 */
	default int computeIfAbsent(final int key, final IntIntMap mappingFunction) {
		java.util.Objects.requireNonNull(mappingFunction);
		final int v = get(key), drv = defaultReturnValue();
		if (v != drv || containsKey(key)) return v;
		if (!mappingFunction.containsKey(key)) return drv;
		int newValue = mappingFunction.get(key);
		put(key, newValue);
		return newValue;
	}

	/**
	 * If the value for the specified key is present, attempts to compute a new mapping given the key
	 * and its current mapped value.
	 *
	 * @apiNote The JDK specification for this method equates not being associated with a value with
	 *          being associated with {code null}. This is not the case for this method.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param remappingFunction the function to compute a value.
	 *
	 * @return the new value associated with the specified key, or the {@linkplain #defaultReturnValue()
	 *         default return value} if none.
	 *
	 * @see java.util.Map#computeIfPresent(Object, java.util.function.BiFunction)
	 * @since 8.0.0
	 */
	default int computeIfPresent(final int key, final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
		java.util.Objects.requireNonNull(remappingFunction);
		final int oldValue = get(key), drv = defaultReturnValue();
		if (oldValue == drv && !containsKey(key)) return drv;
		final Integer newValue = remappingFunction.apply(Integer.valueOf(key), Integer.valueOf(oldValue));
		if (newValue == null) {
			remove(key);
			return drv;
		}
		int newVal = (newValue).intValue();
		put(key, newVal);
		return newVal;
	}

	/**
	 * Attempts to compute a mapping for the specified key and its current mapped value (or {@code null}
	 * if there is no current mapping).
	 *
	 * <p>
	 * If the function returns {@code null}, the mapping is removed (or remains absent if initially
	 * absent). If the function itself throws an (unchecked) exception, the exception is rethrown, and
	 * the current mapping is left unchanged.
	 *
	 * @param key key with which the specified value is to be associated.
	 * @param remappingFunction the function to compute a value.
	 *
	 * @return the new value associated with the specified key, or the {@linkplain #defaultReturnValue()
	 *         default return value} if none.
	 *
	 * @see java.util.Map#compute(Object, java.util.function.BiFunction)
	 * @since 8.0.0
	 */
	default int compute(final int key, final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
		java.util.Objects.requireNonNull(remappingFunction);
		final int oldValue = get(key), drv = defaultReturnValue();
		final boolean contained = oldValue != drv || containsKey(key);
		final Integer newValue = remappingFunction.apply(Integer.valueOf(key), contained ? Integer.valueOf(oldValue) : null);
		if (newValue == null) {
			if (contained) remove(key);
			return drv;
		}
		final int newVal = (newValue).intValue();
		put(key, newVal);
		return newVal;
	}

	/**
	 * If the specified key is not already associated with a value, associates it with the given
	 * {@code value}. Otherwise, replaces the associated value with the results of the given remapping
	 * function, or removes if the result is {@code null}.
	 *
	 * @apiNote The JDK specification for this method equates not being associated with a value with
	 *          being associated with {code null}. This is not the case for this method.
	 *
	 * @param key key with which the resulting value is to be associated.
	 * @param value the value to be merged with the existing value associated with the key or, if no
	 *            existing value is associated with the key, to be associated with the key.
	 * @param remappingFunction the function to recompute a value if present.
	 *
	 * @return the new value associated with the specified key, or the {@linkplain #defaultReturnValue()
	 *         default return value} if no value is associated with the key.
	 *
	 * @see java.util.Map#merge(Object, Object, java.util.function.BiFunction)
	 * @since 8.0.0
	 */
	default int merge(final int key, final int value, final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
		java.util.Objects.requireNonNull(remappingFunction);

		final int oldValue = get(key), drv = defaultReturnValue();
		final int newValue;
		if (oldValue != drv || containsKey(key)) {
			final Integer mergedValue = remappingFunction.apply(Integer.valueOf(oldValue), Integer.valueOf(value));
			if (mergedValue == null) {
				remove(key);
				return drv;
			}
			newValue = (mergedValue).intValue();
		} else {
			newValue = value;
		}
		put(key, newValue);
		return newValue;
	}

	/**
	 * If the specified key is not already associated with a value, associates it with the given
	 * {@code value}. Otherwise, replaces the associated value with the results of the given remapping
	 * function.
	 *
	 * @apiNote The JDK specification for this method equates not being associated with a value with
	 *          being associated with {code null}. This is not the case for this method. Moreover, this
	 *          method cannot delete entries as the type-specific remapping function cannot return
	 *          {@code null}.
	 *
	 * @param key key with which the resulting value is to be associated.
	 * @param value the value to be merged with the existing value associated with the key or, if no
	 *            existing value is associated with the key, to be associated with the key.
	 * @param remappingFunction the function to recompute a value if present.
	 *
	 * @return the new value associated with the specified key.
	 *
	 * @see java.util.Map#merge(Object, Object, java.util.function.BiFunction)
	 * @since 8.5.0
	 */
	default int mergeInt(final int key, final int value, final java.util.function.IntBinaryOperator remappingFunction) {
		java.util.Objects.requireNonNull(remappingFunction);
		final int oldValue = get(key), drv = defaultReturnValue();
		final int newValue = oldValue != drv || containsKey(key) ? remappingFunction.applyAsInt(oldValue, value) : value;
		put(key, newValue);
		return newValue;
	}

	/**
	 * A type-specific {@link java.util.Map.Entry}; provides some additional methods that use
	 * polymorphism to avoid (un)boxing.
	 *
	 * @see java.util.Map.Entry
	 */
	interface Entry {
		/**
		 * Returns the key corresponding to this entry.
		 * 
		 * @see java.util.Map.Entry#getKey()
		 */
		int getIntKey();

		/**
		 * Returns the value corresponding to this entry.
		 * 
		 * @see java.util.Map.Entry#getValue()
		 */
		int getIntValue();

		/**
		 * Replaces the value corresponding to this entry with the specified value (optional operation).
		 * 
		 * @see java.util.Map.Entry#setValue(Object)
		 */
		int setValue(final int value);
	}
}

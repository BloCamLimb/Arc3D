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
package icyllis.arc3d.core.util;

import java.util.Collection;
import java.util.Objects;

/**
 * A type-specific {@link Collection}; provides some additional methods that use polymorphism to
 * avoid (un)boxing.
 *
 * <p>
 * Additionally, this class defines strengthens (again) {@link #iterator()}.
 *
 * <p>
 * This interface specifies reference equality semantics (members will be compared equal with
 * {@code ==} instead of {@link Object#equals(Object) equals}), which may result in breaks in
 * contract if attempted to be used with non reference-equality semantics based {@link Collection}s.
 * For example, a {@code aReferenceCollection.equals(aObjectCollection)} may return different a
 * different result then {@code aObjectCollection.equals(aReferenceCollection)}, in violation of
 * {@link Object#equals equals}'s contract requiring it being symmetric.
 *
 * @see Collection
 */
public interface LongCollection extends Iterable<Long> {
	/**
	 * Returns a type-specific iterator on the elements of this collection.
	 *
	 * @apiNote This specification strengthens the one given in {@link java.lang.Iterable#iterator()},
	 *          which was already strengthened in the corresponding type-specific class, but was
	 *          weakened by the fact that this interface extends {@link Collection}.
	 *
	 * @return a type-specific iterator on the elements of this collection.
	 */
    @Override
	LongIterator iterator();

    /**
     * Performs the given action for each element of this type-specific {@link java.lang.Iterable} until
     * all elements have been processed or the action throws an exception.
     *
     * @param action the action to be performed for each element.
     * @see java.lang.Iterable#forEach(java.util.function.Consumer)
     * @since 8.0.0
     * @apiNote Implementing classes should generally override this method, and take the default
     *          implementation of the other overloads which will delegate to this method (after proper
     *          conversions).
     */
    default void forEach(final java.util.function.LongConsumer action) {
        Objects.requireNonNull(action);
        iterator().forEachRemaining(action);
    }

    /**
     * Returns the number of elements in this collection.  If this collection
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * @return the number of elements in this collection
     */
    int size();

    /**
     * Returns {@code true} if this collection contains no elements.
     *
     * @return {@code true} if this collection contains no elements
     */
    boolean isEmpty();

	/**
	 * Ensures that this collection contains the specified element (optional operation).
	 * 
	 * @see Collection#add(Object)
	 */
	boolean add(long key);

	/**
	 * Returns {@code true} if this collection contains the specified element.
	 * 
	 * @see Collection#contains(Object)
	 */
	boolean contains(long key);

	/**
	 * Removes a single instance of the specified element from this collection, if it is present
	 * (optional operation).
	 *
	 * <p>
	 * Note that this method should be called {@link java.util.Collection#remove(Object) remove()}, but
	 * the clash with the similarly named index-based method in the {@link java.util.List} interface
	 * forces us to use a distinguished name. For simplicity, the set interfaces reinstates
	 * {@code remove()}.
	 *
	 * @see Collection#remove(Object)
	 */
	boolean rem(long key);

	/**
	 * Returns a primitive type array containing the items of this collection.
	 * 
	 * @return a primitive type array containing the items of this collection.
	 * @see Collection#toArray()
	 */
	long[] toLongArray();

	/**
	 * Returns an array containing all of the elements in this collection; the runtime type of the
	 * returned array is that of the specified array.
	 *
	 * <p>
	 * Note that, contrarily to {@link Collection#toArray(Object[])}, this methods just writes all
	 * elements of this collection: no special value will be added after the last one.
	 *
	 * @param a if this array is big enough, it will be used to store this collection.
	 * @return a primitive type array containing the items of this collection.
	 * @see Collection#toArray(Object[])
	 */
	long[] toArray(long a[]);

	/**
	 * Adds all elements of the given type-specific collection to this collection.
	 *
	 * @param c a type-specific collection.
	 * @see Collection#addAll(Collection)
	 * @return {@code true} if this collection changed as a result of the call.
	 */
	boolean addAll(LongCollection c);

	/**
	 * Checks whether this collection contains all elements from the given type-specific collection.
	 *
	 * @param c a type-specific collection.
	 * @see Collection#containsAll(Collection)
	 * @return {@code true} if this collection contains all elements of the argument.
	 */
	boolean containsAll(LongCollection c);

	/**
	 * Remove from this collection all elements in the given type-specific collection.
	 *
	 * @param c a type-specific collection.
	 * @see Collection#removeAll(Collection)
	 * @return {@code true} if this collection changed as a result of the call.
	 */
	boolean removeAll(LongCollection c);

	/**
	 * Remove from this collection all elements which satisfy the given predicate.
	 *
	 * @param filter a predicate which returns {@code true} for elements to be removed.
	 * @see Collection#removeIf(java.util.function.Predicate)
	 * @return {@code true} if any elements were removed.
	 * @apiNote Implementing classes should generally override this method, and take the default
	 *          implementation of the other overloads which will delegate to this method (after proper
	 *          conversions).
	 */
	default boolean removeIf(final java.util.function.LongPredicate filter) {
		java.util.Objects.requireNonNull(filter);
		boolean removed = false;
		final LongIterator each = iterator();
		while (each.hasNext()) {
			if (filter.test(each.nextLong())) {
				each.remove();
				removed = true;
			}
		}
		return removed;
	}

	/**
	 * Retains in this collection only elements from the given type-specific collection.
	 *
	 * @param c a type-specific collection.
	 * @see Collection#retainAll(Collection)
	 * @return {@code true} if this collection changed as a result of the call.
	 */
	boolean retainAll(LongCollection c);

    /**
     * Removes all of the elements from this collection (optional operation).
     * The collection will be empty after this method returns.
     *
     * @throws UnsupportedOperationException if the {@code clear} operation
     *         is not supported by this collection
     */
    void clear();
}

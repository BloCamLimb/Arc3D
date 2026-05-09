/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.engine;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;

/**
 * Used to build a packed array as the storage or lookup key of a hash map.
 */
public non-sealed class KeyBuilder extends Key {

    private int size;

    public KeyBuilder() {
    }

    public KeyBuilder(int capacity) {
        if (capacity == 0) data = IntArrays.EMPTY_ARRAY;
        else data = new int[capacity];
        assert hash == 1;
    }

    @SuppressWarnings("IncompleteCopyConstructor")
    public KeyBuilder(@NonNull KeyBuilder other) {
        int size = other.size;
        data = size == 0 ? IntArrays.EMPTY_ARRAY : Arrays.copyOf(other.data, size);
        this.size = size;
        hash = other.hash;
    }

    /**
     * Resets this key builder to initial state.
     */
    public final void clear() {
        size = 0;
        hash = 1;
    }

    /**
     * @return the number of ints
     */
    public final int size() {
        return size;
    }

    /**
     * @return true if this key builder contains no bits
     */
    public final boolean isEmpty() {
        return size == 0;
    }

    private void grow(int capacity) {
        if (capacity > data.length) {
            if (data != IntArrays.DEFAULT_EMPTY_ARRAY) {
                capacity = (int)Math.max(Math.min((long) data.length + (long)(data.length >> 1), Integer.MAX_VALUE - 8), capacity);
            } else if (capacity < 10) {
                capacity = 10;
            }

            data = IntArrays.forceCapacity(data, capacity, size);

        }
    }

    /**
     * Appends a full word.
     */
    public final void add(int v) {
        grow(size + 1);
        data[size++] = v;
        hash = hash * 31 + v;
    }

    /**
     * Appends an array of words.
     */
    public final void add(int[] v, int off, int len) {
        grow(size + len);
        System.arraycopy(v, off, data, size, len);
        size += len;
        for (int i = off; i < off + len; i++)
            hash = 31 * hash + v[i];
    }

    /**
     * Trims the backing store so that the capacity is equal to the size.
     */
    public final void trim() {
        if (0 < data.length && size != data.length) {
            int[] t = new int[size];
            System.arraycopy(data, 0, t, 0, size);
            data = t;
        }
    }

    /**
     * @return a copy of packed int array as storage key
     */
    public final Key toStorageKey() {
        if (size == 0) {
            return Key.EMPTY;
        } else {
            int[] t = new int[size];
            System.arraycopy(data, 0, t, 0, size);
            return new Key(t, hash);
        }
    }

    /**
     * Compares with packed int array (storage key).
     */
    @Override
    public final boolean equals(Object o) {
        return o instanceof Key key && // check for null
                Arrays.equals(data, 0, size, key.data, 0, key.size());
    }
}

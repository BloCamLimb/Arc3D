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
 * A key loaded with custom data (int array).
 * <p>
 * Accepts <code>Key</code> as storage key or <code>KeyBuilder</code> as lookup key.
 */
public sealed class Key permits KeyBuilder {

    public static final Key EMPTY = new Key(IntArrays.EMPTY_ARRAY, 1);

    transient int[] data;
    transient int hash;

    // Used by subclass
    Key() {
        data = IntArrays.DEFAULT_EMPTY_ARRAY;
        hash = 1;
    }

    Key(int[] storage, int hash) {
        data = storage;
        this.hash = hash;
    }

    public int size() {
        return data.length;
    }

    public boolean isEmpty() {
        assert (data.length == 0) == (this == EMPTY);
        return data.length == 0;
    }

    public final int get(int i) {
        assert i < size();
        return data[i];
    }

    public final void get(int i, int@NonNull [] out, int off, int len) {
        assert i + len <= size();
        System.arraycopy(data, i, out, off, len);
    }

    @Override
    public final int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Key key && Arrays.equals(data, key.data);
    }
}

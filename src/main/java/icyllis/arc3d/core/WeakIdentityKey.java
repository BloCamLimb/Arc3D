/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.core;

import org.jspecify.annotations.NonNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Identity weak reference, WeakReference's own identity can also be used as a unique key.
 */
public final class WeakIdentityKey<T> extends WeakReference<T> {

    private final int hash;

    public WeakIdentityKey(T referent) {
        super(referent);
        hash = System.identityHashCode(referent);
    }

    public WeakIdentityKey(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        hash = System.identityHashCode(referent);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public boolean equals(Object o) {
        // use WeakReference's identity
        if (this == o) {
            return true;
        }
        // use referent's identity
        if (o instanceof Reference r) {
            Object got = get();
            return got != null && r.refersTo(got);
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "WeakIdentityKey@" + Integer.toHexString(hash);
    }
}

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

import java.util.Set;

/**
 * An abstract class providing basic methods for sets implementing a type-specific interface.
 *
 * <p>
 * Note that the type-specific {@link Set} interface adds a type-specific {@code remove()} method,
 * as it is no longer harmful for subclasses. Thus, concrete subclasses of this class must implement
 * {@code remove()} (the {@code rem()} implementation of this class just delegates to
 * {@code remove()}).
 */
public abstract class AbstractIntSet extends AbstractIntCollection implements Cloneable, IntSet {
	protected AbstractIntSet() {
	}

	@Override
	public abstract IntIterator iterator();

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof IntCollection)) return false;
        IntCollection s = (IntCollection)o;
		if (s.size() != size()) return false;
		if (s instanceof IntSet) {
			return containsAll(s);
		}
		return containsAll(s);
	}

	/**
	 * Returns a hash code for this set.
	 *
	 * The hash code of a set is computed by summing the hash codes of its elements.
	 *
	 * @return a hash code for this set.
	 */
	@Override
	public int hashCode() {
		int h = 0, n = size();
		IntIterator i = iterator();
		int k;
		while (n-- != 0) {
			k = i.nextInt(); // We need k because KEY2JAVAHASH() is a macro with repeated evaluation.
			h += (k);
		}
		return h;
	}

	/**
	 * {@inheritDoc} Delegates to the type-specific {@code rem()} method implemented by type-specific
	 * abstract {@link java.util.Collection} superclass.
	 */
	@Override
	public boolean remove(int k) {
		return super.rem(k);
	}

	/**
	 * {@inheritDoc} Delegates to the type-specific {@code remove()} method specified in the
	 * type-specific {@link Set} interface.
	 * 
	 * @deprecated Please use {@code remove()} instead.
	 */
	@Deprecated
	@Override
	public boolean rem(int k) {
		return remove(k);
	}
}

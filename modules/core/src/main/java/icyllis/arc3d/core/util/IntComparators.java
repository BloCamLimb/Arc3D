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
 * Copyright (C) 2003-2024 Sebastiano Vigna
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

/**
 * A class providing static methods and objects that do useful things with comparators.
 */
public final class IntComparators {
	private IntComparators() {
	}

	/** A type-specific comparator mimicking the natural order. */
	protected static class NaturalImplicitComparator implements IntComparator, java.io.Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public final int compare(final int a, final int b) {
			return (Integer.compare((a), (b)));
		}

		@Override
		public IntComparator reversed() {
			return OPPOSITE_COMPARATOR;
		}

		private Object readResolve() {
			return NATURAL_COMPARATOR;
		}
	};

	public static final IntComparator NATURAL_COMPARATOR = new NaturalImplicitComparator();

	/** A type-specific comparator mimicking the opposite of the natural order. */
	protected static class OppositeImplicitComparator implements IntComparator, java.io.Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public final int compare(final int a, final int b) {
			return -(Integer.compare((a), (b)));
		}

		@Override
		public IntComparator reversed() {
			return NATURAL_COMPARATOR;
		}

		private Object readResolve() {
			return OPPOSITE_COMPARATOR;
		}
	};

	public static final IntComparator OPPOSITE_COMPARATOR = new OppositeImplicitComparator();

	protected static class OppositeComparator implements IntComparator, java.io.Serializable {
		private static final long serialVersionUID = 1L;
		final IntComparator comparator;

		protected OppositeComparator(final IntComparator c) {
			comparator = c;
		}

		@Override
		public final int compare(final int a, final int b) {
			return comparator.compare(b, a);
		}

		@Override
		public final IntComparator reversed() {
			return comparator;
		}
	};

	/**
	 * Returns a comparator representing the opposite order of the given comparator.
	 *
	 * @param c a comparator.
	 * @return a comparator representing the opposite order of {@code c}.
	 */
	public static IntComparator oppositeComparator(final IntComparator c) {
		if (c instanceof OppositeComparator) return ((OppositeComparator)c).comparator;
		return new OppositeComparator(c);
	}
}

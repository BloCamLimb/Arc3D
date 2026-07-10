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
 * Copyright (C) 2017-2024 Sebastiano Vigna
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

import java.util.function.BiConsumer;

/**
 * A type-specific {@link BiConsumer}.
 *
 * @see BiConsumer
 * @since 8.5.18
 */
@FunctionalInterface
public interface LongObjectBiConsumer<V> {
	/**
	 * Performs this operation on the given entry.
	 *
	 * @param k the key.
	 * @param v the value.
	 */
	void accept(long k, V v);

	default LongObjectBiConsumer<V> andThen(final LongObjectBiConsumer<V> after) {
		java.util.Objects.requireNonNull(after);
		return (k, v) -> {
			accept(k, v);
			after.accept(k, v);
		};
	}
}

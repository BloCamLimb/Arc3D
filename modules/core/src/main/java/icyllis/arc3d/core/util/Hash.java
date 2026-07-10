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

/** Basic data for all hash-based classes. */

public interface Hash {

	/** The initial default size of a hash table. */
	int DEFAULT_INITIAL_SIZE = 16;
	/** The default load factor of a hash table. */
	float DEFAULT_LOAD_FACTOR = .75f;
	/** The load factor for a (usually small) table that is meant to be particularly fast. */
	float FAST_LOAD_FACTOR = .5f;
	/** The load factor for a (usually very small) table that is meant to be extremely fast. */
	float VERY_FAST_LOAD_FACTOR = .25f;
}

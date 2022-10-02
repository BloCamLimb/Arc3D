/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.engine;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Abstract interface that supports creating vertices, indices, and meshes, as well as
 * invoking GPU draw operations.
 */
//TODO
public interface MeshDrawTarget {

    /**
     * Makes space for vertex data. The returned pointer is the location where vertex data
     * should be written. On return the buffer that will hold the data as well as an offset into
     * the buffer (in 'vertexSize' units) where the data will be placed.
     * <p>
     * This method requires {@link Mesh#getVertexSize()} and {@link Mesh#getVertexCount()} as
     * arguments and {@link Mesh#setVertexBuffer(Buffer, int)} as results.
     *
     * @return may NULL if failed
     */
    long makeVertexSpace(Mesh mesh);

    /**
     * Makes space for instance data. The returned pointer is the location where instance data
     * should be written. On return the buffer that will hold the data as well as an offset into
     * the buffer (in 'instanceSize' units) where the data will be placed.
     * <p>
     * This method requires {@link Mesh#getInstanceSize()} and {@link Mesh#getInstanceCount()} as
     * arguments and {@link Mesh#setInstanceBuffer(Buffer, int)} as results.
     *
     * @return may NULL if failed
     */
    long makeInstanceSpace(Mesh mesh);

    /**
     * Helper method.
     *
     * @return may null if failed
     * @see #makeVertexSpace(Mesh)
     */
    @Nullable
    ByteBuffer makeVertexWriter(Mesh mesh);

    /**
     * Helper method.
     *
     * @return may null if failed
     * @see #makeInstanceSpace(Mesh)
     */
    @Nullable
    ByteBuffer makeInstanceWriter(Mesh mesh);
}

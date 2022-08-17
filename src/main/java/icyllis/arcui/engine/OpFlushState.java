/*
 * Arc UI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arc UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcui.engine;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Tracks the state across all the GrOps (really just the GrDrawOps) in a OpsTask flush.
 */
public class OpFlushState implements MeshDrawTarget {

    private final VertexBufferAllocPool mVertexPool;
    private final InstanceBufferAllocPool mInstancePool;

    public OpFlushState(Server server,
                        ResourceProvider resourceProvider,
                        BufferAllocPool.CpuBufferCache cpuBufferCache) {
        mVertexPool = new VertexBufferAllocPool(server, cpuBufferCache);
        mInstancePool = new InstanceBufferAllocPool(server, cpuBufferCache);
    }

    @Override
    public long makeVertexSpace(Mesh mesh) {
        return mVertexPool.makeSpace(mesh);
    }

    @Override
    public long makeInstanceSpace(Mesh mesh) {
        return mInstancePool.makeSpace(mesh);
    }

    @Nullable
    @Override
    public ByteBuffer makeVertexWriter(Mesh mesh) {
        return mVertexPool.makeWriter(mesh);
    }

    @Nullable
    @Override
    public ByteBuffer makeInstanceWriter(Mesh mesh) {
        return mInstancePool.makeWriter(mesh);
    }
}

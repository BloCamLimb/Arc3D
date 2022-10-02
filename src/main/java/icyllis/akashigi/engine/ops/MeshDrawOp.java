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

package icyllis.akashigi.engine.ops;

import icyllis.akashigi.engine.*;

/**
 * Base class for mesh-drawing {@link DrawOp DrawOps}.
 */
public abstract class MeshDrawOp extends DrawOp implements Mesh {

    private PipelineInfo mPipelineInfo;

    public MeshDrawOp() {
    }

    public PipelineInfo getPipelineInfo() {
        return mPipelineInfo;
    }

    @Override
    public void onPrePrepare(RecordingContext context,
                             SurfaceProxyView writeView,
                             int pipelineFlags) {
        assert (mPipelineInfo == null);
        mPipelineInfo = onCreatePipelineInfo(writeView, pipelineFlags);
    }

    @Override
    public final void onPrepare(OpFlushState state, SurfaceProxyView writeView, int pipelineFlags) {
        if (mPipelineInfo == null) {
            mPipelineInfo = onCreatePipelineInfo(writeView, pipelineFlags);
        }
        onPrepareDraws(state);
    }

    protected abstract PipelineInfo onCreatePipelineInfo(SurfaceProxyView writeView,
                                                         int pipelineFlags);

    protected abstract void onPrepareDraws(MeshDrawTarget target);
}

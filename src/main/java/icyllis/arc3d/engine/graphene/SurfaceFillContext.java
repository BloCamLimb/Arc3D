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

package icyllis.arc3d.engine.graphene;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.RecordingContext;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.ops.OpsTask;

public class SurfaceFillContext extends SurfaceContext {

    @SharedPtr
    private OpsTask mOpsTask;

    private final ImageProxyView mWriteView;

    public SurfaceFillContext(RecordingContext context,
                              ImageProxyView readView,
                              ImageProxyView writeView,
                              int colorType,
                              int alphaType,
                              ColorSpace colorSpace) {
        super(context, readView, colorType, alphaType, colorSpace);
        mWriteView = writeView;
    }

    public OpsTask getOpsTask() {
        assert mContext.isOwnerThread();
        if (mOpsTask == null || mOpsTask.isClosed()) {
            return nextOpsTask();
        }
        return mOpsTask;
    }

    public OpsTask nextOpsTask() {
        OpsTask newOpsTask = getDrawingManager().newOpsTask(mWriteView);
        mOpsTask = RefCnt.move(mOpsTask, newOpsTask);
        return mOpsTask;
    }
}
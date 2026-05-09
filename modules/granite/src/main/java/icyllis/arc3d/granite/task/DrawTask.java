/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite.task;

import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.CommandBuffer;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.granite.RecordingContext;

public final class DrawTask extends Task {

    @SharedPtr
    private ImageProxy mTarget;
    private TaskList mChildTasks;

    public DrawTask(@SharedPtr ImageProxy target, TaskList childTasks) {
        mTarget = target;
        mChildTasks = childTasks;
    }

    @Override
    protected void deallocate() {
        super.deallocate();
        mTarget = RefCnt.move(mTarget);
        mChildTasks.close();
        mChildTasks = null;
    }

    @Override
    public int prepare(RecordingContext context) {
        return mChildTasks.prepare(context);
    }

    @Override
    public int execute(ImmediateContext context, CommandBuffer commandBuffer) {
        assert mTarget.isInstantiated();
        return mChildTasks.execute(context, commandBuffer);
    }
}

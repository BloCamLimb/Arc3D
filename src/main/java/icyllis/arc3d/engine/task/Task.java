/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.engine.task;

import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.engine.*;

public abstract class Task extends RefCnt {

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_FAILURE = 1;
    public static final int RESULT_DISCARD = 2;

    public abstract int prepare(ResourceProvider resourceProvider);

    public abstract int execute(ImmediateContext context, CommandBuffer cmdBuffer);

    @Override
    protected void deallocate() {
    }
}
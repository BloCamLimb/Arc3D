/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite;

import icyllis.arc3d.engine.ImmediateContext;
import org.jspecify.annotations.NonNull;

public class GraniteUtil {

    public static final String SHADER_CODE_SOURCE = "granite:shader_code_source";
    public static final String RENDERER_PROVIDER = "granite:renderer_provider";

    public static boolean init(@NonNull ImmediateContext context) {
        if (context.getSharedObject(RENDERER_PROVIDER) != null) {
            return true;
        }
        var staticBufferManager = new StaticBufferManager(context.getResourceProvider(), context.getCaps());
        var rendererProvider = new RendererProvider(context.getCaps(), staticBufferManager);

        var result = staticBufferManager.flush(context.getQueueManager(), context.getDeviceBoundCache());
        if (result == StaticBufferManager.RESULT_FAILURE) {
            return false;
        }
        if (result == StaticBufferManager.RESULT_SUCCESS &&
                !context.getQueueManager().submit()) {
            return false;
        }
        context.setSharedObject(SHADER_CODE_SOURCE, new ShaderCodeSource());
        context.setSharedObject(RENDERER_PROVIDER, rendererProvider);

        return true;
    }
}

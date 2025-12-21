/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.Caps;
import icyllis.arc3d.engine.KeyBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class KeyContext {

    @RawPtr
    public final Caps caps;
    /**
     * Raw ptr to context, null when pre-compiling shaders.
     */
    @RawPtr
    public final RecordingContext context;
    @RawPtr
    public final SurfaceDrawContext drawContext;

    public final KeyBuilder paintParamsKeyBuilder;
    @RawPtr
    public final UniformDataGatherer uniformDataGatherer;
    @RawPtr
    public final TextureDataGatherer textureDataGatherer;

    public final ImageInfo dstInfo;
    /**
     * Color components using non-premultiplied alpha, transformed into destination space
     * <p>
     * Since RGB and alpha are never used together, it is assumed to be opaque,
     * no need to premultiply here.
     */
    public final float[] paintColor = new float[4];

    public KeyContext(@NonNull  @RawPtr RecordingContext context,
                      @NonNull @RawPtr SurfaceDrawContext drawContext,
                      @NonNull  KeyBuilder paintParamsKeyBuilder,
                      @NonNull @RawPtr UniformDataGatherer uniformDataGatherer,
                      @NonNull @RawPtr TextureDataGatherer textureDataGatherer,
                      @NonNull  ImageInfo dstInfo) {
        this.drawContext = drawContext;
        this.context = context;
        caps = context.getCaps();
        this.paintParamsKeyBuilder = paintParamsKeyBuilder;
        this.uniformDataGatherer = uniformDataGatherer;
        this.textureDataGatherer = textureDataGatherer;
        this.dstInfo = dstInfo;
    }

    public KeyContext reset(float @Nullable [] paintColor) {
        paintParamsKeyBuilder.clear();
        uniformDataGatherer.reset();
        textureDataGatherer.resetForDraw();
        if (paintColor != null) {
            System.arraycopy(paintColor, 0, this.paintColor, 0, 4);
            PaintParams.prepareColorForDst(this.paintColor, dstInfo);
        }
        return this;
    }

    public void addBlock(int stageID) {
        paintParamsKeyBuilder.add(stageID);
    }
}

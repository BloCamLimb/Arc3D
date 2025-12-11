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

import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.Caps;
import org.jspecify.annotations.Nullable;

public class KeyContext {

    @RawPtr
    private final RecordingContext mRecordingContext;
    private final Caps mCaps;
    private final ImageInfo mTargetInfo;
    // color components using non-premultiplied alpha, transformed into destination space
    private final float[] mColor = new float[4];

    public KeyContext(@RawPtr RecordingContext recordingContext,
                      ImageInfo targetInfo) {
        mRecordingContext = recordingContext;
        mCaps = recordingContext.getCaps();
        mTargetInfo = targetInfo;
    }

    public void reset(float @Nullable [] paintColor) {
        if (paintColor != null) {
            System.arraycopy(paintColor, 0, mColor, 0, 4);
            PaintParams.prepareColorForDst(mColor, mTargetInfo);
        }
    }

    /**
     * Raw ptr to context, null when pre-compiling shaders.
     */
    @RawPtr
    @Nullable
    public RecordingContext getRecordingContext() {
        return mRecordingContext;
    }

    public Caps getCaps() {
        return mCaps;
    }

    /**
     * Returns the current paint color, in destination space, non-premultiplied.
     * This is not a copy, don't modify.
     * <p>
     * Since RGB and alpha are never used together, it is assumed to be opaque,
     * no need to premultiply.
     */
    public float[] getPaintColor() {
        return mColor;
    }

    public ImageInfo targetInfo() {
        return mTargetInfo;
    }
}

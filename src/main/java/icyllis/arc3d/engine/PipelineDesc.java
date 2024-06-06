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

package icyllis.arc3d.engine;

/**
 * Two usages.
 */
//TODO
public abstract class PipelineDesc {

    public static class GraphicsPipelineInfo {

        public byte mPrimitiveType;
        public String mPipelineLabel;
        public VertexInputLayout mInputLayout;
        public StringBuilder mVertSource;
        public String mVertLabel;
        public StringBuilder mFragSource;
        public String mFragLabel;
    }

    public byte getPrimitiveType() {
        return 0;
    }

    /**
     * Generates all info used to create graphics pipeline.
     */
    public GraphicsPipelineInfo createGraphicsPipelineInfo(Caps caps) {
        return null;
    }

    // deep copy
    public abstract PipelineDesc copy();
}
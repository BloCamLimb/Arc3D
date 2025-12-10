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

package icyllis.arc3d.granite;

import icyllis.arc3d.engine.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Objects;

/**
 * Descriptor of a graphics pipeline in Granite Renderer.
 */
public final class GraphicsPipelineDesc extends PipelineDesc {

    private GeometryStep mGeometryStep;
    private Key mPaintParamsKey;
    private boolean mUseStepSolidColor;

    public GraphicsPipelineDesc() {
    }

    public GraphicsPipelineDesc(GeometryStep geometryStep,
                                Key paintParamsKey,
                                boolean useStepSolidColor) {
        mGeometryStep = geometryStep;
        mPaintParamsKey = paintParamsKey;
        mUseStepSolidColor = useStepSolidColor;
    }

    public GraphicsPipelineDesc set(GeometryStep geometryStep,
                                    Key paintParamsKey,
                                    boolean useStepSolidColor) {
        mGeometryStep = geometryStep;
        mPaintParamsKey = paintParamsKey;
        mUseStepSolidColor = useStepSolidColor;
        return this;
    }

    public GeometryStep geomStep() {
        return mGeometryStep;
    }

    public Key paintParamsKey() {
        return mPaintParamsKey;
    }

    public boolean useStepSolidColor() {
        return mUseStepSolidColor;
    }

    public boolean mayRequireLocalCoords() {
        return !mUseStepSolidColor && !mPaintParamsKey.isEmpty() &&
                        mPaintParamsKey.get(0) != FragmentStage.kSolidColorShader_BuiltinStageID;
    }

    private FragmentNode createNode(ShaderCodeSource codeSource,
                                    StringBuilder label,
                                    int[] currentStageIndex) {
        assert currentStageIndex[0] < mPaintParamsKey.size();
        int index = currentStageIndex[0]++;
        int id = mPaintParamsKey.get(index);

        FragmentStage stage = codeSource.findStage(id);
        if (stage == null) {
            return null;
        }

        String name = stage.name();
        if (name.endsWith("Shader")) {
            label.append(name, 0, name.length() - 6);
        } else {
            label.append(name);
        }

        FragmentNode[] children;
        if (stage.mNumChildren > 0) {
            children = new FragmentNode[stage.mNumChildren];
            label.append(" [ ");
            for (int i = 0; i < stage.mNumChildren; i++) {
                FragmentNode child = createNode(codeSource, label, currentStageIndex);
                if (child == null) {
                    return null;
                }
                children[i] = child;
            }
            label.append("]");
        } else {
            children = FragmentNode.NO_CHILDREN;
        }
        label.append(" ");

        return new FragmentNode(
                stage,
                children,
                id,
                index
        );
    }

    public FragmentNode[] getRootNodes(ShaderCodeSource codeSource,
                                       StringBuilder label) {
        final int keySize = mPaintParamsKey.size();

        // There can be at most 3 root nodes.
        var roots = new ObjectArrayList<FragmentNode>(5);
        int[] currentIndex = {0};
        while (currentIndex[0] < keySize) {
            FragmentNode root = createNode(codeSource, label, currentIndex);
            if (root == null) {
                label.setLength(0);
                return FragmentNode.NO_CHILDREN;
            }
            roots.add(root);
        }

        return roots.toArray(FragmentNode.NO_CHILDREN);
    }

    public boolean isDepthOnlyPass() {
        boolean depthOnly = mPaintParamsKey.isEmpty();
        assert !depthOnly || !mUseStepSolidColor;
        return depthOnly;
    }

    @Override
    public GraphicsPipelineInfo createGraphicsPipelineInfo(Device device) {
        PipelineBuilder pipelineBuilder = new PipelineBuilder(device, this);
        return pipelineBuilder.build();
    }

    @Override
    public GraphicsPipelineDesc copy() {
        if (mPaintParamsKey instanceof KeyBuilder keyBuilder) {
            // deep copy
            return new GraphicsPipelineDesc(mGeometryStep, keyBuilder.toStorageKey(),
                    mUseStepSolidColor);
        }
        // shallow copy
        return new GraphicsPipelineDesc(mGeometryStep, mPaintParamsKey,
                mUseStepSolidColor);
    }

    @Override
    public int hashCode() {
        int result = mGeometryStep.uniqueID();
        result = 31 * result + mPaintParamsKey.hashCode();
        result = 31 * result + Boolean.hashCode(mUseStepSolidColor);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        // We will never compare ourselves or KeyBuilder
        if (o instanceof GraphicsPipelineDesc desc) {
            return mGeometryStep.uniqueID() == desc.mGeometryStep.uniqueID() &&
                    mUseStepSolidColor == desc.mUseStepSolidColor &&
                    mPaintParamsKey.equals(desc.mPaintParamsKey);
        }
        return false;
    }
}

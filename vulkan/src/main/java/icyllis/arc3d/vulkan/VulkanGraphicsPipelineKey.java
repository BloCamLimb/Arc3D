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

package icyllis.arc3d.vulkan;

import icyllis.arc3d.engine.PipelineKey;

public class VulkanGraphicsPipelineKey extends PipelineKey {

    public final VulkanRenderPassSet.CompatibleKey mCompatibleRenderPassKey;

    public VulkanGraphicsPipelineKey() {
        mCompatibleRenderPassKey = new VulkanRenderPassSet.CompatibleKey();
    }

    @SuppressWarnings("IncompleteCopyConstructor")
    public VulkanGraphicsPipelineKey(VulkanGraphicsPipelineKey other) {
        super(other);
        mCompatibleRenderPassKey = new VulkanRenderPassSet.CompatibleKey(other.mCompatibleRenderPassKey);
    }

    @Override
    public VulkanGraphicsPipelineKey copy() {
        return new VulkanGraphicsPipelineKey(this);
    }

    @Override
    public int hashCode() {
        int result = mPipelineDesc.hashCode();
        result = result * 31 + mCompatibleRenderPassKey.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VulkanGraphicsPipelineKey key) {
            return mPipelineDesc.equals(key.mPipelineDesc) &&
                    mCompatibleRenderPassKey.equals(key.mCompatibleRenderPassKey);
        }
        return false;
    }
}

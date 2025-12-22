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

import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ManagedResource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDescriptorSetLayout extends ManagedResource {

    private final DescriptorSetLayout mInfo;
    private final long mSetLayout;

    private VulkanDescriptorSetLayout(VulkanDevice device,
                                     DescriptorSetLayout info,
                                     long setLayout) {
        super(device);
        mInfo = info;
        mSetLayout = setLayout;

        assert setLayout != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    public static VulkanDescriptorSetLayout make(@NonNull VulkanDevice device,
                                                 @NonNull DescriptorSetLayout info) {
        try (var stack = MemoryStack.stackPush()) {
            var pBindings = info.toVkBindings(stack);
            var pCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(pBindings);
            var pSetLayout = stack.mallocLong(1);

            var result = vkCreateDescriptorSetLayout(device.vkDevice(),
                    pCreateInfo, null, pSetLayout);
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanDescriptorSetLayout: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanDescriptorSetLayout(device, info,pSetLayout.get(0));
        }
    }

    @NonNull
    public DescriptorSetLayout getInfo() {
        return mInfo;
    }

    public long vkSetLayout() {
        return mSetLayout;
    }

    @Override
    protected void deallocate() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyDescriptorSetLayout(device.vkDevice(),
                mSetLayout, null);

    }
}

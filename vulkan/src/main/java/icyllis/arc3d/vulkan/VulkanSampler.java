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
import icyllis.arc3d.engine.Device;
import icyllis.arc3d.engine.Sampler;
import icyllis.arc3d.engine.SamplerDesc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanSampler extends Sampler {

    private final long mSampler;

    private VulkanSampler(Device device, SamplerDesc desc, long sampler) {
        super(device, desc);
        mSampler = sampler;
        assert sampler != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    public static VulkanSampler make(@NonNull VulkanDevice device,
                                     @NonNull SamplerDesc desc) {
        try (var stack = MemoryStack.stackPush()) {

            VkSamplerCreateInfo pCreateInfo = VkSamplerCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .flags(0);

            // If MipmapMode is None, VK_SAMPLER_MIPMAP_MODE_NEAREST (rounding) will be used
            pCreateInfo
                    .magFilter(VKUtil.toVkFilter(desc.getMagFilter()))
                    .minFilter(VKUtil.toVkFilter(desc.getMinFilter()))
                    .mipmapMode(VKUtil.toVkMipmapMode(desc.getMipmapMode()))
                    .addressModeU(VKUtil.toVkAddressMode(desc.getAddressModeX()))
                    .addressModeV(VKUtil.toVkAddressMode(desc.getAddressModeY()))
                    .addressModeW(VKUtil.toVkAddressMode(desc.getAddressModeZ()))
                    .mipLodBias(0.0f);

            //TODO clamp to VulkanCaps VkPhysicalDeviceLimits::maxSamplerAnisotropy
            pCreateInfo
                    .anisotropyEnable(desc.isAnisotropy())
                    .maxAnisotropy(desc.getMaxAnisotropy());

            pCreateInfo
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER);

            // There are no Vulkan filter modes that directly correspond to nearest or linear milFilter since
            // there is always a mipmapMode. If maxLod is 0, the sampler will always use the magFilter,
            // causing the minFilter to be ignored; if it is 0.5, it can be rounded to mip level 1.
            // Set minLod = 0 and maxLod = 0.25 (between 0 and 0.5) to force the minFilter on mip level 0.
            pCreateInfo
                    .minLod(0.0f)
                    .maxLod(desc.getMipmapMode() != SamplerDesc.MIPMAP_MODE_NONE
                            ? VK_LOD_CLAMP_NONE
                            : 0.25f);

            // border color defaults to (0,0,0,0)
            pCreateInfo
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false); // non-integer coordinates

            var pSampler = stack.mallocLong(1);
            var result = vkCreateSampler(
                    device.vkDevice(),
                    pCreateInfo,
                    null,
                    pSampler
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanSampler: desc {} result {}",
                        desc, VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanSampler(device, desc,
                    pSampler.get(0));
        }
    }

    public long vkSampler() {
        return mSampler;
    }

    @Override
    protected void destroy() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroySampler(
                device.vkDevice(),
                mSampler,
                null
        );
    }
}

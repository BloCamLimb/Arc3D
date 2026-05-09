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

package icyllis.arc3d.vulkan;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.core.WeakIdentityKey;
import icyllis.arc3d.engine.Engine.ImageType;
import icyllis.arc3d.engine.ManagedResource;
import icyllis.arc3d.engine.Resource;
import icyllis.arc3d.engine.Swizzle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NativeType;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents Vulkan image views, managed by {@link VulkanImage}.
 */
public final class VulkanImageView extends ManagedResource {

    private final WeakIdentityKey<@RawPtr VulkanImageView> mUniqueID;

    private final long mImageView;
    private final short mSwizzle;
    private final short mMipLevelCount;
    private final int mBaseArrayLayer;
    private final int mLayerCount;

    private VulkanImageView(VulkanDevice device, long imageView,
                            short swizzle, int mipLevelCount,
                            int baseArrayLayer, int layerCount) {
        super(device);
        mImageView = imageView;
        mSwizzle = swizzle;
        mMipLevelCount = (short) mipLevelCount;
        mBaseArrayLayer = baseArrayLayer;
        mLayerCount = layerCount;
        assert imageView != VK_NULL_HANDLE;
        mUniqueID = new WeakIdentityKey<>(this);
    }

    /**
     * Create a shader resource view as shader input, for texture lookup.
     * Or create a render target view as attachment.
     *
     * @param imageType see {@link ImageType}
     * @param swizzle   see {@link Swizzle}
     */
    @Nullable
    @SharedPtr
    public static VulkanImageView make(@NonNull VulkanDevice device,
                                       long vkImage,
                                       int imageType,
                                       @NativeType("VkFormat") int vkFormat,
                                       short swizzle,
                                       int mipLevelCount,
                                       int baseArrayLayer,
                                       int layerCount) {
        try (var stack = MemoryStack.stackPush()) {
            var pCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(0)
                    .image(vkImage)
                    .viewType(VKUtil.toVkImageViewType(imageType))
                    .format(vkFormat);
            if (swizzle != Swizzle.RGBA) {
                pCreateInfo.components().set(
                        VKUtil.toVkComponentSwizzle(Swizzle.getR(swizzle)),
                        VKUtil.toVkComponentSwizzle(Swizzle.getG(swizzle)),
                        VKUtil.toVkComponentSwizzle(Swizzle.getB(swizzle)),
                        VKUtil.toVkComponentSwizzle(Swizzle.getA(swizzle))
                );
            } else {
                pCreateInfo.components().set(
                        VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY
                );
            }
            pCreateInfo.subresourceRange()
                    .aspectMask(VKUtil.getFullAspectMask(vkFormat))
                    .baseMipLevel(0)
                    .levelCount(mipLevelCount)
                    .baseArrayLayer(baseArrayLayer)
                    .layerCount(layerCount);
            var pView = stack.mallocLong(1);
            var result = vkCreateImageView(
                    device.vkDevice(),
                    pCreateInfo,
                    null,
                    pView
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanImageView: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }
            return new VulkanImageView(device, pView.get(0),
                    swizzle, mipLevelCount, baseArrayLayer, layerCount);
        }
    }

    @NativeType("VkImageView")
    public long vkImageView() {
        return mImageView;
    }

    /**
     * Similar to {@link Resource#getUniqueID()}.
     */
    public WeakIdentityKey<@RawPtr VulkanImageView> getUniqueID() {
        return mUniqueID;
    }

    public short getSwizzle() {
        return mSwizzle;
    }

    public int getMipLevelCount() {
        return mMipLevelCount;
    }

    public int getBaseArrayLayer() {
        return mBaseArrayLayer;
    }

    public int getLayerCount() {
        return mLayerCount;
    }

    @Override
    protected void deallocate() {
        VulkanDevice device = (VulkanDevice) getDevice();
        vkDestroyImageView(device.vkDevice(), mImageView, null);
    }
}

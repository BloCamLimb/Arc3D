/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.vulkan;

import icyllis.akashigi.engine.*;

import javax.annotation.Nonnull;

import static icyllis.akashigi.vulkan.VkCore.*;
import static org.lwjgl.vulkan.EXTImageDrmFormatModifier.VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;

public final class VkBackendTexture extends BackendTexture {

    // We don't know if the backend texture is made renderable or not, so we default the usage flags
    // to include color attachment as well.
    private static final int DEFAULT_USAGE_FLAGS = VK_IMAGE_USAGE_TRANSFER_DST_BIT |
            VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
            VK_IMAGE_USAGE_SAMPLED_BIT |
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

    private final VkImageInfo mInfo;
    final VkSharedImageInfo mState;

    private final BackendFormat mBackendFormat;

    // The VkImageInfo can NOT be modified anymore.
    public VkBackendTexture(int width, int height, VkImageInfo info) {
        this(width, height, info, new VkSharedImageInfo(info), VkBackendFormat.make(info.mFormat,
                info.mMemoryHandle != -1 || info.mImageTiling == VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT));
    }

    VkBackendTexture(int width, int height, VkImageInfo info,
                     VkSharedImageInfo state, BackendFormat backendFormat) {
        super(width, height);
        if (info.mImageUsageFlags == 0) {
            info.mImageUsageFlags = DEFAULT_USAGE_FLAGS;
        }
        mInfo = info;
        mState = state;
        mBackendFormat = backendFormat;
    }

    @Override
    public int getBackend() {
        return Engine.Vulkan;
    }

    @Override
    public boolean isExternal() {
        return mBackendFormat.isExternal();
    }

    @Override
    public boolean isMipmapped() {
        return mInfo.mLevelCount > 1;
    }

    @Override
    public boolean getVkImageInfo(VkImageInfo info) {
        info.set(mInfo);
        info.mImageLayout = mState.getImageLayout();
        info.mCurrentQueueFamily = mState.getQueueFamilyIndex();
        return true;
    }

    @Override
    public void setVkImageLayout(int layout) {
        mState.setImageLayout(layout);
    }

    @Override
    public void setVkQueueFamilyIndex(int queueFamilyIndex) {
        mState.setQueueFamilyIndex(queueFamilyIndex);
    }

    @Nonnull
    @Override
    public BackendFormat getBackendFormat() {
        return mBackendFormat;
    }

    @Override
    public boolean isProtected() {
        return mInfo.mProtected;
    }

    @Override
    public boolean isSameTexture(BackendTexture texture) {
        if (texture instanceof VkBackendTexture t) {
            return mInfo.mImage == t.mInfo.mImage;
        }
        return false;
    }
}
/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2026 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.engine.Buffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import static icyllis.arc3d.engine.Engine.BufferUsageFlags;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK11.*;

public final class VulkanBuffer extends Buffer {

    private final long mBuffer;
    private final VulkanAllocation mMemoryAlloc;

    private int mCurrentAccess;

    public VulkanBuffer(VulkanDevice device,
                        long size,
                        int usage,
                        long buffer, VulkanAllocation memoryAlloc) {
        super(device, size, usage);
        mBuffer = buffer;
        mMemoryAlloc = memoryAlloc;
        assert buffer != VK_NULL_HANDLE;
    }

    @Nullable
    @SharedPtr
    public static VulkanBuffer make(VulkanDevice device,
                                    long size,
                                    int usage) {
        assert (size > 0);

        try (var stack = MemoryStack.stackPush()) {
            var pCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(device.isProtectedContext() ? VK_BUFFER_CREATE_PROTECTED_BIT : 0)
                    .size(size);

            int vkUsage = 0;
            if ((usage & BufferUsageFlags.kVertex) != 0) {
                vkUsage |= VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            }
            if ((usage & BufferUsageFlags.kIndex) != 0) {
                vkUsage |= VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            }
            if ((usage & BufferUsageFlags.kDrawIndirect) != 0) {
                vkUsage |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
            }
            if ((usage & BufferUsageFlags.kUpload) != 0) {
                vkUsage |= VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            }
            if ((usage & BufferUsageFlags.kReadback) != 0) {
                vkUsage |= VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            }
            if ((usage & BufferUsageFlags.kUniform) != 0) {
                vkUsage |= VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            }
            if ((usage & BufferUsageFlags.kStorage) != 0) {
                vkUsage |= VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            }
            //TODO texel buffer hint

            if ((vkUsage & BufferUsageFlags.kDeviceLocal) != 0) {
                vkUsage |= VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            }

            pCreateInfo
                    .usage(vkUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .pQueueFamilyIndices(null);

            var pBuffer = stack.mallocLong(1);
            var result = vkCreateBuffer(
                    device.vkDevice(),
                    pCreateInfo,
                    null,
                    pBuffer
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                device.getLogger().error("Failed to create VulkanBuffer: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            var allocator = device.getMemoryAllocator();
            var allocInfo = new VulkanAllocation();

            // only upload buffer use persistent mapping, and it's always combined with host visible flag
            boolean persistentlyMapped = (usage & BufferUsageFlags.kUpload) != 0;
            int allocFlags = persistentlyMapped ? VulkanMemoryAllocator.kPersistentlyMapped_AllocFlag : 0;

            if (!allocator.allocateBufferMemory(
                    device, pBuffer.get(0), usage, allocFlags, allocInfo
            )) {
                vkDestroyBuffer(device.vkDevice(), pBuffer.get(0), null);
                device.getLogger().error("Failed to create VulkanBuffer: cannot allocate {} bytes from device",
                        size);
                return null;
            }

            result = vkBindImageMemory(
                    device.vkDevice(),
                    pBuffer.get(0),
                    allocInfo.mMemory,
                    allocInfo.mOffset
            );
            device.checkResult(result);
            if (result != VK_SUCCESS) {
                allocator.freeMemory(allocInfo);
                vkDestroyBuffer(device.vkDevice(), pBuffer.get(0), null);
                device.getLogger().error("Failed to bind buffer memory: {}",
                        VKUtil.getResultMessage(result));
                return null;
            }

            return new VulkanBuffer(device, size, usage, pBuffer.get(0), allocInfo);
        }
    }

    public long vkBuffer() {
        return mBuffer;
    }

    public int getCurrentAccess() {
        return mCurrentAccess;
    }

    public void setCurrentAccess(int currentAccess) {
        mCurrentAccess = currentAccess;
    }

    @Override
    protected void onRelease() {
        VulkanDevice device = (VulkanDevice) getDevice();
        device.getMemoryAllocator().freeMemory(mMemoryAlloc);
        vkDestroyBuffer(device.vkDevice(), mBuffer, null);
    }

    @Override
    protected long onMap(int mode, long offset, long size) {
        if (mode == kWriteDiscard_MapMode &&
                mMemoryAlloc.mMappedPointer != NULL) {
            return mMemoryAlloc.mMappedPointer + offset;
        }
        VulkanDevice device = (VulkanDevice) getDevice();
        var allocator = device.getMemoryAllocator();
        var mappedBuffer = allocator.mapMemory(device, mMemoryAlloc);
        if (mappedBuffer == NULL) {
            device.getLogger().error("Failed to map buffer {}", this);
        }
        if (mappedBuffer != NULL && mode == kRead_MapMode && size != 0) {
            // make device writes visible to host, availability was guaranteed by a pipeline barrier
            if ((mMemoryAlloc.mMemoryFlags & VulkanAllocation.kHostCoherent_Flag) == 0) {
                boolean result = allocator.invalidateMemory(device, mMemoryAlloc, offset, size);
                if (!result) {
                    device.getLogger().error("Failed to invalidate mapped memory {}", this);
                }
            }
        }
        return mappedBuffer + offset;
    }

    @Override
    protected void onUnmap(int mode, long offset, long size) {
        VulkanDevice device = (VulkanDevice) getDevice();
        var allocator = device.getMemoryAllocator();
        if (mode == kWriteDiscard_MapMode && size != 0) {
            // make host writes available to device, visibility will be guaranteed by vkQueueSubmit
            if ((mMemoryAlloc.mMemoryFlags & VulkanAllocation.kHostCoherent_Flag) == 0) {
                boolean result = allocator.flushMemory(device, mMemoryAlloc, offset, size);
                if (!result) {
                    device.getLogger().error("Failed to flush mapped memory {}", this);
                }
            }
        }
        if (mode == kWriteDiscard_MapMode &&
                mMemoryAlloc.mMappedPointer != NULL) {
            // persistent mapping case, noop
            return;
        }
        allocator.unmapMemory(mMemoryAlloc);
    }
}

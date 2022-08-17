/*
 * Arc UI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arc UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcui.engine;

/**
 * Represents a device memory block that <b>prefers</b> to allocate GPU memory.
 * Also known as geometric buffer, g-buffer. To be exact, GLBuffer or VkBuffer.
 */
public abstract class GpuBuffer extends GpuResource implements Buffer {

    private final int mSizeInBytes;

    public GpuBuffer(Server server, int sizeInBytes) {
        super(server);
        mSizeInBytes = sizeInBytes;
    }

    @Override
    public int size() {
        return mSizeInBytes;
    }

    @Override
    public boolean isCpuBuffer() {
        return false;
    }

    public long map() {
        return 0;
    }

    public void unmap() {
    }

    public boolean isMapped() {
        return false;
    }

    public boolean updateData(long src, int offset, int size) {
        return false;
    }
}

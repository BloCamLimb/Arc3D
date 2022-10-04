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

package icyllis.akashigi.engine;

import icyllis.akashigi.core.ImageInfo;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class DataUtils {

    public static final Unsafe UNSAFE;

    static {
        try {
            Field unsafe = MemoryUtil.class.getDeclaredField("UNSAFE");
            unsafe.setAccessible(true);
            UNSAFE = (Unsafe) unsafe.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static boolean compressionTypeIsOpaque(int compression) {
        return switch (compression) {
            case ImageInfo.COMPRESSION_TYPE_NONE,
                    ImageInfo.COMPRESSION_TYPE_BC1_RGB8_UNORM,
                    ImageInfo.COMPRESSION_TYPE_ETC2_RGB8_UNORM -> true;
            case ImageInfo.COMPRESSION_TYPE_BC1_RGBA8_UNORM -> false;
            default -> throw new IllegalArgumentException();
        };
    }

    public static int num4x4Blocks(int size) {
        return ((size + 3) & ~3) >> 2;
    }

    public static long numBlocks(int compression, int width, int height) {
        return switch (compression) {
            case ImageInfo.COMPRESSION_TYPE_NONE -> (long) width * height;
            case ImageInfo.COMPRESSION_TYPE_ETC2_RGB8_UNORM,
                    ImageInfo.COMPRESSION_TYPE_BC1_RGB8_UNORM,
                    ImageInfo.COMPRESSION_TYPE_BC1_RGBA8_UNORM -> {
                long numBlocksWidth = num4x4Blocks(width);
                long numBlocksHeight = num4x4Blocks(height);
                yield numBlocksWidth * numBlocksHeight;
            }
            default -> throw new IllegalArgumentException();
        };
    }

    private DataUtils() {
    }
}
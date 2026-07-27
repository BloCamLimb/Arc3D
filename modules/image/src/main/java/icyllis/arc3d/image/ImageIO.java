/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.image;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

/**
 * Core class of Arc3D Core Image Codec.
 */
public final class ImageIO {

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean test(@NonNull ByteBuffer input,
                               @NonNull Predicate<ByteBuffer> filter) {
        input.mark();
        try {
            return filter.test(input);
        } catch (Exception e) {
            return false;
        } finally {
            input.reset();
        }
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterPNG(@NonNull ByteBuffer input) {
        for (byte b : PNG.FILE_SIGNATURE) {
            if (input.get() != b) {
                return false;
            }
        }
        return true;
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterBMP(@NonNull ByteBuffer input) {
        return input.get() == (byte) 'B' &&
                input.get() == (byte) 'M';
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterGIF(@NonNull ByteBuffer input) {
        int b;
        return input.get() == (byte) 'G' &&
                input.get() == (byte) 'I' &&
                input.get() == (byte) 'F' &&
                input.get() == (byte) '8' &&
                ((b = input.get()) == '7' || b == '9') &&
                input.get() == (byte) 'a';
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterJPEG(@NonNull ByteBuffer input) {
        for (byte b : JPEG.FILE_SIGNATURE) {
            if (input.get() != b) {
                return false;
            }
        }
        return true;
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterTIFF(@NonNull ByteBuffer input) {
        byte b0 = input.get();
        byte b1 = input.get();
        byte b2 = input.get();
        byte b3 = input.get();
        return (b0 == (byte) 0x49 && b1 == (byte) 0x49 &&
                b2 == (byte) 0x2a && b3 == (byte) 0x00)
                ||
               (b0 == (byte) 0x4d && b1 == (byte) 0x4d &&
                b2 == (byte) 0x00 && b3 == (byte) 0x2a);
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterPIC(@NonNull ByteBuffer input) {
        if (input.get() != (byte) 0x53 ||
                input.get() != (byte) 0x80 ||
                input.get() != (byte) 0xF6 ||
                input.get() != (byte) 0x34)
            return false;
        input.position(input.position() + 84);
        return input.get() == (byte) 'P' &&
                input.get() == (byte) 'I' &&
                input.get() == (byte) 'C' &&
                input.get() == (byte) 'T';
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterPNM(@NonNull ByteBuffer input) {
        int b;
        return input.get() == (byte) 'P' &&
                ((b = input.get()) >= '1' && b <= '6');
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterPAM(@NonNull ByteBuffer input) {
        // Magic: exactly "P7\n"
        return input.get() == (byte) 'P' &&
                input.get() == (byte) '7' &&
                input.get() == (byte) '\n';
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterRadiance(@NonNull ByteBuffer input) {
        return input.get() == (byte) '#' &&
                input.get() == (byte) '?' &&
                input.get() == (byte) 'R' &&
                input.get() == (byte) 'A' &&
                input.get() == (byte) 'D' &&
                input.get() == (byte) 'I' &&
                input.get() == (byte) 'A' &&
                input.get() == (byte) 'N' &&
                input.get() == (byte) 'C' &&
                input.get() == (byte) 'E' &&
                input.get() == (byte) '\n';
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterOpenEXR(@NonNull ByteBuffer input) {
        for (byte b : OpenEXR.FILE_SIGNATURE) {
            if (input.get() != b) {
                return false;
            }
        }
        return true;
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterKTX2(@NonNull ByteBuffer input) {
        for (byte b : KTX2.FILE_SIGNATURE) {
            if (input.get() != b) {
                return false;
            }
        }
        return true;
    }

    /**
     * @hide
     * @hidden
     */
    @ApiStatus.Internal
    public static boolean filterTGA(@NonNull ByteBuffer input) {
        // TGA has no magic number, it must be the last
        input.get();
        int color_map_type = input.get() & 0xff;
        if (color_map_type != 0 && color_map_type != 1)
            return false;
        int data_type_code = input.get() & 0xff;
        if (color_map_type == 1) {
            // Uncompressed, color-mapped images.
            // Run-Length Encoded color-mapped images.
            if (data_type_code != 1 && data_type_code != 9)
                return false;
            input.getInt(); // color map range
            int color_map_depth = input.get() & 0xff;
            if (color_map_depth != 16 && color_map_depth != 24 && color_map_depth != 32)
                return false;
        } else {
            if (data_type_code != 2 && data_type_code != 3 && data_type_code != 10 && data_type_code != 11)
                return false;
            input.getInt(); // color map range
            input.get(); // color map depth
        }
        input.getInt(); // origin
        input.getInt(); // dimensions
        int bits_per_pixel = input.get() & 0xff;
        if (color_map_type == 1 && bits_per_pixel != 8 && bits_per_pixel != 16)
            return false;
        return bits_per_pixel == 16 || bits_per_pixel == 24 || bits_per_pixel == 32;
    }
}

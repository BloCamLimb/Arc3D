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

package icyllis.arc3d.core.image;

public abstract class Predictor {

    // the widest vector is 512 bits; allocating more eliminates the need to handle remainder scalar values
    // (prevents index-out-of-bounds).
    public static final int HEADROOM = 512/8;

    public static Predictor createInstance() {
        boolean hasIncubatorVector = false;
        try {
            Class.forName("jdk.incubator.vector.ByteVector", false, Predictor.class.getClassLoader());
            hasIncubatorVector = true;
        } catch (ClassNotFoundException ignored) {
        }
        if (hasIncubatorVector) {
            return new PredictorIncubatorVector();
        } else {
            return new PredictorStandard();
        }
    }

    public abstract void decodeSub1(byte[] curr, int count);
    public abstract void decodeSub2(byte[] curr, int count);
    public abstract void decodeSub3(byte[] curr, int count);
    public abstract void decodeSub4(byte[] curr, int count);
    public abstract void decodeSub6(byte[] curr, int count);
    public abstract void decodeSub8(byte[] curr, int count);

    // dest and curr may NOT be the same array
    public abstract void encodeSub(byte[] curr, byte[] dest, int count, int bpp);

    // auto vectorization, fastest
    public static void decodeUp(byte[] curr, byte[] prev, int count) {
        for (int i = 0; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
        }
    }

    // auto vectorization, fastest
    public static void encodeUp(byte[] curr, byte[] prev, byte[] dest, int count) {
        for (int i = 0; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (prev[i] & 0xFF));
        }
    }

    public static void decodeAverage(byte[] curr, byte[] prev, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + ((prev[i] & 0xFF) >> 1));
        }
        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (((prev[i] & 0xFF) + (curr[i - bpp] & 0xFF)) >> 1));
        }
    }

    public abstract void decodeAverage3(byte[] curr, byte[] prev, int count);
    public abstract void decodeAverage4(byte[] curr, byte[] prev, int count);
    public abstract void decodeAverage6(byte[] curr, byte[] prev, int count);
    public abstract void decodeAverage8(byte[] curr, byte[] prev, int count);

    // dest and curr may NOT be the same array
    public abstract void encodeAverage(byte[] curr, byte[] prev, byte[] dest, int count, int bpp);

    // this is an optimized version from stb_image.h => stbi__paeth
    // equivalent to paeth_std, but much faster
    public static int paeth(int a, int b, int c) {
        int thresh = c*3 - (a + b);
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        int t0 = (hi <= thresh) ? lo : c;
        return (thresh <= lo) ? hi : t0;
    }

    // reference implementation
    @Deprecated
    public static int paeth_std(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);

        if (pa <= pb && pa <= pc) {
            return a;
        } else if (pb <= pc) {
            return b;
        } else {
            return c;
        }
    }

    // bpp = 1, 1.7 times faster than reference implementation
    // bpp = 2, 3.0 times faster than reference implementation
    // bpp = 3, 4.1 times faster than reference implementation
    // bpp = 4, 4.6 times faster than reference implementation
    // bpp = 6, 4.8 times faster than reference implementation
    // bpp = 8, 4.9 times faster than reference implementation
    public static void decodePaeth(byte[] curr, byte[] prev, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
        }
        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + paeth(curr[i - bpp] & 0xFF, prev[i] & 0xFF, prev[i - bpp] & 0xFF));
        }
    }

    public abstract void decodePaeth3(byte[] curr, byte[] prev, int count);
    public abstract void decodePaeth4(byte[] curr, byte[] prev, int count);
    public abstract void decodePaeth6(byte[] curr, byte[] prev, int count);
    public abstract void decodePaeth8(byte[] curr, byte[] prev, int count);

    // dest and curr may NOT be the same array
    public abstract void encodePaeth(byte[] curr, byte[] prev, byte[] dest, int count, int bpp);

    public abstract long sumOfAbs(byte[] arr, int count);
}

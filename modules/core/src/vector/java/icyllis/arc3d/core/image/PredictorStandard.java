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

/**
 * Standard filtering engine, relying on auto-vectorization and loop unrolling.
 */
public class PredictorStandard extends Predictor {

    @Override
    public void decodeSub1(byte[] curr, int count) {
        for (int i = 1; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 1] & 0xFF));
        }
    }

    @Override
    public void decodeSub2(byte[] curr, int count) {
        for (int i = 2; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 2] & 0xFF));
        }
    }

    @Override
    public void decodeSub3(byte[] curr, int count) {
        for (int i = 3; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 3] & 0xFF));
        }
    }

    @Override
    public void decodeSub4(byte[] curr, int count) {
        for (int i = 4; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 4] & 0xFF));
        }
    }

    @Override
    public void decodeSub6(byte[] curr, int count) {
        for (int i = 6; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 6] & 0xFF));
        }
    }

    @Override
    public void decodeSub8(byte[] curr, int count) {
        for (int i = 8; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - 8] & 0xFF));
        }
    }

    @Override
    public void encodeSub(byte[] curr, byte[] dest, int count, int bpp) {
        System.arraycopy(curr, 0, dest, 0, bpp);
        for (int i = bpp; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (curr[i - bpp] & 0xFF));
        }
    }

    @Override
    public void decodeAverage3(byte[] curr, byte[] prev, int count) {
        decodeAverage(curr, prev, count, 3);
    }

    @Override
    public void decodeAverage4(byte[] curr, byte[] prev, int count) {
        decodeAverage(curr, prev, count, 4);
    }

    @Override
    public void decodeAverage6(byte[] curr, byte[] prev, int count) {
        decodeAverage(curr, prev, count, 6);
    }

    @Override
    public void decodeAverage8(byte[] curr, byte[] prev, int count) {
        decodeAverage(curr, prev, count, 8);
    }

    @Override
    public void encodeAverage(byte[] curr, byte[] prev, byte[] dest, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - ((prev[i] & 0xFF) >> 1));
        }
        for (int i = bpp; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (((prev[i] & 0xFF) + (curr[i - bpp] & 0xFF)) >> 1));
        }
    }

    // 4.1 times faster than reference implementation
    @Override
    public void decodePaeth3(byte[] curr, byte[] prev, int count) {
        decodePaeth(curr, prev, count, 3);
    }

    // 4.6 times faster than reference implementation
    @Override
    public void decodePaeth4(byte[] curr, byte[] prev, int count) {
        decodePaeth(curr, prev, count, 4);
    }

    // 4.8 times faster than reference implementation
    @Override
    public void decodePaeth6(byte[] curr, byte[] prev, int count) {
        decodePaeth(curr, prev, count, 6);
    }

    // 4.9 times faster than reference implementation
    @Override
    public void decodePaeth8(byte[] curr, byte[] prev, int count) {
        decodePaeth(curr, prev, count, 8);
    }

    @Override
    public void encodePaeth(byte[] curr, byte[] prev, byte[] dest, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (prev[i] & 0xFF));
        }
        for (int i = bpp; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - paeth(curr[i - bpp] & 0xFF, prev[i] & 0xFF, prev[i - bpp] & 0xFF));
        }
    }

    @Override
    public long sumOfAbs(byte[] arr, int count) {
        long totalSum = 0L;

        for (int i = 0; i < count; i++) {
            totalSum += Math.abs(arr[i]);
        }

        return totalSum;
    }
}

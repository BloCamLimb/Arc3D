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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Vectorized PNG filtering engine.
 */
public class PNGFilterIncubatorVector extends PNGFilter {

    private static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;

    // at most 512
    private static final VectorSpecies<Byte> B512_A;

    static {
        var preferred = ByteVector.SPECIES_PREFERRED;
        if (preferred.vectorBitSize() >= 512) {
            B512_A = ByteVector.SPECIES_512;
        } else {
            B512_A = preferred;
        }
    }

    private static final VectorSpecies<Byte> B_WIDE = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_WIDE = B_WIDE.withLanes(int.class);

    public static final VarHandle BYTE_ARRAY_AS_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());

    // 7.3 times faster than standard implementation
    @Override
    public void decodeSub1(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(1));
            v = v.add(v.unslice(2));
            v = v.add(v.unslice(4));
            v = v.add(v.unslice(8));

            carry = v.slice(15);
            v.intoArray(curr, i);
        }
    }

    // 3.5 times faster than standard implementation
    @Override
    public void decodeSub2(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(2));
            v = v.add(v.unslice(4));
            v = v.add(v.unslice(8));

            carry = v.slice(14);
            v.intoArray(curr, i);
        }
    }

    // 2.5 times faster than standard implementation
    @Override
    public void decodeSub3(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(3));
            v = v.add(v.unslice(6));
            v = v.add(v.unslice(12));

            carry = v.slice(13);
            v.intoArray(curr, i);
        }
    }

    // 2.2 times faster than standard implementation
    @Override
    public void decodeSub4(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(4));
            v = v.add(v.unslice(8));

            carry = v.slice(12);
            v.intoArray(curr, i);
        }
    }

    // 2.0 times faster than standard implementation
    @Override
    public void decodeSub6(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(6));
            v = v.add(v.unslice(12));

            carry = v.slice(10);
            v.intoArray(curr, i);
        }
    }

    // 3.5 times faster than standard implementation
    @Override
    public void decodeSub8(byte[] curr, int count) {
        ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            v = v.add(carry);
            v = v.add(v.unslice(8));

            carry = v.slice(8);
            v.intoArray(curr, i);
        }
    }

    @Override
    public void encodeSub(byte[] curr, byte[] dest, int count, int bpp) {
        /*ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
        for (int i = 0; i < count; i += 16) {
            ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

            ByteVector prev = v.unslice(8).add(carry);
            ByteVector out  = v.sub(prev);

            carry = v.slice(8);
            out.intoArray(dest, i);
        }*/
        System.arraycopy(curr, 0, dest, 0, bpp);

        for (int i = bpp; i < count; i += B512_A.length()) {
            ByteVector a = ByteVector.fromArray(B512_A, curr, i - bpp);
            ByteVector x = ByteVector.fromArray(B512_A, curr, i);
            x.sub(a).intoArray(dest, i);
        }
    }

    // 1.2 times faster than standard implementation
    @Override
    public void decodeAverage3(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 3) {
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(a.add(b).lanewise(VectorOperators.LSHR, (short) 1)
                    .convertShape(VectorOperators.S2B, B64, 0));
            curr[i+0] = x.lane(0);
            curr[i+1] = x.lane(1);
            curr[i+2] = x.lane(2);
        }
    }

    // 1.8 times faster than standard implementation
    @Override
    public void decodeAverage4(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 4) {
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(a.add(b).lanewise(VectorOperators.LSHR, (short) 1)
                    .convertShape(VectorOperators.S2B, B64, 0));
            BYTE_ARRAY_AS_INT.set(curr, i, x.reinterpretAsInts().lane(0));
        }
    }

    // 1.2 times faster than standard implementation
    @Override
    public void decodeAverage6(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b;
        ByteVector x = ByteVector.zero(B64);

        byte[] tmp = new byte[8];

        for (int i = 0; i < count; i += 6) {
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(a.add(b).lanewise(VectorOperators.LSHR, (short) 1)
                    .convertShape(VectorOperators.S2B, B64, 0));
            x.intoArray(tmp, 0);
            System.arraycopy(tmp, 0, curr, i, 6);
        }
    }

    // 2.7 times faster than standard implementation
    @Override
    public void decodeAverage8(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 8) {
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(a.add(b).lanewise(VectorOperators.LSHR, (short) 1)
                    .convertShape(VectorOperators.S2B, B64, 0));
            x.intoArray(curr, i);
        }
    }

    // 8.0 times faster than standard implementation, if 256 species (AVX2) is used
    @Override
    public void encodeAverage(byte[] curr, byte[] prev, byte[] dest, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - ((prev[i] & 0xFF) >> 1));
        }
        for (int i = bpp; i < count; i += B512_A.length()) {
            ByteVector x = ByteVector.fromArray(B512_A, curr, i);
            ByteVector a = ByteVector.fromArray(B512_A, curr, i - bpp);
            ByteVector b = ByteVector.fromArray(B512_A, prev, i);

            // avg(a,b) = (a & b) + ((a ^ b) >>> 1)
            ByteVector avg = a.and(b).add(a.lanewise(VectorOperators.XOR, b).lanewise(VectorOperators.LSHR, (byte) 1));

            x.sub(avg).intoArray(dest, i);
        }
    }

    // this is an optimized version from stb_image.h => stbi__paeth
    private static ShortVector paeth(ShortVector a, ShortVector b, ShortVector c) {
        ShortVector thresh = c.mul((short) 3).sub(a.add(b));
        ShortVector lo = a.min(b);
        ShortVector hi = a.max(b);

        VectorMask<Short> m0 = hi.compare(VectorOperators.LE, thresh);
        ShortVector t0 = c.blend(lo, m0);

        VectorMask<Short> m1 = thresh.compare(VectorOperators.LE, lo);
        return t0.blend(hi, m1);
    }

    // 6.7 times faster than reference implementation
    // 1.6 times faster than standard implementation
    @Override
    public void decodePaeth3(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b = ShortVector.zero(S128);
        ShortVector c;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 3) {
            c = b;
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(paeth(a, b, c)
                    .convertShape(VectorOperators.S2B, B64, 0));
            curr[i+0] = x.lane(0);
            curr[i+1] = x.lane(1);
            curr[i+2] = x.lane(2);
        }
    }

    // 9.6 times faster than reference implementation
    // 2.1 times faster than standard implementation
    @Override
    public void decodePaeth4(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b = ShortVector.zero(S128);
        ShortVector c;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 4) {
            c = b;
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(paeth(a, b, c)
                    .convertShape(VectorOperators.S2B, B64, 0));
            BYTE_ARRAY_AS_INT.set(curr, i, x.reinterpretAsInts().lane(0));
        }
    }

    // 13.1 times faster than reference implementation
    // 2.7 times faster than standard implementation
    @Override
    public void decodePaeth6(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b = ShortVector.zero(S128);
        ShortVector c;
        ByteVector x = ByteVector.zero(B64);

        byte[] tmp = new byte[8];

        for (int i = 0; i < count; i += 6) {
            c = b;
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(paeth(a, b, c)
                    .convertShape(VectorOperators.S2B, B64, 0));
            x.intoArray(tmp, 0);
            System.arraycopy(tmp, 0, curr, i, 6);
        }
    }

    // 18.4 times faster than reference implementation
    // 3.7 times faster than standard implementation
    @Override
    public void decodePaeth8(byte[] curr, byte[] prev, int count) {
        ShortVector a;
        ShortVector b = ShortVector.zero(S128);
        ShortVector c;
        ByteVector x = ByteVector.zero(B64);

        for (int i = 0; i < count; i += 8) {
            c = b;
            b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            a = (ShortVector) x
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            x = ByteVector.fromArray(B64, curr, i);

            x = x.add(paeth(a, b, c)
                    .convertShape(VectorOperators.S2B, B64, 0));
            x.intoArray(curr, i);
        }
    }

    @Override
    public void encodePaeth(byte[] curr, byte[] prev, byte[] dest, int count, int bpp) {
        for (int i = 0; i < bpp; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (prev[i] & 0xFF));
        }
        for (int i = bpp; i < count; i += 8) {
            ByteVector x = ByteVector.fromArray(B64, curr, i);
            ShortVector a = (ShortVector) ByteVector.fromArray(B64, curr, i - bpp)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            ShortVector b = (ShortVector) ByteVector.fromArray(B64, prev, i)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);
            ShortVector c = (ShortVector) ByteVector.fromArray(B64, prev, i - bpp)
                    .convertShape(VectorOperators.ZERO_EXTEND_B2S, S128, 0);

            x.sub(paeth(a, b, c).convertShape(VectorOperators.S2B, B64, 0))
                    .intoArray(dest, i);
        }
    }

    // 4.7 times faster than standard implementation, if 256 species (AVX2) is used
    // 4.0 times faster, if 128 species is used
    @Override
    public long sumOfAbs(byte[] arr, int count) {
        long totalSum = 0L;
        int i = 0;
        final int speciesLength = B_WIDE.length();
        final int upperBound = B_WIDE.loopBound(count);

        IntVector acc0 = IntVector.zero(I_WIDE);
        IntVector acc1 = IntVector.zero(I_WIDE);
        IntVector acc2 = IntVector.zero(I_WIDE);
        IntVector acc3 = IntVector.zero(I_WIDE);

        int blockCount = 0;

        for (; i < upperBound; i += speciesLength) {
            ByteVector bv = ByteVector.fromArray(B_WIDE, arr, i);

            IntVector iv0 = ((IntVector) bv.convert(VectorOperators.B2I, 0)).abs();
            IntVector iv1 = ((IntVector) bv.convert(VectorOperators.B2I, 1)).abs();
            IntVector iv2 = ((IntVector) bv.convert(VectorOperators.B2I, 2)).abs();
            IntVector iv3 = ((IntVector) bv.convert(VectorOperators.B2I, 3)).abs();

            acc0 = acc0.add(iv0);
            acc1 = acc1.add(iv1);
            acc2 = acc2.add(iv2);
            acc3 = acc3.add(iv3);

            blockCount += speciesLength;

            // avoid overflow, since abs is 128 at most, (16000000 + 1024) * 128 < 2^31
            if (blockCount >= 16_000_000) {
                totalSum += acc0.reduceLanes(VectorOperators.ADD);
                totalSum += acc1.reduceLanes(VectorOperators.ADD);
                totalSum += acc2.reduceLanes(VectorOperators.ADD);
                totalSum += acc3.reduceLanes(VectorOperators.ADD);

                acc0 = IntVector.zero(I_WIDE);
                acc1 = IntVector.zero(I_WIDE);
                acc2 = IntVector.zero(I_WIDE);
                acc3 = IntVector.zero(I_WIDE);
                blockCount = 0;
            }
        }

        // remainder blocks
        if (blockCount > 0) {
            totalSum += acc0.reduceLanes(VectorOperators.ADD);
            totalSum += acc1.reduceLanes(VectorOperators.ADD);
            totalSum += acc2.reduceLanes(VectorOperators.ADD);
            totalSum += acc3.reduceLanes(VectorOperators.ADD);
        }

        // remainder scalars
        for (; i < count; i++) {
            totalSum += Math.abs(arr[i]);
        }

        return totalSum;
    }
}

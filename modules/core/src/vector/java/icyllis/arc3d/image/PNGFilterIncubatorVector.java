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

import jdk.incubator.vector.ByteVector;
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
}

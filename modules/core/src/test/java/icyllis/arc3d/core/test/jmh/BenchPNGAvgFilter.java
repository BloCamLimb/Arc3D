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

package icyllis.arc3d.core.test.jmh;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchPNGAvgFilter {

    // BPP = 4
    //Benchmark                               Mode  Cnt       Score      Error  Units
    //BenchPNGAvgFilter.decodeAvg_Java       thrpt    5  354094.914 ± 4852.409  ops/s
    //BenchPNGAvgFilter.decodeAvg_Vector128  thrpt    5  626213.390 ± 7985.023  ops/s

    // BPP = 3
    //Benchmark                               Mode  Cnt       Score       Error  Units
    //BenchPNGAvgFilter.decodeAvg_Java       thrpt    5  273123.835 ±  6672.359  ops/s
    //BenchPNGAvgFilter.decodeAvg_Vector128  thrpt    5  328872.687 ± 15902.372  ops/s

    // BPP = 6
    //Benchmark                               Mode  Cnt       Score       Error  Units
    //BenchPNGAvgFilter.decodeAvg_Java       thrpt    5  453650.192 ± 19003.037  ops/s
    //BenchPNGAvgFilter.decodeAvg_Vector128  thrpt    5  540282.702 ±  6089.033  ops/s

    // BPP = 8
    //Benchmark                               Mode  Cnt        Score       Error  Units
    //BenchPNGAvgFilter.decodeAvg_Java       thrpt    5   463046.698 ±  8129.897  ops/s
    //BenchPNGAvgFilter.decodeAvg_Vector128  thrpt    5  1238944.782 ± 41923.569  ops/s

    public byte[] row;
    public byte[] prevRow;
    public int bpp;

    private static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;

    @Setup
    public void setup() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        bpp = 3;
        row = new byte[6144];
        for (int i = 0; i < row.length; i++) {
            row[i] = (byte) r.nextInt(256);
        }
        prevRow = new byte[6144];
        for (int i = 0; i < prevRow.length; i++) {
            prevRow[i] = (byte) r.nextInt(256);
        }
    }

    @Benchmark
    public void decodeAvg_Java() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length;

        for (int i = 0; i < bpp; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + ((prev[i] & 0xFF) >> 1));
        }
        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (((prev[i] & 0xFF) + (curr[i - bpp] & 0xFF)) >> 1));
        }
    }

    public static final VarHandle BYTE_ARRAY_AS_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());

    @Benchmark
    public void decodeAvg_Vector128() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length - 12;

        if (bpp == 4) {
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
        } else if (bpp == 3) {
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
        } else if (bpp == 6) {
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
        } else if (bpp == 8) {
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
    }
}

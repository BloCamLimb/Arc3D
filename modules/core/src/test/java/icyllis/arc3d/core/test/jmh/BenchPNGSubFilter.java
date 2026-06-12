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
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorShuffle;
import org.lwjgl.system.MemoryUtil;
import org.openjdk.jmh.annotations.*;
import sun.misc.Unsafe;

import java.util.concurrent.ThreadLocalRandom;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchPNGSubFilter {

    // BPP = 1
    //Benchmark                                  Mode  Cnt       Score       Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5  117023.300 ±  1173.935  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5  116696.148 ±   933.087  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5  378371.494 ±  7278.358  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  853916.884 ± 59844.013  ops/s

    // BPP = 2
    //Benchmark                                  Mode  Cnt       Score        Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5  230983.256 ±  11318.847  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5  227808.562 ±   4736.223  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5  594863.167 ±  51033.453  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  802296.516 ± 169080.173  ops/s

    // BPP = 3
    //Benchmark                                  Mode  Cnt       Score        Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5  330564.977 ±  19426.036  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5  331948.381 ±  15328.072  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5  730364.058 ± 247601.187  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  843851.009 ± 133041.182  ops/s

    // BPP = 4
    //Benchmark                                  Mode  Cnt       Score        Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5  436672.283 ±  63708.084  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5  449561.349 ±  15854.729  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5  873186.112 ± 141105.585  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  967820.439 ± 368038.967  ops/s
    //BenchPNGSubFilter.decodeSub_Vector64      thrpt    5  902950.441 ± 441218.695  ops/s

    // BPP = 6
    //Benchmark                                  Mode  Cnt        Score        Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5   604930.048 ±  42569.555  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5   608698.061 ±  19898.200  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5   724045.638 ± 474890.143  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  1236985.970 ±  73347.520  ops/s

    // BPP = 8
    //Benchmark                                  Mode  Cnt        Score        Error  Units
    //BenchPNGSubFilter.decodeSub_Java          thrpt    5   486670.895 ± 131150.490  ops/s
    //BenchPNGSubFilter.decodeSub_Unsafe        thrpt    5   577876.328 ± 117301.050  ops/s
    //BenchPNGSubFilter.decodeSub_UnsafeUnroll  thrpt    5   876899.721 ± 235045.252  ops/s
    //BenchPNGSubFilter.decodeSub_Vector128     thrpt    5  1680179.805 ± 172721.142  ops/s

    public byte[] row;
    public long rowNative;
    //@Param({"1", "2", "3", "4", "6", "8"})
    public int bpp;

    @Setup
    public void setup() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        row = new byte[6144];
        for (int i = 0; i < row.length; i++) {
            row[i] = (byte) r.nextInt(256);
        }
        bpp = 8;
        rowNative = MemoryUtil.nmemAllocChecked(row.length);
        MemoryUtil.memCopy(row, rowNative);
    }

    @TearDown
    public void tearDown() {
        row = null;
        MemoryUtil.nmemFree(rowNative);
    }

    @Benchmark
    public void decodeSub_Java() {
        int bpp = this.bpp;
        byte[] curr = row;
        int count = row.length;

        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - bpp] & 0xFF));
        }
    }

    static final VectorShuffle<Byte> BROADCAST_HIGH =
            VectorShuffle.fromValues(ByteVector.SPECIES_64, 4,5,6,7, 4,5,6,7);

    @Benchmark
    public void decodeSub_Vector64() {
        int bpp = this.bpp;
        byte[] curr = row;
        int count = row.length;

        if (bpp == 4) {
            ByteVector carry = ByteVector.zero(ByteVector.SPECIES_64);
            for (int i = 0; i < count; i += 8) {
                ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_64, curr, i);
                v = v.add(v.unslice(4));
                v = v.add(carry);
                carry = v.rearrange(BROADCAST_HIGH);
                v.intoArray(curr, i);
            }
        } else {
            for (int i = bpp; i < count; i++) {
                curr[i] = (byte) ((curr[i] & 0xFF) + (curr[i - bpp] & 0xFF));
            }
        }
    }

    @Benchmark
    public void decodeSub_Vector128() {
        int bpp = this.bpp;
        byte[] curr = row;
        int count = row.length;

        if (bpp == 4) {
            ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
            for (int i = 0; i < count; i += 16) {
                ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

                v = v.add(carry);
                v = v.add(v.unslice(4));
                v = v.add(v.unslice(8));

                carry = v.slice(12);
                v.intoArray(curr, i);
            }
        } else if (bpp == 3) {
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
        } else if (bpp == 2) {
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
        } else if (bpp == 1) {
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
        } else if (bpp == 6) {
            ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
            for (int i = 0; i < count; i += 16) {
                ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

                v = v.add(carry);
                v = v.add(v.unslice(6));
                v = v.add(v.unslice(12));

                carry = v.slice(10);
                v.intoArray(curr, i);
            }
        } else if (bpp == 8) {
            ByteVector carry = ByteVector.zero(ByteVector.SPECIES_128);
            for (int i = 0; i < count; i += 16) {
                ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_128, curr, i);

                v = v.add(carry);
                v = v.add(v.unslice(8));

                carry = v.slice(8);
                v.intoArray(curr, i);
            }
        }
    }

    @Benchmark
    public void decodeSub_Unsafe() {
        int bpp = this.bpp;
        long curr = rowNative + bpp;
        long count = rowNative + row.length;
        for (long i = curr; i < count; i++) {
            MemoryUtil.memPutByte(i, (byte) ((MemoryUtil.memGetByte(i) & 0xFF) + (MemoryUtil.memGetByte(i - bpp) & 0xFF)));
        }
    }

    @Benchmark
    public void decodeSub_UnsafeUnroll() {
        long curr = rowNative + bpp;
        long count = rowNative + row.length;
        if (bpp == 4) {
            for (long i = curr; i < count; i += 4) {
                MemoryUtil.memPutByte(i + 0, (byte) ((MemoryUtil.memGetByte(i + 0) & 0xFF) + (MemoryUtil.memGetByte(i - 4) & 0xFF)));
                MemoryUtil.memPutByte(i + 1, (byte) ((MemoryUtil.memGetByte(i + 1) & 0xFF) + (MemoryUtil.memGetByte(i - 3) & 0xFF)));
                MemoryUtil.memPutByte(i + 2, (byte) ((MemoryUtil.memGetByte(i + 2) & 0xFF) + (MemoryUtil.memGetByte(i - 2) & 0xFF)));
                MemoryUtil.memPutByte(i + 3, (byte) ((MemoryUtil.memGetByte(i + 3) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        } else if (bpp == 3) {
            for (long i = curr; i < count; i += 3) {
                MemoryUtil.memPutByte(i + 0, (byte) ((MemoryUtil.memGetByte(i + 0) & 0xFF) + (MemoryUtil.memGetByte(i - 3) & 0xFF)));
                MemoryUtil.memPutByte(i + 1, (byte) ((MemoryUtil.memGetByte(i + 1) & 0xFF) + (MemoryUtil.memGetByte(i - 2) & 0xFF)));
                MemoryUtil.memPutByte(i + 2, (byte) ((MemoryUtil.memGetByte(i + 2) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        } else if (bpp == 2) {
            for (long i = curr; i < count; i += 2) {
                MemoryUtil.memPutByte(i + 0, (byte) ((MemoryUtil.memGetByte(i + 0) & 0xFF) + (MemoryUtil.memGetByte(i - 2) & 0xFF)));
                MemoryUtil.memPutByte(i + 1, (byte) ((MemoryUtil.memGetByte(i + 1) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        } else if (bpp == 1) {
            for (long i = curr; i < count; i++) {
                MemoryUtil.memPutByte(i, (byte) ((MemoryUtil.memGetByte(i) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        } else if (bpp == 6) {
            for (long i = curr; i < count; i += 6) {
                MemoryUtil.memPutByte(i + 0, (byte) ((MemoryUtil.memGetByte(i + 0) & 0xFF) + (MemoryUtil.memGetByte(i - 6) & 0xFF)));
                MemoryUtil.memPutByte(i + 1, (byte) ((MemoryUtil.memGetByte(i + 1) & 0xFF) + (MemoryUtil.memGetByte(i - 5) & 0xFF)));
                MemoryUtil.memPutByte(i + 2, (byte) ((MemoryUtil.memGetByte(i + 2) & 0xFF) + (MemoryUtil.memGetByte(i - 4) & 0xFF)));
                MemoryUtil.memPutByte(i + 3, (byte) ((MemoryUtil.memGetByte(i + 3) & 0xFF) + (MemoryUtil.memGetByte(i - 3) & 0xFF)));
                MemoryUtil.memPutByte(i + 4, (byte) ((MemoryUtil.memGetByte(i + 4) & 0xFF) + (MemoryUtil.memGetByte(i - 2) & 0xFF)));
                MemoryUtil.memPutByte(i + 5, (byte) ((MemoryUtil.memGetByte(i + 5) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        } else if (bpp == 8) {
            for (long i = curr; i < count; i += 8) {
                MemoryUtil.memPutByte(i + 0, (byte) ((MemoryUtil.memGetByte(i + 0) & 0xFF) + (MemoryUtil.memGetByte(i - 8) & 0xFF)));
                MemoryUtil.memPutByte(i + 1, (byte) ((MemoryUtil.memGetByte(i + 1) & 0xFF) + (MemoryUtil.memGetByte(i - 7) & 0xFF)));
                MemoryUtil.memPutByte(i + 2, (byte) ((MemoryUtil.memGetByte(i + 2) & 0xFF) + (MemoryUtil.memGetByte(i - 6) & 0xFF)));
                MemoryUtil.memPutByte(i + 3, (byte) ((MemoryUtil.memGetByte(i + 3) & 0xFF) + (MemoryUtil.memGetByte(i - 5) & 0xFF)));
                MemoryUtil.memPutByte(i + 4, (byte) ((MemoryUtil.memGetByte(i + 4) & 0xFF) + (MemoryUtil.memGetByte(i - 4) & 0xFF)));
                MemoryUtil.memPutByte(i + 5, (byte) ((MemoryUtil.memGetByte(i + 5) & 0xFF) + (MemoryUtil.memGetByte(i - 3) & 0xFF)));
                MemoryUtil.memPutByte(i + 6, (byte) ((MemoryUtil.memGetByte(i + 6) & 0xFF) + (MemoryUtil.memGetByte(i - 2) & 0xFF)));
                MemoryUtil.memPutByte(i + 7, (byte) ((MemoryUtil.memGetByte(i + 7) & 0xFF) + (MemoryUtil.memGetByte(i - 1) & 0xFF)));
            }
        }
    }
}

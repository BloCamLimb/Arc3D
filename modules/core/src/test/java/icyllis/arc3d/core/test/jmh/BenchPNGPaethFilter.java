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

import icyllis.arc3d.image.PNGFilter;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.lwjgl.system.MemoryUtil;
import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchPNGPaethFilter {

    // BPP = 1
    //Benchmark                                   Mode  Cnt      Score     Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java       thrpt    5  56530.656 ± 761.165  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd    thrpt    5  32939.172 ± 961.674  ops/s

    // BPP = 2
    //Benchmark                                   Mode  Cnt       Score       Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java       thrpt    5  105985.648 ± 10014.258  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd    thrpt    5   35242.228 ±   822.302  ops/s

    // BPP = 3
    //Benchmark                                      Mode  Cnt       Score       Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java          thrpt    5  143725.352 ±  2628.129  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd       thrpt    5   35255.069 ±   786.783  ops/s
    //BenchPNGPaethFilter.decodePaeth_Unsafe        thrpt    5  132325.582 ± 12402.153  ops/s
    //BenchPNGPaethFilter.decodePaeth_UnsafeUnroll  thrpt    5  133866.785 ±  4528.326  ops/s
    //BenchPNGPaethFilter.decodePaeth_Vector128     thrpt    5  235445.133 ±  5702.135  ops/s

    // BPP = 4
    //Benchmark                                      Mode  Cnt       Score       Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java          thrpt    5  162440.202 ± 45346.324  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd       thrpt    5   35237.103 ±   986.412  ops/s
    //BenchPNGPaethFilter.decodePaeth_Unsafe        thrpt    5  137443.764 ± 11001.499  ops/s
    //BenchPNGPaethFilter.decodePaeth_UnsafeUnroll  thrpt    5  140843.108 ±  3819.990  ops/s
    //BenchPNGPaethFilter.decodePaeth_Vector128     thrpt    5  339838.249 ±  5842.900  ops/s

    // BPP = 6
    //Benchmark                                   Mode  Cnt       Score       Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java       thrpt    5  173849.570 ±  9784.565  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd    thrpt    5   35979.025 ±   390.012  ops/s
    //BenchPNGPaethFilter.decodePaeth_Vector128  thrpt    5  471435.240 ± 30178.021  ops/s

    // BPP = 8
    //Benchmark                                   Mode  Cnt       Score      Error  Units
    //BenchPNGPaethFilter.decodePaeth_Java       thrpt    5  175557.592 ± 4895.255  ops/s
    //BenchPNGPaethFilter.decodePaeth_JavaStd    thrpt    5   35643.221 ±  969.496  ops/s
    //BenchPNGPaethFilter.decodePaeth_Vector128  thrpt    5  656401.903 ± 8125.598  ops/s

    public byte[] row;
    public byte[] prevRow;
    public long rowNative;
    public long prevRowNative;
    public int bpp;

    private static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;

    @Setup
    public void setup() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        bpp = 4;
        row = new byte[6144];
        for (int i = 0; i < row.length; i++) {
            row[i] = (byte) r.nextInt(256);
        }
        prevRow = new byte[6144];
        for (int i = 0; i < prevRow.length; i++) {
            prevRow[i] = (byte) r.nextInt(256);
        }
        rowNative = MemoryUtil.nmemAllocChecked(row.length);
        MemoryUtil.memCopy(row, rowNative);
        prevRowNative = MemoryUtil.nmemAllocChecked(prevRow.length);
        MemoryUtil.memCopy(prevRow, prevRowNative);
    }

    @TearDown
    public void tearDown() {
        row = null;
        MemoryUtil.nmemFree(rowNative);
        prevRow = null;
        MemoryUtil.nmemFree(prevRowNative);
    }

    @Benchmark
    public void decodePaeth_Java() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length;
        for (int i = 0; i < bpp; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
        }
        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + PNGFilter.paeth(curr[i - bpp] & 0xFF, prev[i] & 0xFF, prev[i - bpp] & 0xFF));
        }
    }

    @Benchmark
    public void decodePaeth_JavaStd() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length;
        for (int i = 0; i < bpp; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
        }
        for (int i = bpp; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + PNGFilter.paeth_std(curr[i - bpp] & 0xFF, prev[i] & 0xFF, prev[i - bpp] & 0xFF));
        }
    }

    private static ShortVector paeth(ShortVector a, ShortVector b, ShortVector c) {
        ShortVector thresh = c.mul((short) 3).sub(a.add(b));
        ShortVector lo = a.min(b);
        ShortVector hi = a.max(b);

        VectorMask<Short> m0 = hi.compare(VectorOperators.LE, thresh);
        ShortVector t0 = c.blend(lo, m0);

        VectorMask<Short> m1 = thresh.compare(VectorOperators.LE, lo);
        return t0.blend(hi, m1);
    }

    public static final VarHandle BYTE_ARRAY_AS_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());

    @Benchmark
    public void decodePaeth_Vector128() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length - 12;

        if (bpp == 4) {
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
        } else if (bpp == 3) {
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
        } else if (bpp == 6) {
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
        } else if (bpp == 8) {
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
        } else {
            for (int i = 0; i < bpp; i++) {
                curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
            }
            for (int i = bpp; i < count; i++) {
                curr[i] = (byte) ((curr[i] & 0xFF) + PNGFilter.paeth(curr[i - bpp] & 0xFF, prev[i] & 0xFF, prev[i - bpp] & 0xFF));
            }
        }
    }

    /*@Benchmark
    public void decodePaeth_Unsafe() {
        int bpp = this.bpp;
        long curr = rowNative;
        long prev = prevRowNative;
        int count = row.length;
        for (int i = 0; i < bpp; i++) {
            MemoryUtil.memPutByte(curr+i, (byte) ((MemoryUtil.memGetByte(curr+i) & 0xFF) + (MemoryUtil.memGetByte(prev+i) & 0xFF)));
        }
        for (int i = bpp; i < count; i++) {
            MemoryUtil.memPutByte(curr+i, (byte) ((MemoryUtil.memGetByte(curr+i) & 0xFF) + PNGFilter.paeth(
                    MemoryUtil.memGetByte(curr+i - bpp) & 0xFF,
                    MemoryUtil.memGetByte(prev+i) & 0xFF,
                    MemoryUtil.memGetByte(prev+i - bpp) & 0xFF
            )));
        }
    }

    @Benchmark
    public void decodePaeth_UnsafeUnroll() {
        int bpp = this.bpp;
        long curr = rowNative;
        long prev = prevRowNative;
        if (bpp == 4) {
            long count = rowNative + row.length;
            MemoryUtil.memPutByte(curr+0, (byte) ((MemoryUtil.memGetByte(curr+0) & 0xFF) + (MemoryUtil.memGetByte(prev+0) & 0xFF)));
            MemoryUtil.memPutByte(curr+1, (byte) ((MemoryUtil.memGetByte(curr+1) & 0xFF) + (MemoryUtil.memGetByte(prev+1) & 0xFF)));
            MemoryUtil.memPutByte(curr+2, (byte) ((MemoryUtil.memGetByte(curr+2) & 0xFF) + (MemoryUtil.memGetByte(prev+2) & 0xFF)));
            MemoryUtil.memPutByte(curr+3, (byte) ((MemoryUtil.memGetByte(curr+3) & 0xFF) + (MemoryUtil.memGetByte(prev+3) & 0xFF)));
            for (long i = curr + 4, j = prev + 4; i < count; i += 4, j += 4) {
                MemoryUtil.memPutByte(i+0, (byte) ((MemoryUtil.memGetByte(i+0) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-4) & 0xFF,
                        MemoryUtil.memGetByte(j+0) & 0xFF,
                        MemoryUtil.memGetByte(j-4) & 0xFF
                )));
                MemoryUtil.memPutByte(i+1, (byte) ((MemoryUtil.memGetByte(i+1) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-3) & 0xFF,
                        MemoryUtil.memGetByte(j+1) & 0xFF,
                        MemoryUtil.memGetByte(j-3) & 0xFF
                )));
                MemoryUtil.memPutByte(i+2, (byte) ((MemoryUtil.memGetByte(i+2) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-2) & 0xFF,
                        MemoryUtil.memGetByte(j+2) & 0xFF,
                        MemoryUtil.memGetByte(j-2) & 0xFF
                )));
                MemoryUtil.memPutByte(i+3, (byte) ((MemoryUtil.memGetByte(i+3) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-1) & 0xFF,
                        MemoryUtil.memGetByte(j+3) & 0xFF,
                        MemoryUtil.memGetByte(j-1) & 0xFF
                )));
            }
        } else if (bpp == 3) {
            long count = rowNative + row.length;
            MemoryUtil.memPutByte(curr+0, (byte) ((MemoryUtil.memGetByte(curr+0) & 0xFF) + (MemoryUtil.memGetByte(prev+0) & 0xFF)));
            MemoryUtil.memPutByte(curr+1, (byte) ((MemoryUtil.memGetByte(curr+1) & 0xFF) + (MemoryUtil.memGetByte(prev+1) & 0xFF)));
            MemoryUtil.memPutByte(curr+2, (byte) ((MemoryUtil.memGetByte(curr+2) & 0xFF) + (MemoryUtil.memGetByte(prev+2) & 0xFF)));
            for (long i = curr + 3, j = prev + 3; i < count; i += 3, j += 3) {
                MemoryUtil.memPutByte(i+0, (byte) ((MemoryUtil.memGetByte(i+0) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-3) & 0xFF,
                        MemoryUtil.memGetByte(j+0) & 0xFF,
                        MemoryUtil.memGetByte(j-3) & 0xFF
                )));
                MemoryUtil.memPutByte(i+1, (byte) ((MemoryUtil.memGetByte(i+1) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-2) & 0xFF,
                        MemoryUtil.memGetByte(j+1) & 0xFF,
                        MemoryUtil.memGetByte(j-2) & 0xFF
                )));
                MemoryUtil.memPutByte(i+2, (byte) ((MemoryUtil.memGetByte(i+2) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(i-1) & 0xFF,
                        MemoryUtil.memGetByte(j+2) & 0xFF,
                        MemoryUtil.memGetByte(j-1) & 0xFF
                )));
            }
        } else {
            int count = row.length;
            for (int i = 0; i < bpp; i++) {
                MemoryUtil.memPutByte(curr+i, (byte) ((MemoryUtil.memGetByte(curr+i) & 0xFF) + (MemoryUtil.memGetByte(prev+i) & 0xFF)));
            }
            for (int i = bpp; i < count; i++) {
                MemoryUtil.memPutByte(curr+i, (byte) ((MemoryUtil.memGetByte(curr+i) & 0xFF) + PNGFilter.paeth(
                        MemoryUtil.memGetByte(curr+i - bpp) & 0xFF,
                        MemoryUtil.memGetByte(prev+i) & 0xFF,
                        MemoryUtil.memGetByte(prev+i - bpp) & 0xFF
                )));
            }
        }
    }*/
}

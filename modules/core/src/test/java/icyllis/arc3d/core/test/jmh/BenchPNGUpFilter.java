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

import org.lwjgl.system.MemoryUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.util.concurrent.ThreadLocalRandom;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchPNGUpFilter {

    private static final Unsafe UNSAFE = getUnsafe();

    private static sun.misc.Unsafe getUnsafe() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
            throw new AssertionError("No MemoryUtil.UNSAFE", e);
        }
    }

    public byte[] row;
    public byte[] prevRow;
    public long rowNative;
    public long prevRowNative;

    @Setup
    public void setup() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
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
    public void decodeUp_Java() {
        byte[] curr = row;
        byte[] prev = prevRow;
        int count = row.length;
        for (int i = 0; i < count; i++) {
            curr[i] = (byte) ((curr[i] & 0xFF) + (prev[i] & 0xFF));
        }
    }

    @Benchmark
    public void decodeUp_Unsafe() {
        long curr = rowNative;
        long prev = prevRowNative;
        int count = row.length;
        for (int i = 0; i < count; i++) {
            UNSAFE.putByte(curr+i, (byte) ((UNSAFE.getByte(curr+i) & 0xFF) + (UNSAFE.getByte(prev+i) & 0xFF)));
        }
    }

    @Benchmark
    public void decodeUp_UnsafeUnroll() {
        long curr = rowNative;
        long prev = prevRowNative;
        long count = rowNative + row.length;
        for (long i = curr, j = prev; i < count; i += 4, j += 4) {
            MemoryUtil.memPutByte(i, (byte) ((MemoryUtil.memGetByte(i) & 0xFF) + (MemoryUtil.memGetByte(j) & 0xFF)));
            MemoryUtil.memPutByte(i+1, (byte) ((MemoryUtil.memGetByte(i+1) & 0xFF) + (MemoryUtil.memGetByte(j+1) & 0xFF)));
            MemoryUtil.memPutByte(i+2, (byte) ((MemoryUtil.memGetByte(i+2) & 0xFF) + (MemoryUtil.memGetByte(j+2) & 0xFF)));
            MemoryUtil.memPutByte(i+3, (byte) ((MemoryUtil.memGetByte(i+3) & 0xFF) + (MemoryUtil.memGetByte(j+3) & 0xFF)));
        }
    }
}

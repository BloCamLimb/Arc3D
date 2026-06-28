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

import icyllis.arc3d.core.MathUtil;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
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

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;

import static icyllis.arc3d.core.image.PNG.FILTER_TYPE_NONE;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchSAD {

    public byte[] input;

    @Setup
    public void setup() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        input = new byte[6144];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) r.nextInt(256);
        }
    }

    private static final VectorSpecies<Byte> SPECIES_BYTE = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> SPECIES_INT = SPECIES_BYTE.withLanes(int.class);

    @Benchmark
    public long normalLoop() {
        long total = 0L;

        byte[] arr = input;

        for (byte b : arr) {
            total += Math.abs(b);
        }

        return total;
    }

    @Benchmark
    public long vectorized() {
        byte[] arr = input;

        long totalSum = 0L;
        int i = 0;
        int speciesLength = SPECIES_BYTE.length();
        int upperBound = SPECIES_BYTE.loopBound(arr.length);

        IntVector acc0 = IntVector.zero(SPECIES_INT);
        IntVector acc1 = IntVector.zero(SPECIES_INT);
        IntVector acc2 = IntVector.zero(SPECIES_INT);
        IntVector acc3 = IntVector.zero(SPECIES_INT);

        int blockCount = 0;

        for (; i < upperBound; i += speciesLength) {
            ByteVector bv = ByteVector.fromArray(SPECIES_BYTE, arr, i);

            IntVector iv0 = ((IntVector) bv.convert(VectorOperators.B2I, 0)).abs();
            IntVector iv1 = ((IntVector) bv.convert(VectorOperators.B2I, 1)).abs();
            IntVector iv2 = ((IntVector) bv.convert(VectorOperators.B2I, 2)).abs();
            IntVector iv3 = ((IntVector) bv.convert(VectorOperators.B2I, 3)).abs();

            acc0 = acc0.add(iv0);
            acc1 = acc1.add(iv1);
            acc2 = acc2.add(iv2);
            acc3 = acc3.add(iv3);

            blockCount += speciesLength;

            if (blockCount >= 16_000_000) {
                totalSum += acc0.reduceLanes(VectorOperators.ADD);
                totalSum += acc1.reduceLanes(VectorOperators.ADD);
                totalSum += acc2.reduceLanes(VectorOperators.ADD);
                totalSum += acc3.reduceLanes(VectorOperators.ADD);

                acc0 = IntVector.zero(SPECIES_INT);
                acc1 = IntVector.zero(SPECIES_INT);
                acc2 = IntVector.zero(SPECIES_INT);
                acc3 = IntVector.zero(SPECIES_INT);
                blockCount = 0;
            }
        }

        if (blockCount > 0) {
            totalSum += acc0.reduceLanes(VectorOperators.ADD);
            totalSum += acc1.reduceLanes(VectorOperators.ADD);
            totalSum += acc2.reduceLanes(VectorOperators.ADD);
            totalSum += acc3.reduceLanes(VectorOperators.ADD);
        }

        for (; i < arr.length; i++) {
            totalSum += Math.abs(arr[i]);
        }

        return totalSum;
    }

    @Benchmark
    public long entropy() {
        int[] counts = new int[256];
        byte[] arr = input;
        for (int ii = 0; ii < arr.length; ii++) {
            counts[arr[ii]&0xFF]++;
        }
        long score = 0;
        for (var x : counts) {
            if (x != 0) {
                score += ilog2i(x);
            }
        }
        return score;
    }

    private static long ilog2i(int i) {
        long log = MathUtil.floorLog2(i);
        return i * log + ((i - (1L << log)) << 1L);
    }

    @Benchmark
    public long big_entropy() {
        int[] counts = new int[65536];
        byte[] arr = input;
        for (int ii = 1; ii < arr.length; ii++) {
            int bigram = (((arr[ii-1] & 0xFF) << 8) | (arr[ii] & 0xFF));
            counts[bigram]++;
        }
        long score = 0;
        for (var x : counts) {
            if (x != 0) {
                score += ilog2i(x);
            }
        }
        return score;
    }

    @Benchmark
    public long brute() {
        ByteBuffer td = ByteBuffer.allocate((int) MathUtil.compressBound(input.length));

        Deflater def = new Deflater();
        def.setInput(input);
        def.finish();
        while (!def.finished()) {
            def.deflate(td);
        }
        def.end();
        return td.position();
    }
}

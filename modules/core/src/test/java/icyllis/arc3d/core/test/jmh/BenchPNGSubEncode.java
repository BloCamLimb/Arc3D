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
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ThreadLocalRandom;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class BenchPNGSubEncode {

    //Benchmark                            Mode  Cnt         Score         Error  Units
    //BenchPNGSubEncode.encodeSub_Java    thrpt    5    612435.355 ±   33571.015  ops/s
    //BenchPNGSubEncode.encodeSub_Vector  thrpt    5  13161266.370 ±  609053.218  ops/s
    //BenchPNGSubEncode.encodeUp_Java     thrpt    5  15195663.439 ± 1954616.386  ops/s

    public byte[] row;
    public byte[] prevRow;
    public byte[] tmp;
    public int bpp;

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
        bpp = 4;
        tmp = new byte[row.length];
    }

    @Benchmark
    public void encodeSub_Java() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] dest = tmp;
        int count = row.length;

        for (int i = bpp; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (curr[i - bpp] & 0xFF));
        }
    }

    private static final VectorSpecies<Byte> B_A;

    static {
        var prefer = ByteVector.SPECIES_PREFERRED;
        if (prefer.vectorBitSize() > 256) {
            B_A = ByteVector.SPECIES_256;
        } else {
            B_A = prefer;
        }
    }

    @Benchmark
    public void encodeSub_Vector() {
        int bpp = this.bpp;
        byte[] curr = row;
        byte[] dest = tmp;
        int count = row.length - 32;

        System.arraycopy(curr, 0, dest, 0, bpp);

        for (int i = bpp; i < count; i += B_A.length()) {
            ByteVector left  = ByteVector.fromArray(B_A, curr, i - bpp);
            ByteVector right = ByteVector.fromArray(B_A, curr, i);
            right.sub(left).intoArray(dest, i);
        }
    }

    @Benchmark
    public void encodeUp_Java() {
        byte[] curr = row;
        byte[] prev = prevRow;
        byte[] dest = tmp;
        int count = curr.length;
        for (int i = 0; i < count; i++) {
            dest[i] = (byte) ((curr[i] & 0xFF) - (prev[i] & 0xFF));
        }
    }
}

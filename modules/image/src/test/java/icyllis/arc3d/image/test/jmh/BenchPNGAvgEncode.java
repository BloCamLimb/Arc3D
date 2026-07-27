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

package icyllis.arc3d.image.test.jmh;

import icyllis.arc3d.core.image.Predictor;
import icyllis.arc3d.core.image.PredictorIncubatorVector;
import icyllis.arc3d.core.image.PredictorStandard;
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
public class BenchPNGAvgEncode {

    // X64 AVX2
    //Benchmark                              Mode  Cnt        Score        Error  Units
    //BenchPNGAvgEncode.encodeAvg_Java      thrpt    5   453103.706 ±  26244.256  ops/s
    //BenchPNGAvgEncode.encodeAvg_Vector    thrpt    5  3628777.678 ± 286303.452  ops/s
    //BenchPNGAvgEncode.encodePaeth_Java    thrpt    5   103805.526 ±   4025.853  ops/s
    //BenchPNGAvgEncode.encodePaeth_Vector  thrpt    5  1152099.851 ±  18259.260  ops/s

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

    public static final PredictorStandard STANDARD = new PredictorStandard();
    public static final PredictorIncubatorVector VECTOR = new PredictorIncubatorVector();

    @Benchmark
    public void encodeAvg_Java() {
        byte[] curr = row;
        byte[] prev = prevRow;
        byte[] dest = tmp;
        int count = curr.length;
        STANDARD.encodeAverage(curr, prev, dest, count, bpp);
    }

    @Benchmark
    public void encodeAvg_Vector() {
        byte[] curr = row;
        byte[] prev = prevRow;
        byte[] dest = tmp;
        int count = curr.length - Predictor.HEADROOM;
        VECTOR.encodeAverage(curr, prev, dest, count, bpp);
    }

    @Benchmark
    public void encodePaeth_Java() {
        byte[] curr = row;
        byte[] prev = prevRow;
        byte[] dest = tmp;
        int count = curr.length;
        STANDARD.encodePaeth(curr, prev, dest, count, bpp);
    }

    @Benchmark
    public void encodePaeth_Vector() {
        byte[] curr = row;
        byte[] prev = prevRow;
        byte[] dest = tmp;
        int count = curr.length - Predictor.HEADROOM;
        VECTOR.encodePaeth(curr, prev, dest, count, bpp);
    }
}

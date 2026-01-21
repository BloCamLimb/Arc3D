/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.sketch.test;

import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.PathBuilder;
import icyllis.arc3d.sketch.PathStroker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.awt.BasicStroke;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class TestBenchmark {

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .shouldFailOnError(true).shouldDoGC(true)
                .jvmArgs("-XX:+UseFMA")
                .build())
                .run();
    }

    /*private final Matrix4 mMatrix = new Matrix4();
    {
        mMatrix.preRotate(MathUtil.PI_O_3, MathUtil.PI_O_6, MathUtil.PI_O_4);
    }
    private final long mData = MemoryUtil.nmemAlignedAllocChecked(8, 64);*/

    /*public static final Pixmap SRC_PIXMAP;
    public static final Pixmap DST_PIXMAP;*/
    public static final Path SRC_PATH;

    static {
        /*int[] x = {0}, y = {0}, channels = {0};
        var imgData = STBImage.stbi_load(
                "F:/123459857_p0.png",
                x, y, channels, 4
        );
        assert imgData != null;
        SRC_PIXMAP = new Pixmap(
                ImageInfo.make(x[0], y[0], ColorInfo.CT_RGBA_8888, ColorInfo.AT_UNPREMUL, null),
                null,
                MemoryUtil.memAddress(imgData),
                4 * x[0]
        );
        var newInfo = ImageInfo.make(x[0], y[0], ColorInfo.CT_BGR_565, ColorInfo.AT_UNPREMUL, null);
        long newPixels = MemoryUtil.nmemAlloc(newInfo.computeMinByteSize());
        assert newPixels != 0;
        DST_PIXMAP = new Pixmap(
                newInfo, null, newPixels, newInfo.minRowBytes()
        );*/
        PathBuilder src = new PathBuilder()
                .moveTo(100, 120)
                .lineTo(130, 160)
                .lineTo(100, 200)
                .lineTo(180, 140)
                .lineTo(170, 130)
                .lineTo(170, 120)
                .cubicTo(160, 130, 120, 100, 190, 60);
        SRC_PATH = src.build();
    }

    /*@Benchmark
    public void uploadMethod1() {
        boolean res = SRC_PIXMAP.readPixels(DST_PIXMAP, 0, 0);
        assert res;
    }*/

    @Benchmark
    public void arcStroker(Blackhole bh) {
        PathBuilder dst = new PathBuilder();
        PathStroker stroker = new PathStroker();
        stroker.init(
                dst, 10 * 0.5f, Paint.CAP_ROUND, Paint.JOIN_ROUND, 4, 1
        );
        SRC_PATH.forEach(stroker);
        bh.consume(dst);
    }

    @Benchmark
    public void marlinStroker(Blackhole bh) {
        BasicStroke stroker = new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 4);
        var shape = stroker.createStrokedShape(SRC_PATH);
        bh.consume(shape);
    }

    @Benchmark
    public void marlinStrokerConvert(Blackhole bh) {
        PathBuilder dst = new PathBuilder();
        BasicStroke stroker = new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 4);
        var shape = stroker.createStrokedShape(SRC_PATH);
        dst.addShape(shape, null);
        bh.consume(dst);
    }
}

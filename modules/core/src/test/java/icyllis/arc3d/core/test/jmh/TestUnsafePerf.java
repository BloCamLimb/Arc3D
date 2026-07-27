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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import sun.misc.Unsafe;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryUtil.memPutFloat;

@Fork(1)
@Threads(2)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class TestUnsafePerf {

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

    /*private static final VarHandle VH_JAVA_FLOAT;

    static {
        try {
            var lookup = MethodHandles.lookup();

            var ofAddress = lookup
                    .findStatic(MemorySegment.class, "ofAddress", MethodType.methodType(MemorySegment.class, long.class));

            var reinterpret = lookup
                    .findVirtual(MemorySegment.class, "reinterpret", MethodType.methodType(MemorySegment.class, long.class));

            VH_JAVA_FLOAT = createMemoryAccessVH(ValueLayout.JAVA_FLOAT, ofAddress, reinterpret).withInvokeExactBehavior();
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static VarHandle createMemoryAccessVH(ValueLayout layout, MethodHandle ofAddress, MethodHandle reinterpret) {
        var vh = layout.varHandle();

        vh = MethodHandles.insertCoordinates(vh, 1, 0L);
        vh = MethodHandles.filterCoordinates(vh, 0, MethodHandles.filterReturnValue(
                ofAddress,
                MethodHandles.insertArguments(reinterpret, 1, layout.byteSize())
        ));

        return vh;
    }

    public static void memPutFloat(long ptr, float value)   { VH_JAVA_FLOAT.set(ptr, value); }*/

    public static void main(String[] args) {
        var s = new TestUnsafePerf();
    }

    public volatile long addr;
    //public volatile MemorySegment seg;
    public volatile ByteBuffer directbuf;

    public volatile DataProvider vhdata;
    public volatile DataProvider unsafedata;
    public volatile DataProvider segdata;
    public volatile boolean branc;

    public volatile long[] arr;

    @Setup
    public void setup() {
        addr = MemoryUtil.nmemAllocChecked(88);
        //seg = MemorySegment.ofAddress(addr).reinterpret(88);
        directbuf = ByteBuffer.allocateDirect(88);
        vhdata = new DataProviderVH();
        unsafedata = new DataProviderUnsafe();
        //segdata = new DataProviderSegment();
        branc = Math.random() > 0.5;
        arr = new long[1024];
    }

    public interface DataProvider {

        void get(long p);
    }

    static class DataProviderVH implements DataProvider {

        float m1;
        float m2;
        float m3;
        float m4;
        float m5;
        float m6;

        public DataProviderVH() {
            m1 = (float) Math.random();
            m2 = (float) Math.random();
            m3 = (float) Math.random();
            m4 = (float) Math.random();
            m5 = (float) Math.random();
            m6 = (float) Math.random();
        }

        @Override
        public void get(long p) {
            memPutFloat(p, m1);
            memPutFloat(p+4, m2);
            memPutFloat(p+8, m3);
            memPutFloat(p+12, m4);
            memPutFloat(p+16, m5);
            memPutFloat(p+20, m6);
            memPutFloat(p+24, m1);
            memPutFloat(p+28, m2);
            memPutFloat(p+32, m3);
        }
    }

    static class DataProviderUnsafe implements DataProvider {

        float m1;
        float m2;
        float m3;
        float m4;
        float m5;
        float m6;

        public DataProviderUnsafe() {
            m1 = (float) Math.random();
            m2 = (float) Math.random();
            m3 = (float) Math.random();
            m4 = (float) Math.random();
            m5 = (float) Math.random();
            m6 = (float) Math.random();
        }

        @Override
        public void get(long p) {
            UNSAFE.putFloat(p, m1);
            UNSAFE.putFloat(p+4, m2);
            UNSAFE.putFloat(p+8, m3);
            UNSAFE.putFloat(p+12, m4);
            UNSAFE.putFloat(p+16, m5);
            UNSAFE.putFloat(p+20, m6);
            UNSAFE.putFloat(p+24, m1);
            UNSAFE.putFloat(p+28, m2);
            UNSAFE.putFloat(p+32, m3);
        }
    }

    /*static class DataProviderSegment implements DataProvider {

        float m1;
        float m2;
        float m3;
        float m4;
        float m5;
        float m6;

        public DataProviderSegment() {
            m1 = (float) Math.random();
            m2 = (float) Math.random();
            m3 = (float) Math.random();
            m4 = (float) Math.random();
            m5 = (float) Math.random();
            m6 = (float) Math.random();
        }

        @Override
        public void get(long p) {
            var seg = MemorySegment.ofAddress(p).reinterpret(36);
            seg.set(ValueLayout.JAVA_FLOAT, 0, m1);
            seg.set(ValueLayout.JAVA_FLOAT, 4, m2);
            seg.set(ValueLayout.JAVA_FLOAT, 8, m3);
            seg.set(ValueLayout.JAVA_FLOAT, 12, m4);
            seg.set(ValueLayout.JAVA_FLOAT, 16, m5);
            seg.set(ValueLayout.JAVA_FLOAT, 20, m6);
            seg.set(ValueLayout.JAVA_FLOAT, 24, m1);
            seg.set(ValueLayout.JAVA_FLOAT, 28, m2);
            seg.set(ValueLayout.JAVA_FLOAT, 32, m3);
        }

        public void get(MemorySegment seg, int off) {
            seg.set(ValueLayout.JAVA_FLOAT, off + 0, m1);
            seg.set(ValueLayout.JAVA_FLOAT, off + 4, m2);
            seg.set(ValueLayout.JAVA_FLOAT, off + 8, m3);
            seg.set(ValueLayout.JAVA_FLOAT, off + 12, m4);
            seg.set(ValueLayout.JAVA_FLOAT, off + 16, m5);
            seg.set(ValueLayout.JAVA_FLOAT, off + 20, m6);
            seg.set(ValueLayout.JAVA_FLOAT, off + 24, m1);
            seg.set(ValueLayout.JAVA_FLOAT, off + 28, m2);
            seg.set(ValueLayout.JAVA_FLOAT, off + 32, m3);
        }
    }*/

    @Benchmark
    public void clear_loop() {
        Arrays.fill(arr, 0L);
    }

    @Benchmark
    public void clear_unsafe() {
        UNSAFE.setMemory(arr, Unsafe.ARRAY_LONG_BASE_OFFSET, arr.length * 8L, (byte) 0);
    }

    @Benchmark
    public void memoryutil(Blackhole bh) {
        long addr = this.addr;
        memPutFloat(addr, 60);
        memPutFloat(addr+8, 70);
        memPutFloat(addr+16, 80);
        memPutFloat(addr+24, 90);
        memPutFloat(addr+32, 1000);
        memPutFloat(addr+40, 20000);
        vhdata.get(addr+44);
    }

    @Benchmark
    public void unsafe(Blackhole bh) {
        long addr = this.addr;
        UNSAFE.putFloat(addr, 60);
        UNSAFE.putFloat(addr+8, 70);
        UNSAFE.putFloat(addr+16, 80);
        UNSAFE.putFloat(addr+24, 90);
        UNSAFE.putFloat(addr+32, 1000);
        UNSAFE.putFloat(addr+40, 20000);
        unsafedata.get(addr+44);
    }

    /*@Benchmark
    public void segment(Blackhole bh) {
        var seg = MemorySegment.ofAddress(addr).reinterpret(88);
        seg.set(ValueLayout.JAVA_FLOAT, 0, 60);
        seg.set(ValueLayout.JAVA_FLOAT, 8, 70);
        seg.set(ValueLayout.JAVA_FLOAT, 16, 80);
        seg.set(ValueLayout.JAVA_FLOAT, 24, 90);
        seg.set(ValueLayout.JAVA_FLOAT, 32, 1000);
        seg.set(ValueLayout.JAVA_FLOAT, 40, 20000);
        this.segdata.get(addr+44);
    }*/

    /*@Benchmark
    public void directbuffer(Blackhole bh) {
        var buf = directbuf;
        buf.putFloat(0, 60);
        buf.putFloat(8, 70);
        buf.putFloat(16, 80);
        buf.putFloat(24, 90);
        buf.putFloat(32, 1000);
        buf.putFloat(40, 20000);
        this.segdata.get(addr+44);
    }*/
}

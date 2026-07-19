/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.core.test;

import icyllis.arc3d.core.PixelUtils;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.jni.JNINativeInterface;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.*;
import sun.misc.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.libc.LibCString.*;

@State(Scope.Benchmark)
public class TestMemCpy {

    private static final Unsafe UNSAFE = getUnsafe();

    private static sun.misc.Unsafe getUnsafe() {
        java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            if (!field.getType().equals(sun.misc.Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers))) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (sun.misc.Unsafe) field.get(null);
            } catch (Exception e) {
                throw new UnsupportedOperationException("No sun.misc.Unsafe", e);
            }
        }

        throw new UnsupportedOperationException("No sun.misc.Unsafe");
    }

    //                                                              lower is better
    // Benchmark                                (length)  Mode  Cnt   Score    Error  Units
    // TestMemCpy.array_baseline                      32  avgt    3   3.320 ±  0.179  ns/op
    // TestMemCpy.array_baseline                     160  avgt    3   3.796 ±  0.296  ns/op
    // TestMemCpy.array_baseline                     256  avgt    3   5.236 ±  8.546  ns/op
    // TestMemCpy.array_baseline                    1024  avgt    3  19.374 ± 61.547  ns/op
    // TestMemCpy.array_to_offheap_base_region        32  avgt    3  23.368 ±  2.762  ns/op
    // TestMemCpy.array_to_offheap_base_region       160  avgt    3  27.423 ±  2.958  ns/op
    // TestMemCpy.array_to_offheap_base_region       256  avgt    3  28.233 ±  2.385  ns/op
    // TestMemCpy.array_to_offheap_base_region      1024  avgt    3  30.137 ± 12.089  ns/op
    // TestMemCpy.array_to_offheap_baseline           32  avgt    3   9.017 ±  0.445  ns/op
    // TestMemCpy.array_to_offheap_baseline          160  avgt    3  10.124 ±  0.582  ns/op
    // TestMemCpy.array_to_offheap_baseline          256  avgt    3  11.016 ±  0.364  ns/op
    // TestMemCpy.array_to_offheap_baseline         1024  avgt    3  15.842 ±  8.593  ns/op
    // TestMemCpy.offheap_LWJGL                       32  avgt    3   2.667 ±  0.088  ns/op
    // TestMemCpy.offheap_LWJGL                      160  avgt    3  10.137 ±  0.747  ns/op
    // TestMemCpy.offheap_LWJGL                      256  avgt    3  10.948 ±  0.608  ns/op
    // TestMemCpy.offheap_LWJGL                     1024  avgt    3  16.164 ±  1.226  ns/op
    // TestMemCpy.offheap_baseline                    32  avgt    3   9.304 ±  0.934  ns/op
    // TestMemCpy.offheap_baseline                   160  avgt    3  10.039 ±  0.859  ns/op
    // TestMemCpy.offheap_baseline                   256  avgt    3  11.081 ±  0.517  ns/op
    // TestMemCpy.offheap_baseline                  1024  avgt    3  16.533 ±  1.473  ns/op
    // TestMemCpy.offheap_java                        32  avgt    3   1.935 ±  0.067  ns/op
    // TestMemCpy.offheap_java                       160  avgt    3   6.704 ±  0.366  ns/op
    // TestMemCpy.offheap_java                       256  avgt    3   9.278 ±  0.023  ns/op
    // TestMemCpy.offheap_java                      1024  avgt    3  31.557 ± 33.803  ns/op
    // TestMemCpy.offheap_libc                        32  avgt    3   7.455 ±  0.613  ns/op
    // TestMemCpy.offheap_libc                       160  avgt    3   8.135 ±  0.481  ns/op
    // TestMemCpy.offheap_libc                       256  avgt    3   8.861 ±  1.035  ns/op
    // TestMemCpy.offheap_libc                      1024  avgt    3  13.589 ±  1.059  ns/op



    // GraalVM 25, LWJGL 3.4.1
    //TestMemCpy.array_baseline                      32  avgt    3   3.899 ±  0.031  ns/op
    //TestMemCpy.array_baseline                     160  avgt    3   6.973 ±  0.449  ns/op
    //TestMemCpy.array_baseline                     256  avgt    3   8.980 ±  1.175  ns/op
    //TestMemCpy.array_baseline                    1024  avgt    3  25.830 ± 52.945  ns/op
    //TestMemCpy.array_to_offheap_base_region        32  avgt    3  34.080 ±  1.897  ns/op
    //TestMemCpy.array_to_offheap_base_region       160  avgt    3  41.371 ±  1.756  ns/op
    //TestMemCpy.array_to_offheap_base_region       256  avgt    3  41.624 ±  4.235  ns/op
    //TestMemCpy.array_to_offheap_base_region      1024  avgt    3  43.847 ±  1.398  ns/op
    //TestMemCpy.array_to_offheap_baseline           32  avgt    3  15.300 ±  0.772  ns/op
    //TestMemCpy.array_to_offheap_baseline          160  avgt    3  16.819 ±  1.377  ns/op
    //TestMemCpy.array_to_offheap_baseline          256  avgt    3  17.970 ±  1.330  ns/op
    //TestMemCpy.array_to_offheap_baseline         1024  avgt    3  25.406 ±  1.208  ns/op
    //TestMemCpy.offheap_LWJGL                       32  avgt    3   3.898 ±  0.297  ns/op
    //TestMemCpy.offheap_LWJGL                      160  avgt    3  10.208 ±  0.695  ns/op
    //TestMemCpy.offheap_LWJGL                      256  avgt    3  11.330 ±  2.294  ns/op
    //TestMemCpy.offheap_LWJGL                     1024  avgt    3  20.115 ± 72.131  ns/op
    //TestMemCpy.offheap_baseline                    32  avgt    3  15.926 ±  4.517  ns/op
    //TestMemCpy.offheap_baseline                   160  avgt    3  17.217 ±  1.157  ns/op
    //TestMemCpy.offheap_baseline                   256  avgt    3  18.531 ±  0.612  ns/op
    //TestMemCpy.offheap_baseline                  1024  avgt    3  27.487 ± 33.055  ns/op
    //TestMemCpy.offheap_java                        32  avgt    3   3.431 ±  2.555  ns/op
    //TestMemCpy.offheap_java                       160  avgt    3   8.593 ±  2.218  ns/op
    //TestMemCpy.offheap_java                       256  avgt    3   8.128 ±  1.660  ns/op
    //TestMemCpy.offheap_java                      1024  avgt    3  19.706 ±  4.671  ns/op
    //TestMemCpy.offheap_libc                        32  avgt    3  11.473 ±  0.285  ns/op
    //TestMemCpy.offheap_libc                       160  avgt    3  13.156 ±  7.239  ns/op
    //TestMemCpy.offheap_libc                       256  avgt    3  14.187 ±  0.817  ns/op
    //TestMemCpy.offheap_libc                      1024  avgt    3  21.129 ± 10.903  ns/op

    // Liberica 25, LWJGL 3.4.2
    //Benchmark                                (length)  Mode  Cnt   Score     Error  Units
    //TestMemCpy.array_baseline                      32  avgt    3   4.937 ±   0.376  ns/op
    //TestMemCpy.array_baseline                     160  avgt    3   5.690 ±   0.673  ns/op
    //TestMemCpy.array_baseline                     256  avgt    3   9.396 ±  84.788  ns/op
    //TestMemCpy.array_baseline                    1024  avgt    3  17.075 ± 102.864  ns/op
    //TestMemCpy.array_to_offheap_LWJGL              32  avgt    3   7.834 ±   0.732  ns/op
    //TestMemCpy.array_to_offheap_LWJGL             160  avgt    3   9.430 ±   1.297  ns/op
    //TestMemCpy.array_to_offheap_LWJGL             256  avgt    3  10.680 ±   1.052  ns/op
    //TestMemCpy.array_to_offheap_LWJGL            1024  avgt    3  17.264 ±   3.698  ns/op
    //TestMemCpy.array_to_offheap_base_region        32  avgt    3  34.396 ±   2.219  ns/op
    //TestMemCpy.array_to_offheap_base_region       160  avgt    3  40.235 ±   2.852  ns/op
    //TestMemCpy.array_to_offheap_base_region       256  avgt    3  41.866 ±   2.174  ns/op
    //TestMemCpy.array_to_offheap_base_region      1024  avgt    3  44.621 ±   1.582  ns/op
    //TestMemCpy.array_to_offheap_baseline           32  avgt    3  13.716 ±   3.280  ns/op
    //TestMemCpy.array_to_offheap_baseline          160  avgt    3  14.912 ±   0.964  ns/op
    //TestMemCpy.array_to_offheap_baseline          256  avgt    3  16.470 ±   0.920  ns/op
    //TestMemCpy.array_to_offheap_baseline         1024  avgt    3  26.020 ±  10.412  ns/op
    //TestMemCpy.array_to_offheap_bytebuffer         32  avgt    3  14.091 ±   3.961  ns/op
    //TestMemCpy.array_to_offheap_bytebuffer        160  avgt    3  15.218 ±   1.599  ns/op
    //TestMemCpy.array_to_offheap_bytebuffer        256  avgt    3  16.896 ±   0.595  ns/op
    //TestMemCpy.array_to_offheap_bytebuffer       1024  avgt    3  23.783 ±   0.867  ns/op
    //TestMemCpy.array_unsafe_baseline               32  avgt    3  13.884 ±   2.156  ns/op
    //TestMemCpy.array_unsafe_baseline              160  avgt    3  15.071 ±   0.931  ns/op
    //TestMemCpy.array_unsafe_baseline              256  avgt    3  17.417 ±   2.925  ns/op
    //TestMemCpy.array_unsafe_baseline             1024  avgt    3  32.406 ±   3.704  ns/op
    //TestMemCpy.offheap_LWJGL                       32  avgt    3   7.643 ±   0.328  ns/op
    //TestMemCpy.offheap_LWJGL                      160  avgt    3   9.083 ±   0.133  ns/op
    //TestMemCpy.offheap_LWJGL                      256  avgt    3   9.603 ±   1.008  ns/op
    //TestMemCpy.offheap_LWJGL                     1024  avgt    3  16.248 ±   3.812  ns/op
    //TestMemCpy.offheap_baseline                    32  avgt    3  13.757 ±   2.287  ns/op
    //TestMemCpy.offheap_baseline                   160  avgt    3  14.998 ±   1.266  ns/op
    //TestMemCpy.offheap_baseline                   256  avgt    3  17.005 ±   1.275  ns/op
    //TestMemCpy.offheap_baseline                  1024  avgt    3  23.808 ±   1.310  ns/op
    //TestMemCpy.offheap_java                        32  avgt    3   3.687 ±   0.463  ns/op
    //TestMemCpy.offheap_java                       160  avgt    3   9.981 ±   0.537  ns/op
    //TestMemCpy.offheap_java                       256  avgt    3  13.872 ±   1.049  ns/op
    //TestMemCpy.offheap_java                      1024  avgt    3  45.103 ±   4.528  ns/op
    //TestMemCpy.offheap_libc                        32  avgt    3  11.571 ±   1.709  ns/op
    //TestMemCpy.offheap_libc                       160  avgt    3  12.383 ±   6.044  ns/op
    //TestMemCpy.offheap_libc                       256  avgt    3  13.141 ±   0.452  ns/op
    //TestMemCpy.offheap_libc                      1024  avgt    3  20.102 ±   1.481  ns/op
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TestMemCpy.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(1))
                .warmupTime(TimeValue.seconds(1))
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .shouldFailOnError(true).shouldDoGC(true)
                .jvmArgs("-XX:+UseZGC", "-XX:+ZGenerational")
                .build();

        new Runner(opt).run();
    }

    private static final int BUFFER_SIZE = 128 * 1024;

    private static final long f = nmemAlloc(BUFFER_SIZE);
    private static final long t = nmemAlloc(BUFFER_SIZE);

    private static final byte[] a = new byte[BUFFER_SIZE];
    private static final byte[] b = new byte[BUFFER_SIZE];

    @Param({"32", "160", "256", "1024"})
    public int length;

    @Benchmark
    public void offheap_LWJGL() {
        memCopy(f, t, length);
    }

    @Benchmark
    public void offheap_baseline() {
        UNSAFE.copyMemory(null, f, null, t, length);
    }

    @Benchmark
    public void array_to_offheap_baseline() {
        UNSAFE.copyMemory(a, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, t, length);
    }

    @Benchmark
    public void array_to_offheap_base_region() {
        JNINativeInterface.nGetByteArrayRegion(a, 0, length, t);
    }

    @Benchmark
    public void array_to_offheap_bytebuffer() {
        ByteBuffer src = ByteBuffer.wrap(a, 0, length)
                .order(ByteOrder.nativeOrder());
        ByteBuffer dst = MemoryUtil.memByteBuffer(t, length);
        dst.put(src);
    }

    @Benchmark
    public void array_to_offheap_LWJGL() {
        MemoryUtil.memCopy(a, t, 0, length);
    }

    @Benchmark
    public void offheap_java() {
        memCopyAligned(f, t, length);
    }

    @Benchmark
    public void offheap_libc() {
        nmemcpy(t, f, length);
    }

    @Benchmark
    public void array_baseline() {
        System.arraycopy(a, 0, b, 0, length);
    }

    @Benchmark
    public void array_unsafe_baseline() {
        UNSAFE.copyMemory(a, Unsafe.ARRAY_BYTE_BASE_OFFSET, b, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
    }

    private static void memCopyAligned(long src, long dst, int bytes) {
        int aligned = bytes & ~7;

        // Aligned body
        for (int i = 0; i < aligned; i += 8) {
            UNSAFE.putLong(null, dst + i, UNSAFE.getLong(null, src + i));
        }

        // Unaligned tail
        for (int i = aligned; i < bytes; i++) {
            UNSAFE.putByte(null, dst + i, UNSAFE.getByte(null, src + i));
        }
    }
}

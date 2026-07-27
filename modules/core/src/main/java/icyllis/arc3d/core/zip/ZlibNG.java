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

package icyllis.arc3d.core.zip;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;

import static org.lwjgl.system.APIUtil.apiGetFunctionAddress;

/**
 * Native bindings to zlib-ng.
 */
public class ZlibNG {

    private static final SharedLibrary ZLIB_NG;

    static {
        String name = Platform.get() == Platform.WINDOWS ? "zlib-ng2" : "z-ng";
        SharedLibrary lib = null;
        try {
            lib = Library.loadNative(ZlibNG.class, "org.lwjgl", name);
        } catch (UnsatisfiedLinkError e) {
            APIUtil.apiLog(e.toString());
        }
        ZLIB_NG = lib;
    }

    public static final class Functions {

        private Functions() {
        }

        /**
         * Function address.
         */
        public static final long
                zng_inflateInit2 = apiGetFunctionAddress(ZLIB_NG, "zng_inflateInit2"),
                zng_inflate = apiGetFunctionAddress(ZLIB_NG, "zng_inflate"),
                zng_inflateEnd = apiGetFunctionAddress(ZLIB_NG, "zng_inflateEnd"),
                zng_inflateReset = apiGetFunctionAddress(ZLIB_NG, "zng_inflateReset");
    }

    public static @Nullable SharedLibrary getLibrary() {
        return ZLIB_NG;
    }

    public static int zng_inflateInit2(long strm, int windowBits) {
        long __functionAddress = Functions.zng_inflateInit2;
        return JNI.invokePI(strm, windowBits, __functionAddress);
    }

    public static int zng_inflate(long strm, int flush) {
        long __functionAddress = Functions.zng_inflate;
        return JNI.invokePI(strm, flush, __functionAddress);
    }

    public static int zng_inflateEnd(long strm) {
        long __functionAddress = Functions.zng_inflateEnd;
        return JNI.invokePI(strm, __functionAddress);
    }

    public static int zng_inflateReset(long strm) {
        long __functionAddress = Functions.zng_inflateReset;
        return JNI.invokePI(strm, __functionAddress);
    }
}

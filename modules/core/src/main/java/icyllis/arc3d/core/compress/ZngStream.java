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

package icyllis.arc3d.core.compress;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

/**
 * typedef struct zng_stream_s
 */
public class ZngStream extends Struct<@NonNull ZngStream> {

    public static final int SIZEOF;
    public static final int ALIGNOF;
    public static final int NEXT_IN;
    public static final int AVAIL_IN;
    public static final int TOTAL_IN;
    public static final int NEXT_OUT;
    public static final int AVAIL_OUT;
    public static final int TOTAL_OUT;
    public static final int MSG;
    public static final int STATE;
    public static final int ZALLOC;
    public static final int ZFREE;
    public static final int OPAQUE;
    public static final int DATA_TYPE;
    public static final int ADLER;
    public static final int RESERVED;

    static {
        Layout layout = __struct(
                __member(POINTER_SIZE),
                __member(4),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(4),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(POINTER_SIZE),
                __member(4),
                __member(4),
                __member(CLONG_SIZE)
        );
        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();
        NEXT_IN = layout.offsetof(0);
        AVAIL_IN = layout.offsetof(1);
        TOTAL_IN = layout.offsetof(2);
        NEXT_OUT = layout.offsetof(3);
        AVAIL_OUT = layout.offsetof(4);
        TOTAL_OUT = layout.offsetof(5);
        MSG = layout.offsetof(6);
        STATE = layout.offsetof(7);
        ZALLOC = layout.offsetof(8);
        ZFREE = layout.offsetof(9);
        OPAQUE = layout.offsetof(10);
        DATA_TYPE = layout.offsetof(11);
        ADLER = layout.offsetof(12);
        RESERVED = layout.offsetof(13);
    }

    /**
     * Creates a struct instance at the specified address.
     *
     * @param address   the struct memory address
     * @param container an optional container buffer, to be referenced strongly by the struct instance.
     */
    protected ZngStream(long address, @Nullable ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected ZngStream create(long address, @Nullable ByteBuffer container) {
        return new ZngStream(address, container);
    }

    public ZngStream(ByteBuffer container) {
        super(MemoryUtil.memAddress(container), __checkContainer(container, SIZEOF));
    }

    @Override
    public int sizeof() {
        return SIZEOF;
    }

    public static ZngStream malloc() {
        return new ZngStream(MemoryUtil.nmemAllocChecked(SIZEOF), null);
    }

    public static ZngStream calloc() {
        return new ZngStream(MemoryUtil.nmemCallocChecked(1, SIZEOF), null);
    }

    public long next_in() {
        return MemoryUtil.memGetAddress(address + NEXT_IN);
    }

    public int avail_in() {
        return MemoryUtil.memGetInt(address + AVAIL_IN);
    }

    public long next_out() {
        return MemoryUtil.memGetAddress(address + NEXT_OUT);
    }

    public int avail_out() {
        return MemoryUtil.memGetInt(address + AVAIL_OUT);
    }

    public long msg() {
        return MemoryUtil.memGetAddress(address + MSG);
    }

    public void next_in(long v) {
        MemoryUtil.memPutAddress(address + NEXT_IN, v);
    }

    public void avail_in(int v) {
        MemoryUtil.memPutInt(address + AVAIL_IN, v);
    }

    public void next_out(long v) {
        MemoryUtil.memPutAddress(address + NEXT_OUT, v);
    }

    public void avail_out(int v) {
        MemoryUtil.memPutInt(address + AVAIL_OUT, v);
    }
}

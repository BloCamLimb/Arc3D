/*
 * Arc UI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arc UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcui.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for objects that may be shared by multiple objects. When an
 * existing owner wants to share a reference, it calls {@link #ref()}.
 * When an owner wants to release its reference, it calls {@link #unref()}.
 * When the shared object's reference count goes to zero as the result of
 * an {@link #unref()} call, its {@link #onFree()} is called. It is an
 * error for the destructor to be called explicitly (or via the object
 * going out of scope on the stack or calling {@link #onFree()}) if
 * {@link #getRefCnt()} > 1.
 */
public abstract class RefCnt implements AutoCloseable {

    private static final VarHandle REF_CNT;
    private static final ConcurrentHashMap<RefCnt, Boolean> TRACKER;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            REF_CNT = lookup.findVarHandle(RefCnt.class, "mRefCnt", int.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        TRACKER = new ConcurrentHashMap<>(0, 0.9f);
        try {
            assert false;
        } catch (AssertionError e) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Subclasses should override toString() for debug purposes
                TRACKER.forEach((o, __) -> System.err.printf("Leaked: %s\n", o.toString()));
                assert TRACKER.isEmpty();
            }, "RefCnt-Tracker"));
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int mRefCnt = 1;

    /**
     * Default constructor, initializing the reference count to 1.
     */
    @SuppressWarnings("AssertWithSideEffects")
    public RefCnt() {
        assert TRACKER.put(this, Boolean.TRUE) == null;
    }

    /**
     * May return true if the caller is the only owner. Ensures that all previous owner's
     * actions are complete.
     *
     * @return true if this object is uniquely referenced by the program
     */
    public final boolean unique() {
        // std::memory_order_acquire
        return (int) REF_CNT.getAcquire(this) == 1;
    }

    /**
     * Increases the reference count by 1 on the client. Must be balanced by a call to unref().
     * It's an error to call this method if the reference count has already reached zero.
     */
    public final void ref() {
        // std::memory_order_seq_cst, maybe relaxed?
        assert mRefCnt > 0;
        // stronger than std::memory_order_relaxed
        REF_CNT.getAndAddRelease(this, 1);
    }

    /**
     * Decreases the reference count by 1 on the client. If the reference count is 1 before
     * the decrement, then {@link #onFree()} is called. It's an error to call this method if
     * the reference count has already reached zero.
     */
    @SuppressWarnings("AssertWithSideEffects")
    public final void unref() {
        // std::memory_order_seq_cst, maybe relaxed?
        assert mRefCnt > 0;
        // stronger than std::memory_order_acq_rel
        if ((int) REF_CNT.getAndAdd(this, -1) == 1) {
            onFree();
            assert TRACKER.remove(this) == Boolean.TRUE;
        }
    }

    /**
     * Calls {@link #unref()}. May be used with try-with-resources statement.
     */
    @Override
    public final void close() {
        unref();
    }

    /**
     * Debug only. Returns the reference count, accessed in program order, but with no
     * assurance of memory ordering effects with respect to other threads.
     *
     * @return the reference count
     */
    public final int getRefCnt() {
        // std::memory_order_relaxed
        return (int) REF_CNT.getOpaque(this);
    }

    /**
     * This must be used with caution. It is only valid to call this when 'threadIsolatedTestCnt'
     * refs are known to be isolated to the current thread. That is, it is known that there are at
     * least 'threadIsolatedTestCnt' refs for which no other thread may make a balancing unref()
     * call. Assuming the contract is followed, if this returns false then no other thread has
     * ownership of this. If it returns true then another thread *may* have ownership.
     */
    public final boolean refCntGreaterThan(int threadIsolatedTestCnt) {
        // std::memory_order_acquire
        int cnt = (int) REF_CNT.getAcquire(this);
        // If this fails then the above contract has been violated.
        assert (cnt >= threadIsolatedTestCnt);
        return cnt > threadIsolatedTestCnt;
    }

    /**
     * Override this method to invoke de-allocation of the underlying resource.
     */
    protected abstract void onFree();
}

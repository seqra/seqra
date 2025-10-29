package org.opentaint.dataflow.util;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistentInt2LongMap extends Int2LongOpenHashMap {
    private int modification = 0;

    public static final long NO_VALUE = -1L;

    public PersistentInt2LongMap() {
        super();
        defaultReturnValue(NO_VALUE);
    }

    @Override
    public long put(int k, long v) {
        ensureMutable();

        long result = super.put(k, v);
        if (result != v) modification++;
        return result;
    }

    @Override
    public long remove(int k) {
        ensureMutable();

        long result = super.remove(k);
        if (result != defRetValue) modification++;
        return result;
    }

    public @NotNull PersistentInt2LongMap mutable() {
        if (modification != PERSISTED) return this;

        PersistentInt2LongMap copy = (PersistentInt2LongMap) clone();
        copy.modification = 0;
        return copy;
    }

    public @NotNull PersistentInt2LongMap persist(final @Nullable PersistentInt2LongMap original) {
        if (modification == 0) {
            if (original != null) {
                if (original.modification != PERSISTED) {
                    throw new IllegalStateException("Original map must be persisted");
                }
                return original;
            }
        }

        if (size == 0) return emptyPersistentInt2LongMap();
        modification = PERSISTED;
        return this;
    }

    private void ensureMutable() {
        if (modification == PERSISTED) {
            throw new IllegalStateException("Persisted map modification");
        }
    }

    private static final int PERSISTED = -1;
    private static final PersistentInt2LongMap EMPTY_PERSISTENT_INT2LONG_MAP = new PersistentInt2LongMap();

    static {
        EMPTY_PERSISTENT_INT2LONG_MAP.modification = PERSISTED;
    }

    public static @NotNull PersistentInt2LongMap emptyPersistentInt2LongMap() {
        return EMPTY_PERSISTENT_INT2LONG_MAP;
    }

    private static final long serialVersionUID = 0L;
}

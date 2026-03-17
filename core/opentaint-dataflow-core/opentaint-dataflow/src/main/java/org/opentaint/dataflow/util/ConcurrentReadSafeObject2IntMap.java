package org.opentaint.dataflow.util;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

public final class ConcurrentReadSafeObject2IntMap<K> extends Object2IntOpenHashMap<K> {
    public static final int NO_VALUE = -1;

    public ConcurrentReadSafeObject2IntMap() {
        super();
        defaultReturnValue(NO_VALUE);
    }

    @Override
    public int getInt(@Nullable Object k) {
        if (k == null) {
            if (!containsNullKey) return defRetValue;

            do {
                int n = this.n;
                int[] value = this.value;
                if (value.length == n + 1) return value[n];
            } while (true);
        }

        while (true) {
            K[] key = this.key;
            int[] value = this.value;
            int n = this.n;

            // capture arrays to allow concurrent reads
            if (key.length != n + 1 || value.length != n + 1) continue;

            int mask = n - 1;

            // The starting point.
            int pos = HashCommon.mix(k.hashCode()) & mask;

            K curr = key[pos];
            if (curr == null) return defRetValue;

            if (k.equals(curr)) return value[pos];

            // There's always an unused entry.
            while (true) {
                pos = (pos + 1) & mask;

                curr = key[pos];
                if (curr == null) return defRetValue;

                if (k.equals(curr)) return value[pos];
            }
        }
    }

    @Override
    public int removeInt(Object k) {
        throw new UnsupportedOperationException("Removals are not allowed");
    }

    private static final long serialVersionUID = 0L;
}

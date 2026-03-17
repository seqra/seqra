package org.opentaint.dataflow.util;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public final class ConcurrentReadSafeInt2ObjectMap<V> extends Int2ObjectOpenHashMap<V> {
    @Override
    public @Nullable V get(int k) {
        if (k == 0) {
            if (!containsNullKey) return defRetValue;

            do {
                int n = this.n;
                V[] value = this.value;
                if (value.length == n + 1) return value[n];
            } while (true);
        }

        while (true) {
            int[] key = this.key;
            V[] value = this.value;
            int n = this.n;

            // capture arrays to allow concurrent reads
            if (key.length != n + 1 || value.length != n + 1) continue;

            int mask = n - 1;

            // The starting point.
            int pos = HashCommon.mix(k) & mask;
            int curr = key[pos];
            if (curr == 0) return defRetValue;

            if (k == curr) return value[pos];

            // There's always an unused entry.
            while (true) {
                pos = (pos + 1) & mask;
                curr = key[pos];
                if (curr == 0) return defRetValue;

                if (k == curr) return value[pos];
            }
        }
    }

    public int[] getKeys() {
        return this.key;
    }

    public V[] getValues() {
        return this.value;
    }

    public int getN() {
        return this.n;
    }

    public boolean getContainsNullKey() {
        return this.containsNullKey;
    }

    private static final long serialVersionUID = 0L;
}


package jetbrains.exodus.core.dataStructures.persistent;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public interface PersistentLongMap<V> {

    ImmutableMap<V> beginRead();

    PersistentLongMap<V> getClone();

    MutableMap<V> beginWrite();

    interface ImmutableMap<V> extends Iterable<Entry<V>> {
        V get(long key);

        boolean containsKey(long key);

        boolean isEmpty();

        int size();

        Entry<V> getMinimum();

        Iterator<Entry<V>> reverseIterator();

        Iterator<Entry<V>> tailEntryIterator(long staringKey);

        Iterator<Entry<V>> tailReverseEntryIterator(long staringKey);
    }

    interface MutableMap<V> extends ImmutableMap<V> {
        void put(long key, @NotNull V value);

        V remove(long key);

        void clear();

        boolean endWrite();

        void testConsistency(); // for testing consistency
    }

    interface Entry<V> extends Comparable<Entry<V>> {
        long getKey();

        V getValue();
    }
}

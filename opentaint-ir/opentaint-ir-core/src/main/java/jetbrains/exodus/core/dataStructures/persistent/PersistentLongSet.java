
package jetbrains.exodus.core.dataStructures.persistent;

import jetbrains.exodus.core.dataStructures.LongIterator;

public interface PersistentLongSet {

    ImmutableSet beginRead();

    PersistentLongSet getClone();

    MutableSet beginWrite();

    interface ImmutableSet {

        boolean contains(long key);

        LongIterator longIterator();

        LongIterator reverseLongIterator();

        LongIterator tailLongIterator(long key);

        LongIterator tailReverseLongIterator(long key);

        boolean isEmpty();

        int size();
    }

    interface MutableSet extends ImmutableSet {

        void add(long key);

        boolean remove(long key);

        void clear();

        boolean endWrite();
    }
}

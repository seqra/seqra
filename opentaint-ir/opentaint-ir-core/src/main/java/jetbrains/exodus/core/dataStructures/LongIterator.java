
package jetbrains.exodus.core.dataStructures;

import java.util.Iterator;
import java.util.NoSuchElementException;

public interface LongIterator extends Iterator<Long> {
    LongIterator EMPTY = new LongIterator() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public long nextLong() {
            throw new NoSuchElementException();
        }

        @Override
        public Long next() {
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    };

    long nextLong();
}

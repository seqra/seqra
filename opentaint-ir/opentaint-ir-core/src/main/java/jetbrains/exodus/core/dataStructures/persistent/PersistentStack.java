
package jetbrains.exodus.core.dataStructures.persistent;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PersistentStack<T> implements Iterable<T> {

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public static final PersistentStack EMPTY_STACK = new PersistentStack();

    private final T element;
    private final int size;
    private final PersistentStack<T> next;

    private PersistentStack() {
        element = null;
        size = 0;
        next = null;
    }

    private PersistentStack(T e, PersistentStack<T> stack) {
        element = e;
        size = stack.size + 1;
        next = stack;
    }

    public boolean isEmpty() {
        return element == null;
    }

    public int size() {
        return size;
    }

    public PersistentStack<T> push(@NotNull T e) {
        return new PersistentStack<>(e, this);
    }

    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return element;
    }

    public PersistentStack<T> skip() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return next;
    }

    public PersistentStack<T> reverse() {
        PersistentStack<T> result = new PersistentStack<>();
        for (PersistentStack<T> stack = this; !stack.isEmpty(); stack = stack.skip()) {
            //noinspection ObjectAllocationInLoop
            result = new PersistentStack<>(stack.peek(), result);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return isEmpty() ? 271828182 : element.hashCode() + next.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PersistentStack)) {
            return false;
        }
        PersistentStack<T> stack = (PersistentStack<T>) obj;
        if (isEmpty()) {
            return stack.isEmpty();
        }
        if (stack.isEmpty()) {
            return false;
        }
        return element.equals(stack.element) && next.equals(stack.next);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        final PersistentStack[] current = {this};
        return new Iterator<T>() {

            @Override
            public boolean hasNext() {
                return !current[0].isEmpty();
            }

            @Override
            public T next() {
                @SuppressWarnings("unchecked") final T result = (T) current[0].element;
                current[0] = current[0].next;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }
}

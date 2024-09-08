
package jetbrains.exodus.core.dataStructures;

import java.util.ArrayList;

@SuppressWarnings({"CloneableClassInSecureContext", "CloneableClassWithoutClone", "ClassExtendsConcreteCollection"})
public class Stack<T> extends ArrayList<T> {

    private T last;

    public void push(T t) {
        if (last != null) {
            add(last);
        }
        last = t;
    }

    public T peek() {
        return last;
    }

    public T pop() {
        final T result = last;
        if (result != null) {
            last = super.isEmpty() ? null : remove(super.size() - 1);
        }
        return result;
    }

    @Override
    public int size() {
        return last == null ? 0 : super.size() + 1;
    }

    @Override
    public boolean isEmpty() {
        return last == null;
    }
}

package org.opentaint.ir.testing.types;

import java.util.ArrayList;
import java.util.List;

public class Generics {

    static class LinkedBase<T, W extends List<T>> {
        T state;
        W stateW;
        List<W> stateListW;

    }

    static class LinkedImpl<W extends List<String>> extends LinkedBase<String, W> {
    }

    static class SingleBase<T> {

        private T state;
        private ArrayList<T> stateList;

        T run1(T incoming) {
            state = incoming;
            stateList.add(incoming);
            return incoming;
        }

        <W extends T> W run2(List<W> incoming) {
            state = incoming.get(0);
            stateList.addAll(incoming);
            return incoming.get(0);
        }

    }

    static class SingleImpl extends SingleBase<String> {}
}

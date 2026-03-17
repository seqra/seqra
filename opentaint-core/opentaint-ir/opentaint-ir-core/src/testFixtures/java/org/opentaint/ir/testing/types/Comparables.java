package org.opentaint.ir.testing.types;

import org.jetbrains.annotations.NotNull;

public class Comparables {

    public static class ComparableTest1 implements Comparable<ComparableTest1> {
        @Override
        public int compareTo(@NotNull ComparableTest1 o) {
            return 0;
        }
    }

    public static class ComparableTest2<T extends Comparable<T>> implements Comparable<T> {

        @Override
        public int compareTo(@NotNull T o) {
            return 0;
        }
    }

    public static class ComparableTest3 extends ComparableTest2<ComparableTest3> {

    }

    public static class ComparableTest4<T extends Comparable<W>, W extends Comparable<T>> extends ComparableTest2<ComparableTest3> {
        W stateW;
        T stateT;
    }

    public static class ComparableTest5 extends ComparableTest4<Integer, Integer> {
    }

}

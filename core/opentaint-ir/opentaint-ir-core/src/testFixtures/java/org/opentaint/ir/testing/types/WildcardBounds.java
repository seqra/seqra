package org.opentaint.ir.testing.types;

import java.util.List;

public class WildcardBounds {

    public static class DirectBound<T> {
        public List<T> field;
    }

    public static class WildcardUpperBound<T> {
        public List<? extends T> field;
    }

    public static class WildcardUpperBoundString extends WildcardUpperBound<String> {
    }

    public static class WildcardLowerBoundString extends WildcardLowerBound<String> {
    }

    public static class DirectBoundString extends DirectBound<String> {
    }

    public static class WildcardLowerBound<T> {

        public List<? super T> field;

        public List<T> method(List<T> input) {
            return null;
        }
    }
}

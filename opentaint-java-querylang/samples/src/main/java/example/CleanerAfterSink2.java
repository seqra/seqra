package example;

import base.IFDSFalsePositive;
import base.RuleSample;
import base.RuleSet;

@RuleSet("example/CleanerAfterSink2.yaml")
public abstract class CleanerAfterSink2 implements RuleSample {
    Object src() {
        return new Object();
    }

    Object copy(Object o) {
        return new Object();
    }

    void sink(Object a, Object b) {
    }

    void clean(Object a, Object b) {
    }

    static class PositiveSimple extends CleanerAfterSink2 {
        @Override
        public void entrypoint() {
            Object a = src();
            Object b = copy(a);
            sink(a, b);
        }
    }

    @IFDSFalsePositive("Cleaner requires 2 facts")
    static class NegativeSimple extends CleanerAfterSink2 {
        @Override
        public void entrypoint() {
            Object a = src();
            Object b = copy(a);
            sink(a, b);
            clean(a, b);
        }
    }

    static class PositiveMultipleFunctions extends CleanerAfterSink2 {
        @Override
        public void entrypoint() {
            Object a = nestedSrc();
            Object b = copy(a);
            nestedSink(a, b);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object a, Object b) {
            sink(a, b);
        }
    }

    @IFDSFalsePositive("Cleaner requires 2 facts")
    static class NegativeMultipleFunctions extends CleanerAfterSink2 {
        @Override
        public void entrypoint() {
            Object a = nestedSrc();
            Object b = copy(a);
            nestedSink(a, b);
            nestedClean(a, b);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object a, Object b) {
            sink(a, b);
        }

        void nestedClean(Object a, Object b) {
            clean(a, b);
        }
    }

    static class PositiveBranch extends CleanerAfterSink2 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            Object a = nestedSrc();
            Object b = copy(a);
            nestedSink(a, b);
            nestedClean(a, b);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object a, Object b) {
            sink(a, b);
        }

        void nestedClean(Object a, Object b) {
            if (applyClean) {
                clean(a, b);
            }
        }
    }

    @IFDSFalsePositive("Cleaner requires 2 facts")
    static class NegativeBranch extends CleanerAfterSink2 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            Object a = nestedSrc();
            Object b = copy(a);
            nestedSink(a, b);
            nestedClean(a, b);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object a, Object b) {
            sink(a, b);
        }

        void nestedClean(Object a, Object b) {
            if (applyClean) {
                clean(a, b);
            } else {
                otherClean(a, b);
            }
        }

        void otherClean(Object a, Object b) {
            clean(a, b);
        }
    }
}

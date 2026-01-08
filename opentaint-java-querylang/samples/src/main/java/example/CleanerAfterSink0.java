package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/CleanerAfterSink0.yaml")
public abstract class CleanerAfterSink0 implements RuleSample {
    Object src() {
        return new Object();
    }

    void sink(Object o) {
    }

    void clean() {
    }

    static class PositiveSimple extends CleanerAfterSink0 {
        @Override
        public void entrypoint() {
            Object o = src();
            sink(o);
        }
    }

    static class NegativeSimple extends CleanerAfterSink0 {
        @Override
        public void entrypoint() {
            Object o = src();
            sink(o);
            clean();
        }
    }

    static class PositiveMultipleFunctions extends CleanerAfterSink0 {
        @Override
        public void entrypoint() {
            Object o = nestedSrc();
            nestedSink(o);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object o) {
            sink(o);
        }
    }

    static class NegativeMultipleFunctions extends CleanerAfterSink0 {
        @Override
        public void entrypoint() {
            Object o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object o) {
            sink(o);
        }

        void nestedClean(Object o) {
            clean();
        }
    }

    static class PositiveBranch extends CleanerAfterSink0 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            Object o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object o) {
            sink(o);
        }

        void nestedClean(Object o) {
            if (applyClean) {
                clean();
            }
        }
    }

    static class NegativeBranch extends CleanerAfterSink0 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            Object o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        Object nestedSrc() {
            return src();
        }

        void nestedSink(Object o) {
            sink(o);
        }

        void nestedClean(Object o) {
            if (applyClean) {
                clean();
            } else {
                otherClean(o);
            }
        }

        void otherClean(Object o) {
            clean();
        }
    }
}

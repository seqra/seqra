package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/CleanerAfterSink1.yaml")
public abstract class CleanerAfterSink1 implements RuleSample {
    String src() {
        return "tainted";
    }

    void sink(String o) {
    }

    void clean(String o) {
    }

    static class PositiveSimple extends CleanerAfterSink1 {
        @Override
        public void entrypoint() {
            String o = src();
            sink(o);
        }
    }

    static class NegativeSimple extends CleanerAfterSink1 {
        @Override
        public void entrypoint() {
            String o = src();
            sink(o);
            clean(o);
        }
    }

    static class PositiveMultipleFunctions extends CleanerAfterSink1 {
        @Override
        public void entrypoint() {
            String o = nestedSrc();
            nestedSink(o);
        }

        String nestedSrc() {
            return src();
        }

        void nestedSink(String o) {
            sink(o);
        }
    }

    static class NegativeMultipleFunctions extends CleanerAfterSink1 {
        @Override
        public void entrypoint() {
            String o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        String nestedSrc() {
            return src();
        }

        void nestedSink(String o) {
            sink(o);
        }

        void nestedClean(String o) {
            clean(o);
        }
    }

    static class PositiveBranch extends CleanerAfterSink1 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            String o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        String nestedSrc() {
            return src();
        }

        void nestedSink(String o) {
            sink(o);
        }

        void nestedClean(String o) {
            if (applyClean) {
                clean(o);
            }
        }
    }

    static class NegativeBranch extends CleanerAfterSink1 {
        boolean applyClean;

        @Override
        public void entrypoint() {
            String o = nestedSrc();
            nestedSink(o);
            nestedClean(o);
        }

        String nestedSrc() {
            return src();
        }

        void nestedSink(String o) {
            sink(o);
        }

        void nestedClean(String o) {
            if (applyClean) {
                clean(o);
            } else {
                otherClean(o);
            }
        }

        void otherClean(String o) {
            clean(o);
        }
    }
}

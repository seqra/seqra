package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithPatternsSignature.yaml")
public abstract class RuleWithPatternsSignature implements RuleSample {
    void sink(String data) {
    }

    String other(String data) {
        return "other";
    }

    static class PositiveSimple extends RuleWithPatternsSignature {
        @Override
        public void entrypoint() {
            method("data");
        }

        private void method(String src) {
            sink(src);
        }
    }

    static class NegativeSimple1 extends RuleWithPatternsSignature {
        @Override
        public void entrypoint() {
            method("data");
        }

        private void method(String src) {
            other(src);
        }
    }

    static class NegativeSimple2 extends RuleWithPatternsSignature {
        @Override
        public void entrypoint() {
            String src = other("other");
            sink(src);
        }
    }
}

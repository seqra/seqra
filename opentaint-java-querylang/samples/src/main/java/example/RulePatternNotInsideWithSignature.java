package example;

import base.RuleSample;
import base.RuleSet;
import base.TaintRuleFalsePositive;

@RuleSet("example/RulePatternNotInsideWithSignature.yaml")
public abstract class RulePatternNotInsideWithSignature implements RuleSample {
    void sink(String data) {
    }

    @TaintRuleFalsePositive("Cleaner captures data before sink")
    static class NegativeSimple extends RulePatternNotInsideWithSignature {
        @Override
        public void entrypoint() {
            method("data");
        }

        private void method(String src) {
            sink(src);
        }
    }

    static class Positive extends RulePatternNotInsideWithSignature {
        @Override
        public void entrypoint() {
            method("data");
        }

        private void method(String src) {
            sink("unsafe");
        }
    }
}

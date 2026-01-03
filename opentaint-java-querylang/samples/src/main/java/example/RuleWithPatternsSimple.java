package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithPatternsSimple.yaml")
public abstract class RuleWithPatternsSimple implements RuleSample {
    String src() {
        return "data";
    }

    void sink(String data) {
    }

    String other(String data) {
        return "other";
    }

    static class PositiveSimple extends RuleWithPatternsSimple {
        @Override
        public void entrypoint() {
            String src = src();
            sink(src);
        }
    }

    static class NegativeSimple1 extends RuleWithPatternsSimple {
        @Override
        public void entrypoint() {
            String src = src();
            other(src);
        }
    }

    static class NegativeSimple2 extends RuleWithPatternsSimple {
        @Override
        public void entrypoint() {
            String src = other("other");
            sink(src);
        }
    }
}

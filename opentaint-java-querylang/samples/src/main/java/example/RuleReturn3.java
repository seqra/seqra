package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturn3.yaml")
public abstract class RuleReturn3 implements RuleSample {
    static class Positive extends RuleReturn3 {
        @Override
        public void entrypoint() {
            String v = returned();
            sink(v);
        }

        private String returned() {
            return src();
        }
    }

    static class Negative extends RuleReturn3 {
        @Override
        public void entrypoint() {
            String v = returned();
        }

        private String returned() {
            return "literal";
        }
    }

    String src() { return "tainted"; }
    void sink(String s) {}
}
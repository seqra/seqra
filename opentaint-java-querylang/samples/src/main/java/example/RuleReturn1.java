package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturn1.yaml")
public abstract class RuleReturn1 implements RuleSample {
    static class Positive extends RuleReturn1 {
        @Override
        public void entrypoint() {
            String res = returned();
        }

        private String returned() {
            String a = src();
            return a;
        }
    }

    static class Negative extends RuleReturn1 {
        @Override
        public void entrypoint() {
            String res = returned();
        }

        private String returned() {
            String a = src();
            return "safe";
        }
    }

    // utility simulated sources/cleaners/sink for tests
    String src() { return "tainted"; }
    void clean(String s) {}
    void sink(String s) {}
}
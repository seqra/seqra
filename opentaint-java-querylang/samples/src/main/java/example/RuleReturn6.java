package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturn6.yaml")
public abstract class RuleReturn6 implements RuleSample {
    static class Positive extends RuleReturn6 {
        @Override
        public void entrypoint() {
            returned();
        }

        private String returned() {
            String a = src();
            return "";
        }
    }

    static class Negative extends RuleReturn6 {
        @Override
        public void entrypoint() {
            returned();
        }

        private String returned() {
            String a = src();
            return a;
        }
    }

    String src() { return "tainted"; }
    void clean(String s) {}
    void sink(String s) {}
}
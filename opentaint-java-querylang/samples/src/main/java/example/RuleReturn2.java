package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturn2.yaml")
public abstract class RuleReturn2 implements RuleSample {
    static class Positive extends RuleReturn2 {
        @Override
        public void entrypoint() {
            String res = returned();
            f(res);
        }

        private String returned() {
            String a = src();
            // some extra statements
            f(a);
            return a;
        }
    }

    static class Negative extends RuleReturn2 {
        @Override
        public void entrypoint() {
            String res = returned();
            // cleaned before return
            f(res);
        }

        private String returned() {
            String a = src();
            clean(a);
            return a;
        }
    }

    String src() { return "tainted"; }
    void clean(String s) {}
    void f(String s) {}
}
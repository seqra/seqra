package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/R1.yaml")
public abstract class R1 implements RuleSample {
    Object foo(Object data) {
        return null;
    }

    static class Positive extends R1 {
        @Override
        public void entrypoint() {
            method("data");
        }

        private Object method(Object arg) {
            Object x = foo(null);
            return x;
        }
    }

    static class Negative extends R1 {
        @Override
        public void entrypoint() {
            method("data");
        }

        private Object method(Object arg) {
            Object x = foo(arg);
            return x;
        }
    }
}

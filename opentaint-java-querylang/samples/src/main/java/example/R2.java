package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/R2.yaml")
public abstract class R2 implements RuleSample {
    Object foo(Object data) {
        return null;
    }

    static class Positive extends R2 {
        @Override
        public void entrypoint() {
            method("data");
        }

        private Object method(Object arg) {
            Object x = foo(null);
            return x;
        }
    }
}

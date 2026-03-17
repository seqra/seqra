package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/R3.yaml")
public abstract class R3 implements RuleSample {
    Object foo(Object data) {
        return null;
    }
    Object bar(Object data) {
        return null;
    }

    static class Positive extends R3 {
        @Override
        public void entrypoint() {
            method("data");
        }

        private Object method(Object arg) {
            Object x = foo(arg);
            Object y = bar(x);
            return y;
        }
    }
}

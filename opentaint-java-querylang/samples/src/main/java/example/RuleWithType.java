package example;

import base.RuleSample;
import base.RuleSet;
import example.util.OtherType;
import example.util.SimpleType;

@RuleSet("example/RuleWithType.yaml")
public abstract class RuleWithType implements RuleSample {
    public static class PositiveSample extends RuleWithType {
        @Override
        public void entrypoint() {
            f(new SimpleType());
        }

        private void f(SimpleType o) {
            o.foo();
        }
    }

    public static class NegativeSample extends RuleWithType {
        @Override
        public void entrypoint() {
            f(new OtherType());
        }

        private void f(OtherType o) {
            o.foo();
        }
    }
}

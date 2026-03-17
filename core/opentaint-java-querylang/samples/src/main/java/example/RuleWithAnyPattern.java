package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithAnyPattern.yaml")
public abstract class RuleWithAnyPattern implements RuleSample {
    void method(String s) {

    }

    void method(int i) {

    }

    static class PositiveSample extends RuleWithAnyPattern {
        @Override
        public void entrypoint() {
            method("data");
        }
    }

    static class NegativeSample extends RuleWithAnyPattern {
        @Override
        public void entrypoint() {
            method(0);
        }
    }
}

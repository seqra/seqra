package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithoutPattern.yaml")
public abstract class RuleWithoutPattern implements RuleSample {
    void method(String s) {

    }

    void method(int i) {

    }

    static class PositiveSample extends RuleWithoutPattern {
        @Override
        public void entrypoint() {
            method("data");
        }
    }

    static class NegativeSample extends RuleWithoutPattern {
        @Override
        public void entrypoint() {
            method(0);
        }
    }
}

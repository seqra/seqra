package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;

@RuleSet("example/RuleReturnSimple.yaml")
public abstract class RuleReturnSimple implements RuleSample {
    static class Positive extends RuleReturnSimple {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType1 ret = src;
            return ret;
        }
    }

    static class Negative extends RuleReturnSimple {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType1 safe = new CustomType1();
            return safe;
        }
    }
}
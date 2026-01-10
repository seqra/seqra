package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;

@RuleSet("example/RuleReturnConditional.yaml")
public abstract class RuleReturnConditional implements RuleSample {
    static class PositiveSrcElseDerived extends RuleReturnConditional {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType2 simple(CustomType1 src) {
            CustomType1 ret;
            if (src != null) {
                ret = src;
            } else {
                ret = new CustomType1();
            }
            return ret.mkType2();
        }
    }

    static class NegativeAlwaysSafe extends RuleReturnConditional {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType2 simple(CustomType1 src) {
            CustomType1 safe = new CustomType1();
            if (true) {
                safe = new CustomType1();
            }
            return safe.mkType2();
        }
    }
}
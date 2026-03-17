package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleWithMultiplePatternsUnification.yaml")
public abstract class RuleWithMultiplePatternsUnification implements RuleSample {
    static class NegativeSimple extends RuleWithMultiplePatternsUnification {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType3 sink = new CustomType3(src);
            return sink;
        }
    }

    static class PositiveOther extends RuleWithMultiplePatternsUnification {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 unused = src.mkType2();
            CustomType3 sink = new CustomType3(src);
            return sink;
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleReturnChained.yaml")
public abstract class RuleReturnChained implements RuleSample {
    static class Positive extends RuleReturnChained {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType3 v3 = src.mkType2().mkType3();
            CustomType1 sink = v3.mkType1();
            return sink;
        }
    }

    static class PositiveOneLine extends RuleReturnChained {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            return src.mkType2().mkType3().mkType1();
        }
    }

    static class Negative extends RuleReturnChained {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            return src.mkType2().mkType3();
        }
    }
}

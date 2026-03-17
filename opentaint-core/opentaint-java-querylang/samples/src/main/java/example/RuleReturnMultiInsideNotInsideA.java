package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleReturnMultiInsideNotInsideA.yaml")
public abstract class RuleReturnMultiInsideNotInsideA implements RuleSample {
    static class Positive extends RuleReturnMultiInsideNotInsideA {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 v2 = src.mkType2();
            CustomType3 sink = v2.mkType3();
            return sink;
        }
    }

    static class Negative extends RuleReturnMultiInsideNotInsideA {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 v2 = src.mkType2();
            CustomType3 sink = v2.mkType3();
            sanitizeA(sink);
            return sink;
        }

        private void sanitizeA(CustomType3 t) {}
    }
}
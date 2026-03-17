package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleWithMultiplePatterns.yaml")
public abstract class RuleWithMultiplePatterns implements RuleSample {
    static class PositiveSimple extends RuleWithMultiplePatterns {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType2 v2 = src.mkType2();
            CustomType3 v3 = v2.mkType3();
            CustomType1 sink = v3.mkType1();
            return sink;
        }
    }

    static class PositiveOneLine extends RuleWithMultiplePatterns {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            return src.mkType2().mkType3().mkType1();
        }
    }

    static class NegativeSimple extends RuleWithMultiplePatterns {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            return src;
        }
    }


    static class NegativeV1 extends RuleWithMultiplePatterns {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            return src.mkType2().mkType3();
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;

@RuleSet("example/RuleReturnNotInsidePrefix.yaml")
public abstract class RuleReturnNotInsidePrefix implements RuleSample {
    static class Positive extends RuleReturnNotInsidePrefix {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType2 s = src.mkType2();
            return s.mkType3().mkType1();
        }
    }

    static class Negative extends RuleReturnNotInsidePrefix {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType2 s = src.mkType2();
            s.clean();
            return s.mkType3().mkType1();
        }
    }
}
package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;

@RuleSet("example/RuleReturnNotInside.yaml")
public abstract class RuleReturnNotInside implements RuleSample {
    static class Positive extends RuleReturnNotInside {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType1 ret = src.mkType2().mkType3().mkType1();
            return ret;
        }
    }

    static class Negative extends RuleReturnNotInside {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType1 ret = src.mkType2().mkType3().mkType1();
            sanitize(ret);
            return ret;
        }

        private void sanitize(CustomType1 t) {
            // pretend sanitization
        }
    }
}
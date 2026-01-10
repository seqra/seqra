package example;

import base.IFDSFalsePositive;
import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturnWithNotInsideSignature.yaml")
public abstract class RuleReturnWithNotInsideSignature implements RuleSample {
    public Object clean(Object o) {
        return o;
    }

    public static class Positive extends RuleReturnWithNotInsideSignature {

        @Override
        public void entrypoint() {
            method("data");
        }

        public Object method(Object o) {
            return o;
        }
    }

    @IFDSFalsePositive("Cleaner result is cleaned but mark is propagated") // todo
    public static class Negative extends RuleReturnWithNotInsideSignature {

        @Override
        public void entrypoint() {
            method("data");
        }

        public Object method(Object o) {
            return clean(o);
        }
    }
}

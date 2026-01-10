package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturnInsideSignature.yaml")
public abstract class RuleReturnInsideSignature implements RuleSample {
    static class Positive extends RuleReturnInsideSignature {
        @Override
        public void entrypoint() {
            returned("tainted");
        }

        private String returned(String s) {
            String a = src(s);
            return a;
        }
    }

    static class Negative extends RuleReturnInsideSignature {
        @Override
        public void entrypoint() {
            returned("tainted");
        }

        private String returned(String s) {
            String a = src("safe");
            return a;
        }
    }

    String src(String data) { return "tainted"; }
}
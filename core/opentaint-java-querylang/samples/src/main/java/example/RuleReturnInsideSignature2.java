package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleReturnInsideSignature2.yaml")
public abstract class RuleReturnInsideSignature2 implements RuleSample {
    static class Positive extends RuleReturnInsideSignature2 {
        @Override
        public void entrypoint() {
            returned("tainted");
        }

        private String returned(String s) {
            String f = pass(s);
            String a = src(f);
            return a;
        }
    }

    static class Negative extends RuleReturnInsideSignature2 {
        @Override
        public void entrypoint() {
            returned("tainted");
        }

        private String returned(String s) {
            String f = pass(s);
            String a = src("safe");
            return a;
        }
    }

    static class Negative2 extends RuleReturnInsideSignature2 {
        @Override
        public void entrypoint() {
            returned("tainted");
        }

        private String returned(String s) {
            String f = pass("safe");
            String a = src(f);
            return a;
        }
    }

    String src(String data) { return "tainted"; }

    String pass(String data) { return "copy"; }
}
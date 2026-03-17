package custom;

import base.EntryPoint;
import base.RuleSample;
import base.RuleSet;

@RuleSet("custom/RuleReturnWithNotInsideSignatureStrConcat.yaml")
public abstract class RuleReturnWithNotInsideSignatureStrConcat implements RuleSample {
    public String clean(String o) {
        return o;
    }

    public static class Positive extends RuleReturnWithNotInsideSignatureStrConcat {

        @Override
        public void entrypoint() {
            method("data");
        }

        @EntryPoint
        public String method(String o) {
            return "prefix" + o + "suffix";
        }
    }

    public static class Negative extends RuleReturnWithNotInsideSignatureStrConcat {

        @Override
        public void entrypoint() {
            method("data");
        }

        @EntryPoint
        public String method(String o) {
            return "prefix" + clean(o) + "suffix";
        }
    }
}

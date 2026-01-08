package example;

import base.RuleSample;
import base.RuleSet;
import base.TaintRuleFalsePositive;

@RuleSet("example/RulePatternNotWithSignature.yaml")
public abstract class RulePatternNotWithSignature implements RuleSample {
    void f(String data) {

    }

    void clean(String data) {

    }

    final static class PositiveSimple extends RulePatternNotWithSignature {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
        }
    }

    final static class NegativeNoF extends RulePatternNotWithSignature {
        @Override
        public void entrypoint() {
            System.out.println("Hello!");
        }
    }

    @TaintRuleFalsePositive("Cleaner captures data before sink")
    final static class NegativeCleanFirst extends RulePatternNotWithSignature {
        @Override
        public void entrypoint() {
            String data = "";
            clean(data);
            f(data);
        }
    }

    final static class NegativeCleanSecond extends RulePatternNotWithSignature {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            clean(data);
        }
    }
}

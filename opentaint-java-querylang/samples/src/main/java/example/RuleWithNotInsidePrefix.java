package example;

import base.RuleSample;
import base.RuleSet;
import base.TaintRuleFalsePositive;

@RuleSet("example/RuleWithNotInsidePrefix.yaml")
public abstract class RuleWithNotInsidePrefix implements RuleSample {
    void sink(String data) {

    }

    void prefixClean(String data) {

    }

    final static class PositiveSimple extends RuleWithNotInsidePrefix {
        @Override
        public void entrypoint() {
            String data = "";
            sink(data);
        }
    }

    final static class PositiveCleanSecond extends RuleWithNotInsidePrefix {
        @Override
        public void entrypoint() {
            String data = "";
            sink(data);
            prefixClean(data);
        }
    }

    final static class PositiveCleanOnOtherData extends RuleWithNotInsidePrefix {
        @Override
        public void entrypoint() {
            String data = "";
            String data1 = "aaa";
            prefixClean(data1);
            sink(data);
        }
    }

    @TaintRuleFalsePositive("Cleaner captures data before sink")
    final static class NegativeCleanFirst extends RuleWithNotInsidePrefix {
        @Override
        public void entrypoint() {
            String data = "";
            prefixClean(data);
            sink(data);
        }
    }
}

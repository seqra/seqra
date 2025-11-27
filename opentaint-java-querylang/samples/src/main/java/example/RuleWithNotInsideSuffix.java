package example;

import base.IFDSFalsePositive;
import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithNotInsideSuffix.yaml")
public abstract class RuleWithNotInsideSuffix implements RuleSample {
    void sink(String data) {

    }

    void suffixClean(String data) {

    }

    final static class PositiveSimple extends RuleWithNotInsideSuffix {
        @Override
        public void entrypoint() {
            String data = "";
            sink(data);
        }
    }

    final static class PositiveCleanFirst extends RuleWithNotInsideSuffix {
        @Override
        public void entrypoint() {
            String data = "";
            suffixClean(data);
            sink(data);
        }
    }

    final static class PositiveCleanOnOtherData extends RuleWithNotInsideSuffix {
        @Override
        public void entrypoint() {
            String data = "";
            String data1 = "";
            sink(data);
            suffixClean(data1);
        }
    }

    @IFDSFalsePositive("cleaner requires 2 facts: data & state")
    final static class NegativeCleanSecond extends RuleWithNotInsideSuffix {
        @Override
        public void entrypoint() {
            String data = "";
            sink(data);
            suffixClean(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithSeveralSuffixCleaners.yaml")
public abstract class RuleWithSeveralSuffixCleaners implements RuleSample {
    void f(String x) {}
    void clean1(String x) {}
    void clean2(String x) {}

    final static class PositiveSimple extends RuleWithSeveralSuffixCleaners {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
        }
    }

    final static class NegativeWithClean1 extends RuleWithSeveralSuffixCleaners {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            clean1(data);
        }
    }

    final static class NegativeWithClean2 extends RuleWithSeveralSuffixCleaners {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            clean2(data);
        }
    }
}

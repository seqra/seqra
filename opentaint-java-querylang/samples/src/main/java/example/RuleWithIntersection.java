package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithIntersection.yaml")
public abstract class RuleWithIntersection implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {}

    final static class PositiveSimple extends RuleWithIntersection {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }

    final static class NegativeNoSink extends RuleWithIntersection {
        @Override
        public void entrypoint() {
            String data = src();
        }
    }

    final static class NegativeNoSource extends RuleWithIntersection {
        @Override
        public void entrypoint() {
            sink("random data");
        }
    }
}

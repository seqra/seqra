package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithSimplePass.yaml")
public abstract class RuleWithSimplePass implements RuleSample {
    String src() {
        return "tainted string";
    }

    void clean(String data) {

    }

    void pass(String data, String other) {

    }

    void sink(String data) {

    }

    void sink(String data1, String data2) {

    }

    final static class PositiveSimple extends RuleWithSimplePass {
        @Override
        public void entrypoint() {
            String data = src();
            String other = "other";
            pass(data, other);
            sink(other);
        }
    }

    final static class PositiveSimple2 extends RuleWithSimplePass {
        @Override
        public void entrypoint() {
            String data1 = src();
            String data2 = src();
            sink(data1, data2);
        }
    }
}

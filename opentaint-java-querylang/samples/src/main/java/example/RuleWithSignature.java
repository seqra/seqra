package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithSignature.yaml")
public abstract class RuleWithSignature implements RuleSample {

    void sink1(String data) {}

    void sink2(String data) {}

    void methodWithSpecificSignature1(int x, String data) {
        sink1(data);
    }

    void methodWithSpecificSignature2(String data) {
        sink2(data);
    }

    final static class PositiveSimple1 extends RuleWithSignature {
        @Override
        public void entrypoint() {
            String data = "aaa";
            methodWithSpecificSignature1(1, data);
        }
    }

    final static class PositiveSimple2 extends RuleWithSignature {
        @Override
        public void entrypoint() {
            String data = "aaa";
            methodWithSpecificSignature2(data);
        }
    }

    final static class NegativeNoSource extends RuleWithSignature {
        @Override
        public void entrypoint() {
            String data = "aaa";
            sink1(data);
        }
    }
}

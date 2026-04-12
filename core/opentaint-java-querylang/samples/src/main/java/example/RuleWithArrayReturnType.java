package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithArrayReturnType.yaml")
public abstract class RuleWithArrayReturnType implements RuleSample {

    void sink(String data) {}

    String[] methodReturningStringArray(String data) {
        sink(data);
        return new String[] { data };
    }

    int[] methodReturningIntArray(String data) {
        sink(data);
        return new int[] { 1 };
    }

    String methodReturningString(String data) {
        sink(data);
        return data;
    }

    final static class PositiveStringArrayReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringArray(data);
        }
    }

    final static class NegativeIntArrayReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningIntArray(data);
        }
    }

    final static class NegativeStringReturn extends RuleWithArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }
}

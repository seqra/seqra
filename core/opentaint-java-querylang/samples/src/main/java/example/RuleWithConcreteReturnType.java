package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithConcreteReturnType.yaml")
public abstract class RuleWithConcreteReturnType implements RuleSample {

    void sink(String data) {}

    String methodReturningString(String data) {
        sink(data);
        return data;
    }

    int methodReturningInt(String data) {
        sink(data);
        return 0;
    }

    void methodReturningVoid(String data) {
        sink(data);
    }

    final static class PositiveStringReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }

    final static class NegativeIntReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningInt(data);
        }
    }

    final static class NegativeVoidReturn extends RuleWithConcreteReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningVoid(data);
        }
    }
}

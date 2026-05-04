package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

@RuleSet("example/RuleWithGenericReturnType.yaml")
public abstract class RuleWithGenericReturnType implements RuleSample {

    void sink(String data) {}

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Object> methodReturningResponseEntityObject(String data) {
        sink(data);
        return null;
    }

    String methodReturningString(String data) {
        sink(data);
        return data;
    }

    final static class PositiveResponseEntityString extends RuleWithGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    final static class PositiveResponseEntityObject extends RuleWithGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityObject(data);
        }
    }

    final static class NegativeStringReturn extends RuleWithGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }
}

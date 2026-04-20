package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * The pattern-inside declares return type {@code ResponseEntity} (raw, unparameterized).
 * From the Task-5 probe we know opentaint's method-decl pattern ignores generic
 * specificity on the return type: the three method-decl forms (raw, parameterized
 * concrete, parameterized with array) all match. Each class below is Positive.
 */
@RuleSet("example/RuleWithRawResponseEntity.yaml")
public abstract class RuleWithRawResponseEntity implements RuleSample {

    void sink(String data) {}

    @SuppressWarnings("rawtypes")
    ResponseEntity methodReturningRawResponseEntity(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<byte[]> methodReturningResponseEntityByteArray(String data) {
        sink(data);
        return null;
    }

    final static class PositiveRaw extends RuleWithRawResponseEntity {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }

    final static class PositiveParameterizedString extends RuleWithRawResponseEntity {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    final static class PositiveParameterizedByteArray extends RuleWithRawResponseEntity {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityByteArray(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * A27. Wildcard rule pattern with nested-generic and class-mismatch coverage.
 *
 * <p>Rule pattern: {@code ResponseEntity<?> $METHOD(..., String $A, ...)}.
 * The wildcard accepts every parameterization of {@code ResponseEntity},
 * including nested generics like {@code ResponseEntity<List<String>>} and
 * {@code ResponseEntity<Map<String, Integer>>}. The class portion of the
 * pattern still narrows: methods that return a non-{@code ResponseEntity}
 * type ({@code List<String>}, {@code String}) must NOT match.</p>
 */
@RuleSet("example/RuleWithWildcardPatternNestedAndClassMismatch.yaml")
public abstract class RuleWithWildcardPatternNestedAndClassMismatch implements RuleSample {

    void sink(String data) {}

    ResponseEntity<List<String>> methodReturningResponseEntityListString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Map<String, Integer>> methodReturningResponseEntityMapStringInteger(String data) {
        sink(data);
        return null;
    }

    List<String> methodReturningListString(String data) {
        sink(data);
        return null;
    }

    String methodReturningString(String data) {
        sink(data);
        return null;
    }

    final static class PositiveNestedListString extends RuleWithWildcardPatternNestedAndClassMismatch {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityListString(data);
        }
    }

    final static class PositiveNestedMapStringInteger extends RuleWithWildcardPatternNestedAndClassMismatch {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityMapStringInteger(data);
        }
    }

    /**
     * The pattern's class portion is {@code ResponseEntity}; a method
     * returning {@code List<String>} has a different erased class name and
     * must NOT match — the wildcard only loosens the type-argument slot,
     * not the class name.
     */
    final static class NegativeListReturn extends RuleWithWildcardPatternNestedAndClassMismatch {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListString(data);
        }
    }

    final static class NegativeStringReturn extends RuleWithWildcardPatternNestedAndClassMismatch {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }
}

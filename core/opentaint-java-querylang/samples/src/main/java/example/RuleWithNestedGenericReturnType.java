package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * A3. Rule pattern-inside declares return type {@code ResponseEntity<List<String>>}.
 *
 * Expected behavior: only the {@code ResponseEntity<List<String>>}-returning
 * method matches; a method returning plain {@code ResponseEntity<String>}
 * should NOT match (the nested type arg differs).
 *
 * Current engine behavior: the generic specificity at the nested type-arg
 * level is ignored — both methods match. This test is EXPECTED TO FAIL
 * today with an FP on NegativeFlatGeneric.
 */
@RuleSet("example/RuleWithNestedGenericReturnType.yaml")
public abstract class RuleWithNestedGenericReturnType implements RuleSample {

    void sink(String data) {}

    ResponseEntity<List<String>> methodReturningResponseEntityListString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    final static class PositiveNestedGeneric extends RuleWithNestedGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityListString(data);
        }
    }

    /**
     * Honest Negative: rule requires {@code ResponseEntity<List<String>>}
     * but method returns {@code ResponseEntity<String>}. The engine currently
     * reports this as a match (FP).
     */
    final static class NegativeFlatGeneric extends RuleWithNestedGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * A3. Rule pattern-inside declares return type {@code ResponseEntity<List<String>>}.
 * Surprise from the matrix run: engine does not discriminate the nested-generic
 * form either — a method returning plain {@code ResponseEntity<String>} also
 * matches. Pin both as Positive here; the should-be-Negative angle is the
 * @Disabled gap test {@code `B11 ...`} in EngineGapsTest.
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
     * Pinned as Positive: the engine over-matches because the method-decl
     * return-type's generic specificity is effectively ignored today.
     */
    final static class PositiveFlatGenericPinsOverMatch extends RuleWithNestedGenericReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

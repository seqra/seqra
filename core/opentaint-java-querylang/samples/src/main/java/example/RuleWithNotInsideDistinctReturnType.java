package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A28. {@code pattern-not-inside} with a return type that differs from
 * {@code pattern-inside} must filter on its own return type.
 *
 * <p>Rule: {@code pattern-inside ResponseEntity<?> $METHOD(..., String $A, ...)}
 * combined with {@code pattern-not-inside ResponseEntity<Integer> $METHOD(...)}.
 * The two method-decl signatures share parameter shape but differ on return
 * type. The negative predicate must emit a return-type {@code IsType} clause
 * for its own signature; otherwise it would drop the return-type constraint
 * and exclude every method matching the parameter shape, masking real
 * positives.</p>
 */
@RuleSet("example/RuleWithNotInsideDistinctReturnType.yaml")
public abstract class RuleWithNotInsideDistinctReturnType implements RuleSample {

    void sink(String data) {}

    ResponseEntity<String> methodReturningString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Object> methodReturningObject(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Integer> methodReturningInteger(String data) {
        sink(data);
        return null;
    }

    /**
     * {@code <String>} return — the not-inside's return-type {@code <Integer>}
     * must NOT match here, so the rule fires.
     */
    final static class PositiveStringReturn extends RuleWithNotInsideDistinctReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString(data);
        }
    }

    /**
     * {@code <Object>} return — same reasoning.
     */
    final static class PositiveObjectReturn extends RuleWithNotInsideDistinctReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningObject(data);
        }
    }

    /**
     * {@code <Integer>} return — the not-inside excludes this method.
     */
    final static class NegativeIntegerReturn extends RuleWithNotInsideDistinctReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningInteger(data);
        }
    }
}

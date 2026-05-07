package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A25. Concrete-{@code Object} type argument — pattern
 * {@code ResponseEntity<Object> $METHOD(...)}.
 *
 * <p>{@code Object} is the upper bound of the unbounded wildcard {@code ?},
 * so a method declaring its return as {@code ResponseEntity<?>} satisfies
 * the {@code ResponseEntity<Object>} pattern; the raw form is identical to
 * {@code ResponseEntity<?>} and matches as well. Other concrete type
 * arguments such as {@code String} or {@code Integer} do NOT match.</p>
 */
@RuleSet("example/RuleWithObjectTypeArg.yaml")
public abstract class RuleWithObjectTypeArg implements RuleSample {

    void sink(String data) {}

    ResponseEntity<Object> methodReturningResponseEntityObject(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<?> methodReturningResponseEntityWildcard(String data) {
        sink(data);
        return null;
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity methodReturningRawResponseEntity(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Integer> methodReturningResponseEntityInteger(String data) {
        sink(data);
        return null;
    }

    final static class PositiveObjectMatchesObject extends RuleWithObjectTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityObject(data);
        }
    }

    /**
     * {@code ResponseEntity<?>} matches the {@code <Object>} pattern because
     * the unbounded wildcard's upper bound is {@code Object}.
     */
    final static class PositiveWildcardMatchesObject extends RuleWithObjectTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityWildcard(data);
        }
    }

    /**
     * Raw {@code ResponseEntity} is identical to {@code ResponseEntity<?>},
     * so it matches the {@code <Object>} pattern too.
     */
    final static class PositiveRawMatchesObject extends RuleWithObjectTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }

    final static class NegativeStringTypeArg extends RuleWithObjectTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    final static class NegativeIntegerTypeArg extends RuleWithObjectTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityInteger(data);
        }
    }
}

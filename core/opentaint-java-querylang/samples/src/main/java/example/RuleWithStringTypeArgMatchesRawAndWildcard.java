package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A26. Concrete-{@code String} type argument — pattern
 * {@code ResponseEntity<String> $METHOD(...)}.
 *
 * <p>Raw {@code ResponseEntity} and {@code ResponseEntity<?>} both denote
 * "any type argument", so a concrete pattern like {@code <String>} matches
 * both — the unknown type argument could be {@code String}. Other concrete
 * type arguments such as {@code Object} or {@code Integer} do NOT match.</p>
 */
@RuleSet("example/RuleWithStringTypeArgMatchesRawAndWildcard.yaml")
public abstract class RuleWithStringTypeArgMatchesRawAndWildcard implements RuleSample {

    void sink(String data) {}

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
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

    ResponseEntity<Object> methodReturningResponseEntityObject(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Integer> methodReturningResponseEntityInteger(String data) {
        sink(data);
        return null;
    }

    final static class PositiveStringMatchesString extends RuleWithStringTypeArgMatchesRawAndWildcard {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    /**
     * {@code ResponseEntity<?>} could be parameterized with {@code String}, so
     * the {@code <String>} pattern matches it.
     */
    final static class PositiveWildcardMatchesString extends RuleWithStringTypeArgMatchesRawAndWildcard {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityWildcard(data);
        }
    }

    /**
     * Raw {@code ResponseEntity} is identical to {@code ResponseEntity<?>}, so
     * the {@code <String>} pattern matches it for the same reason.
     */
    final static class PositiveRawMatchesString extends RuleWithStringTypeArgMatchesRawAndWildcard {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }

    final static class NegativeObjectTypeArg extends RuleWithStringTypeArgMatchesRawAndWildcard {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityObject(data);
        }
    }

    final static class NegativeIntegerTypeArg extends RuleWithStringTypeArgMatchesRawAndWildcard {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityInteger(data);
        }
    }
}

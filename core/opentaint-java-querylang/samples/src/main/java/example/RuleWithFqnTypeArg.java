package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A13. Fully-qualified type argument — {@code ResponseEntity<java.lang.String>}.
 *
 * Rule return type {@code ResponseEntity<java.lang.String> $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code ResponseEntity<String>} — matches
 *   (FQN vs simple name resolve to the same class).</li>
 *   <li>Negative: method returns {@code ResponseEntity<Integer>} — type arg
 *   does not match; rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithFqnTypeArg.yaml")
public abstract class RuleWithFqnTypeArg implements RuleSample {

    void sink(String data) {}

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<Integer> methodReturningResponseEntityInteger(String data) {
        sink(data);
        return null;
    }

    final static class PositiveStringMatchesFqn extends RuleWithFqnTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    /**
     * Honest Negative: type arg {@code Integer} does not match the required
     * {@code java.lang.String}.
     */
    final static class NegativeIntegerTypeArg extends RuleWithFqnTypeArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityInteger(data);
        }
    }
}

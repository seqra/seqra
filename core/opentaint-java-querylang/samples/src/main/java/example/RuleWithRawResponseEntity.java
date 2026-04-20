package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A6. Rule pattern-inside declares raw return type {@code ResponseEntity}.
 *
 * Expected behavior: only methods with a raw (unparameterized)
 * {@code ResponseEntity} return type match; parameterized forms
 * ({@code ResponseEntity<String>}, {@code ResponseEntity<byte[]>}) should
 * NOT match.
 *
 * Current engine behavior: the method-decl return type in the pattern is
 * compared via erased class name, so raw and parameterized forms collapse
 * to the same thing — all three method-decl forms match. This test is
 * EXPECTED TO FAIL today with FPs on both NegativeParameterizedString and
 * NegativeParameterizedByteArray. The Task-5 probe surfaced this; the
 * failure here pins the expectation that raw vs parameterized should be
 * distinguishable at the method-decl return type position.
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

    /**
     * Honest Negative: rule requires raw {@code ResponseEntity} but method
     * returns {@code ResponseEntity<String>}. The engine currently reports
     * this as a match (FP) because raw and parameterized forms share an
     * erased class name.
     */
    final static class NegativeParameterizedString extends RuleWithRawResponseEntity {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    /**
     * Honest Negative: rule requires raw {@code ResponseEntity} but method
     * returns {@code ResponseEntity<byte[]>}. The engine currently reports
     * this as a match (FP).
     */
    final static class NegativeParameterizedByteArray extends RuleWithRawResponseEntity {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityByteArray(data);
        }
    }
}

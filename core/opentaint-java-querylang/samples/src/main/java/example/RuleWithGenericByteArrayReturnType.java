package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A1. Rule pattern-inside declares return type {@code ResponseEntity<byte[]>}.
 *
 * Expected behavior: only the byte-array-returning method matches; the
 * String-returning method should NOT match (specificity on concrete type arg).
 *
 * Current engine behavior: the method-decl return-type generic specificity
 * is ignored at the concrete-type-argument level — both methods match. This
 * test is EXPECTED TO FAIL today with an FP on NegativeResponseEntityString;
 * the failure honestly documents a gap introduced by this branch's
 * type-matching feature.
 */
@RuleSet("example/RuleWithGenericByteArrayReturnType.yaml")
public abstract class RuleWithGenericByteArrayReturnType implements RuleSample {

    void sink(String data) {}

    ResponseEntity<byte[]> methodReturningResponseEntityByteArray(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    final static class PositiveResponseEntityByteArray extends RuleWithGenericByteArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityByteArray(data);
        }
    }

    /**
     * Honest Negative: rule requires {@code ResponseEntity<byte[]>} but the
     * method returns {@code ResponseEntity<String>}. The engine currently
     * reports this as a match (FP); fixing this requires deeper concrete
     * type-arg discrimination on method-decl return types.
     */
    final static class NegativeResponseEntityString extends RuleWithGenericByteArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

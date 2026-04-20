package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

/**
 * A1. Rule pattern-inside declares return type {@code ResponseEntity<byte[]>}.
 * Surprise from the matrix run: even the concrete-generic case does not
 * discriminate ResponseEntity&lt;String&gt; away — the method-decl pattern's
 * return-type specificity is effectively ignored at generic level today.
 *
 * Both inner classes are therefore Positive and the test pins that behavior.
 * The "Negative" angle (specificity discriminates byte[] from String at
 * method-decl-return level) is covered by the @Disabled gap test in
 * EngineGapsTest.
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
     * Pinned as Positive because the engine does not discriminate by the
     * specific concrete type argument at method-decl return position today.
     * See {@code EngineGapsTest.`B11 ...`} for the @Disabled expectation that
     * this SHOULD be Negative.
     */
    final static class PositiveResponseEntityStringPinsOverMatch extends RuleWithGenericByteArrayReturnType {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

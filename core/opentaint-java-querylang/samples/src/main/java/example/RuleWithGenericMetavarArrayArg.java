package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

@RuleSet("example/RuleWithGenericMetavarArrayArg.yaml")
public abstract class RuleWithGenericMetavarArrayArg implements RuleSample {

    void sink(String data) {}

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<byte[]> methodReturningResponseEntityByteArray(String data) {
        sink(data);
        return null;
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity methodReturningRawResponseEntity(String data) {
        sink(data);
        return null;
    }

    final static class PositiveResponseEntityString extends RuleWithGenericMetavarArrayArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    final static class PositiveResponseEntityByteArray extends RuleWithGenericMetavarArrayArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityByteArray(data);
        }
    }

    /**
     * The metavar type-arg pattern {@code ResponseEntity<$T>} matches the same
     * set of types as {@code ResponseEntity<?>} and the raw form, so a raw use
     * of {@code ResponseEntity} matches.
     */
    final static class PositiveRawResponseEntity extends RuleWithGenericMetavarArrayArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }
}

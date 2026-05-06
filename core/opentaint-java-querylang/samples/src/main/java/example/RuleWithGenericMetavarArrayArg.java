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
     * The rule pattern is {@code ResponseEntity<$T>}, which requires a concrete
     * type argument. A raw use of {@code ResponseEntity} (no type argument)
     * therefore must NOT match.
     */
    final static class NegativeRawResponseEntity extends RuleWithGenericMetavarArrayArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }
}

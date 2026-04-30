package example;

import base.RuleSample;
import base.RuleSet;
import base.TaintRuleFalsePositive;
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

    /**
     * Raw ResponseEntity. Note: pattern is ResponseEntity<$T>, so whether this fires
     * depends on whether the engine considers raw as unifiable with a type-arg metavar.
     */
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
     * In opentaint the method-decl pattern's return-type check is effectively ignored
     * on raw vs. parameterized, so raw ResponseEntity DOES get matched by
     * ResponseEntity&lt;$T&gt;. We treat this as a Positive to pin the current behavior.
     */
    final static class PositiveRawResponseEntity extends RuleWithGenericMetavarArrayArg {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }
}

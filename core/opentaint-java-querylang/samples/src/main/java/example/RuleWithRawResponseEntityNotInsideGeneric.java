package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@RuleSet("example/RuleWithRawResponseEntityNotInsideGeneric.yaml")
public abstract class RuleWithRawResponseEntityNotInsideGeneric implements RuleSample {

    void sink(String data) {}

    @SuppressWarnings("rawtypes")
    @GetMapping("/raw")
    ResponseEntity rawResponseEntity(String data) {
        sink(data);
        return null;
    }

    @GetMapping("/parametrized")
    ResponseEntity<String> parametrizedResponseEntity(String data) {
        sink(data);
        return null;
    }

    final static class PositiveRawResponseEntity extends RuleWithRawResponseEntityNotInsideGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            rawResponseEntity(data);
        }
    }

    final static class NegativeParametrizedResponseEntity extends RuleWithRawResponseEntityNotInsideGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            parametrizedResponseEntity(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import org.springframework.http.ResponseEntity;

@RuleSet("example/RuleWithWildcardGeneric.yaml")
public abstract class RuleWithWildcardGeneric implements RuleSample {

    void sink(String data) {}

    ResponseEntity<?> methodReturningResponseEntityWildcard(String data) {
        sink(data);
        return null;
    }

    ResponseEntity<String> methodReturningResponseEntityString(String data) {
        sink(data);
        return null;
    }

    /**
     * Wildcard ResponseEntity&lt;?&gt; is a valid Java construct that the rule
     * pattern also expresses. Keeping it as a Positive to pin the current behavior.
     */
    final static class PositiveWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityWildcard(data);
        }
    }

    /**
     * ResponseEntity&lt;String&gt; is a concrete parameterized form and must not
     * match a wildcard &lt;?&gt; type argument in the rule pattern.
     */
    final static class NegativeConcreteDoesNotMatch extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

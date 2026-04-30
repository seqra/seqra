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
     * Wildcard ResponseEntity&lt;?&gt; trivially matches the &lt;?&gt; rule
     * pattern.
     */
    final static class PositiveWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityWildcard(data);
        }
    }

    /**
     * ResponseEntity&lt;String&gt; is a concrete parameterization. Java's
     * unbounded wildcard `?` is the supertype of any `X`, so `&lt;?&gt;`
     * accepts any concrete type argument — `ResponseEntity&lt;String&gt;`
     * matches.
     */
    final static class PositiveConcreteMatchesWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }
}

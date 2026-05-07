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

    ResponseEntity<Object> methodReturningResponseEntityObject(String data) {
        sink(data);
        return null;
    }

    @SuppressWarnings("rawtypes")
    ResponseEntity methodReturningRawResponseEntity(String data) {
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
     * ResponseEntity&lt;String&gt; is a concrete parameterization. The
     * unbounded wildcard pattern `&lt;?&gt;` matches every parameterization
     * of {@code ResponseEntity}, so this method matches.
     */
    final static class PositiveConcreteMatchesWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityString(data);
        }
    }

    /**
     * ResponseEntity&lt;Object&gt; — the wildcard pattern `&lt;?&gt;` matches
     * every parameterization, including {@code Object}.
     */
    final static class PositiveObjectMatchesWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningResponseEntityObject(data);
        }
    }

    /**
     * Raw {@code ResponseEntity} — the pattern `ResponseEntity&lt;?&gt;` and
     * the raw form {@code ResponseEntity} have identical meaning, so the raw
     * use is matched by the wildcard pattern.
     */
    final static class PositiveRawMatchesWildcard extends RuleWithWildcardGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningRawResponseEntity(data);
        }
    }
}

package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.Builder;

/**
 * Engine bug: pattern-not-inside ignored for chained-expression calls when the
 * pattern constrains the call arity (literal or metavariable args at a fixed
 * arity). The same pattern matches when the args list is variadic (`...`).
 *
 * Concretely, given a chain
 *   new Builder().cfg("Content-Type", "application/json").sink(taint)
 * the rule's pattern-not-inside `$X.cfg("Content-Type", "application/json")`
 * should subtract the match on `.sink($UNTRUSTED)`, but it does not. Replacing
 * the pattern with `$X.cfg(...)` does subtract — confirming the engine matches
 * `.cfg()` in chained-expression context only when the args are variadic.
 *
 * The same shape surfaced in the spring-xss-html-response-sinks rule, where
 * `.header("Content-Type", "application/json")` could not be used to subtract
 * a chained ResponseEntity builder, while `.contentType(MediaType.X)` (single
 * arg, identifier) could.
 */
@RuleSet("issues/issue98.yaml")
public abstract class issue98 implements RuleSample {
    /** No discriminator in the chain — sink should fire on the tainted value. */
    static class PositiveChainNoCfg extends issue98 {
        @Override
        public void entrypoint() {
            new Builder().sink(Builder.source());
        }
    }

    /**
     * Discriminator `.cfg("Content-Type", "application/json")` is present in
     * the chain — the rule's pattern-not-inside should subtract this match,
     * but currently does not. Disabled in IssuesTest until the engine learns
     * to match constrained-arity calls in chained-expression contexts.
     */
    static class NegativeChainWithCfg extends issue98 {
        @Override
        public void entrypoint() {
            new Builder().cfg("Content-Type", "application/json").sink(Builder.source());
        }
    }
}

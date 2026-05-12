package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.Builder;

/**
 * Engine bug: a `pattern-not-inside` whose discriminator-arg metavariable is
 * narrowed by a sibling `metavariable-pattern` is silently ignored.
 *
 * The rule (issue98.yaml):
 *   - binds `$X` via `pattern-inside: Builder $X = new Builder(); ...`
 *   - binds `$UNTRUSTED` via the positive sink `$X.sink($UNTRUSTED)`
 *   - subtracts via `pattern-not-inside: $X.cfg("Content-Type", $V)` plus
 *     `metavariable-pattern: $V matches "application/json"`
 *
 * Every metavariable in the pattern-not-inside is bound: `$X` from
 * `pattern-inside`, `$V` constrained by `metavariable-pattern`. With the
 * literal-arg form `pattern-not-inside: $X.cfg("Content-Type",
 * "application/json")` (no metavariable-pattern) the engine correctly
 * subtracts the match — this test passes. Replacing the literal arg with a
 * metavariable + `metavariable-pattern` constraint (the same shape the XSS
 * rule uses for `$CT_SAFE`) loses the subtraction.
 *
 * Expected:
 *   - PositiveStmtNoCfg fires (no `.cfg` call in scope).
 *   - NegativeStmtWithCfg is subtracted (`.cfg("Content-Type",
 *     "application/json")` matches the pattern-not-inside, and
 *     `metavariable-pattern` accepts the literal).
 *
 * Actual: NegativeStmtWithCfg also fires.
 *
 * Motivation: matches the `(HttpServletResponse $R).setContentType($CT_SAFE); …`
 * + `metavariable-pattern: $CT_SAFE ∈ {non-HTML strings}` subtractor in
 * `servlet-xss-html-response-sinks.yaml` that fails to clear the
 * `SafeChainedWriterJsonServlet` / `SafeOutputStreamOctetServlet` FPs.
 */
@RuleSet("issues/issue98.yaml")
public abstract class issue98 implements RuleSample {
    /** No discriminator call in scope — sink should fire. */
    static class PositiveStmtNoCfg extends issue98 {
        @Override
        public void entrypoint() {
            Builder b = new Builder();
            b.sink(Builder.source());
        }
    }

    /**
     * Discriminator `b.cfg("Content-Type", "application/json")` is present in
     * the method body. The pattern-not-inside + metavariable-pattern pair
     * should subtract this match — but does not. Disabled until the engine
     * honors metavariable-pattern-narrowed arguments inside
     * pattern-not-inside.
     */
    static class NegativeStmtWithCfg extends issue98 {
        @Override
        public void entrypoint() {
            Builder b = new Builder();
            b.cfg("Content-Type", "application/json");
            b.sink(Builder.source());
        }
    }
}

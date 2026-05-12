package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.Builder;

/**
 * Engine bug: pattern-not-inside is ignored when the pattern constrains a
 * call's argument shape with literal arguments, even when every metavariable
 * in the pattern-not-inside is bound by a sibling positive pattern.
 *
 * Rule (issue98.yaml) anchors a positive sink `$X.sink($UNTRUSTED)` inside a
 * void method scope and tries to subtract matches where the same receiver
 * `$X` also has a `$X.cfg("Content-Type", "application/json")` call somewhere
 * in the method body. `$X` is bound by the sink pattern; no free
 * metavariables remain in the pattern-not-inside.
 *
 * Expected:
 *   - PositiveStmtNoCfg fires (no .cfg call in scope).
 *   - NegativeStmtWithCfg is subtracted (`.cfg("Content-Type",
 *     "application/json")` matches the pattern-not-inside).
 *
 * Actual: NegativeStmtWithCfg also fires — the pattern-not-inside subtraction
 * is silently ignored.
 *
 * This is the same family as issue90 ("ignored pattern-not-inside") but
 * confirmed with the metavariable explicitly wired through a sibling positive
 * pattern, ruling out a "free metavariable" interpretation. Motivation:
 * matches the `(HttpServletResponse $R).setContentType("application/json"); …`
 * subtractor in `servlet-xss-html-response-sinks.yaml` that fails to clear
 * the `SafeChainedWriterJsonServlet` / `SafeOutputStreamOctetServlet` FPs.
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
     * Discriminator `b.cfg("Content-Type", "application/json")` appears in the
     * method body. The pattern-not-inside should subtract this match — but
     * does not. Disabled until the engine honors the subtraction.
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

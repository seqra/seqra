package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i99.Response;
import issues.i99.Writer;

/**
 * Positive control for the `"$V"` + `metavariable-regex` subtractor shape.
 *
 * Verifies that in an isolated `mode: taint` rule with the same structural
 * elements found in `servlet-xss-html-response-sinks.yaml`:
 *   - outer `pattern-inside` binding `$R` at the method-parameter position
 *   - `metavariable-pattern` narrowing `$METHOD` to a name set
 *   - inner `pattern-inside` binding `$W = $R.getWriter(); ...`
 *   - subtractor `pattern-not-inside: $R.setHtmlType("$V"); ...` plus
 *     sibling `metavariable-regex` on `$V`
 * the engine correctly honors the regex constraint and discriminates
 * `text/html` (positive) from `application/json` (negative).
 *
 * Probed variants that all pass:
 *   - 1-arg call (`setHtmlType("$V")`), 2-arg call (`cfg("Content-Type", "$V")`)
 *   - single-line subtractor (`pattern-not-inside: $R.setHtmlType("$V")`)
 *     vs multi-line (`pattern-not-inside: | ...setHtmlType("$V"); ...`)
 *   - assignment-bound `$R` (`$R = new Response();`) vs parameter-bound
 *     (`void doStuff(Response $R)`) outer scope
 *   - cast vs no-cast receivers (`(Response $R)` vs `$R`)
 *   - extra sibling `pattern-not-inside` entries for `MediaType.X_VALUE`-style
 *     constants
 *   - `pattern-sanitizers` added to the sink-lib rule
 *
 * The actual `servlet-xss-html-response-sinks.yaml` rule with the same
 * subtractor shape OVER-subtracts (4 FPs cleared, 8 TPs flip to FN). The
 * bug therefore lives in some structural element of that rule that isn't
 * captured here — likely the `mode: join` parent rule wrapping the sink
 * lib (the engine-test framework doesn't support cross-file join refs, so
 * that variant couldn't be reproduced).
 */
@RuleSet("issues/issue99.yaml")
public abstract class issue99 implements RuleSample {
    /** Content type is `text/html` — pattern-not-inside regex does NOT match, sink fires. */
    static class PositiveHtmlType extends issue99 {
        @Override
        public void entrypoint() {
            doStuff(new Response());
        }

        void doStuff(Response r) {
            r.setHtmlType("text/html");
            Writer w = r.getWriter();
            w.write(Response.source());
        }
    }

    /** Content type is `application/json` — pattern-not-inside regex matches, sink subtracted. */
    static class NegativeJsonType extends issue99 {
        @Override
        public void entrypoint() {
            doStuff(new Response());
        }

        void doStuff(Response r) {
            r.setHtmlType("application/json");
            Writer w = r.getWriter();
            w.write(Response.source());
        }
    }
}

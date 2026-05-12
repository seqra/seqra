package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i99.Response;
import issues.i99.Writer;

/**
 * Engine bug: a sibling `pattern-inside` at the wrong indentation level
 * (placed alongside `pattern-either` instead of inside it) interacts
 * pathologically with `pattern-not-inside` + `metavariable-regex` and
 * causes the subtractor to over-fire — even on samples whose content
 * does not match the subtractor's regex.
 *
 * Rule shape (issue99.yaml):
 *   - patterns:
 *       - patterns:                # entrypoint constraint
 *           - pattern-either:
 *             - pattern-inside: $RT $METHOD(..., Response $R, ...) { ... }
 *           - pattern-inside: $RT $METHOD(..., Response2 $R, ...) { ... }  # sibling-not-child
 *           - metavariable-pattern: $METHOD ∈ {doStuff}
 *       - patterns:                # sink shape
 *           - pattern-inside: $W = $R.getWriter(); ...
 *           - pattern: $W.write($UNTRUSTED)
 *       - patterns:                # subtractor
 *           - pattern-not-inside: $R.setHtmlType("$V"); ...
 *           - metavariable-regex: $V matches '^application/json$'
 *       - focus-metavariable: $UNTRUSTED
 *
 * Expected:
 *   - PositiveHtmlType fires (`setHtmlType("text/html")` doesn't match
 *     the JSON regex; subtraction must NOT apply).
 *   - NegativeJsonType is subtracted (regex matches).
 *
 * Actual: PositiveHtmlType is also subtracted — the
 * `metavariable-regex` constraint is dropped and the subtractor matches
 * every flow regardless of the literal content-type argument.
 *
 * Without the bug-trigger sibling `pattern-inside` for `Response2`, the
 * same rule discriminates correctly. Was reproduced in two layers
 * during diagnosis of the `servlet-xss-html-response-sinks.yaml`
 * `SafeChainedWriterJsonServlet` / `SafeOutputStreamOctetServlet` FPs:
 *   1. The XSS rule itself has the same sibling-at-wrong-indent shape
 *      in its `javax`/`jakarta` entrypoint pattern-either (the jakarta
 *      `pattern-inside` is at the outer level, not inside the OR).
 *   2. Reproducing that shape in `i99-probe-sink.yaml` (analyzer
 *      pipeline) flipped PositiveHtmlType to FN; removing the sibling
 *      restored correct discrimination.
 *   3. Mirroring it here in the engine-test framework reproduces the
 *      same over-subtraction.
 */
@RuleSet("issues/issue99.yaml")
public abstract class issue99 implements RuleSample {
    /** Content type is `text/html` — subtractor regex must NOT match, sink should fire. */
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

    /** Content type is `application/json` — subtractor regex matches, sink subtracted. */
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

package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Repro: a pattern-sanitizer written with a TYPED receiver on an instance method
 * — `(taint.InstanceSanitizerRepro.Box $B).sanitize()` — is not honored, so the
 * sanitized value is still reported as tainted. Mirrors the rules-level FP where
 * `(java.io.File $F).getCanonicalFile()` fails to sanitize path-traversal taint.
 *
 * The rule declares a propagator so taint flows into `sanitize()`'s result, and a
 * pattern-sanitizer that should make that result clean. Positive must be flagged;
 * Negative must NOT be flagged if the typed-receiver instance sanitizer is honored.
 */
@RuleSet("taint/InstanceSanitizerRepro.yaml")
public abstract class InstanceSanitizerRepro implements RuleSample {

    String src() {
        return "tainted";
    }

    void sink(Object data) {
    }

    static final class Box {
        private final String v;

        Box(String v) {
            this.v = v;
        }

        String sanitize() {
            return v;
        }
    }

    // Taint reaches the sink unsanitized -> must be reported.
    static final class Positive extends InstanceSanitizerRepro {
        @Override
        public void entrypoint() {
            String data = src();
            Box b = new Box(data);
            sink(b);
        }
    }

    // Taint passes through the typed-receiver instance-method sanitizer -> must NOT
    // be reported. If the sanitizer is not honored (the bug), this is flagged.
    static final class Negative extends InstanceSanitizerRepro {
        @Override
        public void entrypoint() {
            String data = src();
            Box b = new Box(data);
            String clean = b.sanitize();
            sink(clean);
        }
    }
}

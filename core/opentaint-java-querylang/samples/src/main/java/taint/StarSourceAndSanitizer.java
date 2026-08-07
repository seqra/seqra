package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SOURCE + starred SANITIZER: `$*X = src()` taints the whole object and every nested
 * field; `clean($*C)` must clear the whole object including nested fields. A depth-4 field read
 * follows. Uses AnyAccessorEnabled so the source star reaches the concrete field read.
 */
@RuleSet("taint/StarSourceAndSanitizer.yaml")
public abstract class StarSourceAndSanitizer implements RuleSample {
    L0 src() { return new L0(); }
    L0 clean(L0 b) { return b; }
    void sink(String data) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public String v; }

    // Positive: starred-source field taint reaches the sink with NO sanitizer between.
    final static class PositiveDeepUnsanitized extends StarSourceAndSanitizer {
        @Override public void entrypoint() {
            L0 o = src();                 // $*X whole-object taint
            String v = o.f.f.f.v;         // depth-4 read
            sink(v);
        }
    }

    // Negative: the starred sanitizer clears the whole-object taint before the field read.
    final static class NegativeDeepSanitized extends StarSourceAndSanitizer {
        @Override public void entrypoint() {
            L0 o = src();
            L0 cleaned = clean(o);        // $*C clears object + all nested fields
            String v = cleaned.f.f.f.v;
            sink(v);
        }
    }
}

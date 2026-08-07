package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SANITIZER, taint hidden 5 fields deep. `clean($*C)` must clear the taint on the
 * whole object INCLUDING nested fields at every depth, so a subsequent depth-5 field read is
 * clean. Default AnyAccessorDisabled (matches StarSanitizer).
 */
@RuleSet("taint/StarDeepSanitizer.yaml")
public abstract class StarDeepSanitizer implements RuleSample {
    String src() { return "tainted"; }
    L0 clean(L0 b) { return b; }   // $*C sanitizer: clears object + all fields at all depths
    void sink(String data) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    private static L0 build() {
        L0 o = new L0();
        o.f = new L1();
        o.f.f = new L2();
        o.f.f.f = new L3();
        o.f.f.f.f = new L4();
        return o;
    }

    // Positive: tainted depth-5 field reaches the sink with NO sanitizer between.
    final static class PositiveTaintedDeep extends StarDeepSanitizer {
        @Override public void entrypoint() {
            L0 o = build();
            o.f.f.f.f.v = src();
            sink(o.f.f.f.f.v);
        }
    }

    // Negative: the $*C sanitizer must clean the depth-5 field taint on the returned object.
    final static class NegativeSanitizedDeep extends StarDeepSanitizer {
        @Override public void entrypoint() {
            L0 o = build();
            o.f.f.f.f.v = src();
            L0 cleaned = clean(o);
            sink(cleaned.f.f.f.f.v);   // depth-5 field taint must be gone after $*C
        }
    }
}

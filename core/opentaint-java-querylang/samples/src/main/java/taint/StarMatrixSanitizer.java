package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SANITIZER, 5+ interprocedural depth x 5+ field depth combined. A starred source five
 * calls deep taints a whole L0. On the sanitized path the object goes through `sanitize()` — a
 * HELPER whose body calls the starred-clean `clean()` (the wrapper shape behind the OWASP
 * escapeHtml FPs, i.e. the deep-mark-exclusion fix's sample-level regression test). Afterwards
 * five hops unwrap one field level each and the scalar travels five calls down to the sink;
 * the whole-object clean must have removed the any-field taint at every depth.
 */
@RuleSet("taint/StarMatrixSanitizer.yaml")
public abstract class StarMatrixSanitizer implements RuleSample {
    L0 src() { return new L0(); }
    L0 clean(L0 o) { return o; }
    void sink(String s) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    // Source five calls deep.
    protected L0 src5() { return src4(); }
    protected L0 src4() { return src3(); }
    protected L0 src3() { return src2(); }
    protected L0 src2() { return src1(); }
    protected L0 src1() { L0 o = src(); return o; }   // $*X = src() matches HERE, depth 5

    // The starred clean sits INSIDE a wrapper: its whole-object effect must survive the
    // wrapper's interprocedural summary (deep mark exclusions).
    protected L0 sanitize(L0 o) { return clean(o); }

    // Five hops, each unwrapping exactly one field level.
    protected L1 u1(L0 o) { return o.f; }
    protected L2 u2(L1 o) { return o.f; }
    protected L3 u3(L2 o) { return o.f; }
    protected L4 u4(L3 o) { return o.f; }
    protected String u5(L4 o) { return o.v; }

    // Sink five calls deep.
    protected void k1(String s) { k2(s); }
    protected void k2(String s) { k3(s); }
    protected void k3(String s) { k4(s); }
    protected void k4(String s) { k5(s); }
    protected void k5(String s) { sink(s); }          // sink() called HERE, depth 5

    // Positive: the unsanitized path flags.
    final static class PositiveUnsanitizedDeep extends StarMatrixSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            String s = u5(u4(u3(u2(u1(o)))));
            k1(s);
        }
    }

    // Negative: the wrapped whole-object clean clears the taint at every field depth.
    final static class NegativeSanitizedDeep extends StarMatrixSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            L0 c = sanitize(o);
            String s = u5(u4(u3(u2(u1(c)))));
            k1(s);
        }
    }
}

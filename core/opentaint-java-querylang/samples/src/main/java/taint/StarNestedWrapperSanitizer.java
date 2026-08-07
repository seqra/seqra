package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Summary COMPOSITION regressions for whole-object sanitizer cleans (deep mark exclusions):
 * every way a starred clean can hide behind summary levels must stay effective.
 *
 * - NegativeSanitizedTwoWrappers: the clean under TWO nested wrapper summaries
 *   (sanitize2 -> sanitize1 -> clean), unwrap flow in the caller — the deep exclusion on each
 *   summary's initial fact prunes the caller's whole-object mark at delta application.
 * - NegativeSanitizedInHelper / NegativeSanitizedNested: the clean AND the sinkward unwrap
 *   flow inside a summarized helper — the exclusion must survive being carried through the
 *   helper's own summary. This requires monotone (union, not replace) initial-fact exclusion
 *   refinement and the deep-entry carry on the delta-application path
 *   (MethodCallSummaryHandler); with lossy replace semantics a later application through an
 *   exclusion-free passthrough edge downgraded the refined initial and resurrected the
 *   cleaned mark — the historic false positive here.
 */
@RuleSet("taint/StarNestedWrapperSanitizer.yaml")
public abstract class StarNestedWrapperSanitizer implements RuleSample {
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

    // The starred clean under TWO wrapper summaries.
    protected L0 sanitize1(L0 o) { return clean(o); }
    protected L0 sanitize2(L0 o) { return sanitize1(o); }

    // Five hops, each unwrapping exactly one field level.
    protected L1 u1(L0 o) { return o.f; }
    protected L2 u2(L1 o) { return o.f; }
    protected L3 u3(L2 o) { return o.f; }
    protected L4 u4(L3 o) { return o.f; }
    protected String u5(L4 o) { return o.v; }

    // The whole sanitized flow inside one more summarized helper.
    protected String helper(L0 o) {
        L0 c = sanitize2(o);
        return u5(u4(u3(u2(u1(c)))));
    }

    // Sink five calls deep.
    protected void k1(String s) { k2(s); }
    protected void k2(String s) { k3(s); }
    protected void k3(String s) { k4(s); }
    protected void k4(String s) { k5(s); }
    protected void k5(String s) { sink(s); }          // sink() called HERE, depth 5

    // Positive: the unsanitized path flags.
    final static class PositiveUnsanitizedNested extends StarNestedWrapperSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            String s = u5(u4(u3(u2(u1(o)))));
            k1(s);
        }
    }

    // Negative: clean + unwrap flow inside a summarized helper, two wrapper levels above the
    // clean.
    final static class NegativeSanitizedNested extends StarNestedWrapperSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            String s = helper(o);
            k1(s);
        }
    }

    // Negative: two wrapper levels, flow at the entrypoint — the deep exclusion composes
    // across nested wrapper summaries.
    final static class NegativeSanitizedTwoWrappers extends StarNestedWrapperSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            L0 c = sanitize2(o);
            String s = u5(u4(u3(u2(u1(c)))));
            k1(s);
        }
    }

    // Negative: ONE wrapper level (like StarMatrixSanitizer), with the sanitized flow itself
    // inside a summarized helper.
    protected String helperOneWrapper(L0 o) {
        L0 c = sanitize1(o);
        return u5(u4(u3(u2(u1(c)))));
    }

    final static class NegativeSanitizedInHelper extends StarNestedWrapperSanitizer {
        @Override public void entrypoint() {
            L0 o = src5();
            String s = helperOneWrapper(o);
            k1(s);
        }
    }
}

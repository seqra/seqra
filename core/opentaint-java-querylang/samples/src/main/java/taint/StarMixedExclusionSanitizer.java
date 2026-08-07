package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Mixed exclusion kinds on ONE flow: a starred whole-object clean (deep exclusions on the
 * wrapper summary's initial fact) combined with a plain value clean (depth-1 exclusion via an
 * ordinary sanitizer inside another summarized helper). The same caller initial fact is
 * refined by BOTH summary applications; with lossy replace semantics the later plain
 * refinement dropped the accumulated deep entry and could resurrect the cleaned whole-object
 * mark (the always-propagate-deep-marks regression).
 */
@RuleSet("taint/StarMixedExclusionSanitizer.yaml")
public abstract class StarMixedExclusionSanitizer implements RuleSample {
    L0 src() { return new L0(); }
    L0 cleanAll(L0 o) { return o; }
    String cleanValue(String s) { return s; }
    void sink(String s) {}

    static final class L0 { public L1 f; }
    static final class L1 { public String v; }

    protected L0 srcWrapped() { L0 o = src(); return o; }

    // Starred clean behind a wrapper summary: the summary's initial fact acquires the DEEP
    // exclusion.
    protected L0 sanitizeAll(L0 o) { return cleanAll(o); }

    // Plain clean behind a wrapper summary: the refinement carries a PLAIN (depth-1) part.
    protected String sanitizeValue(String s) { return cleanValue(s); }

    protected String unwrap(L0 o) { return o.f.v; }

    // Starred clean + constant store into the cleaned region inside ONE summarized helper:
    // the deep exclusion and the safe store must compose — the store must not resurrect the
    // cleaned whole-object mark on the returned object. Two store depths: the String leaf
    // and the field itself (killing the whole subtree under f).
    protected L0 sanitizeAllAndAssign(L0 o) {
        cleanAll(o);
        o.f.v = "safe";
        return o;
    }

    protected L0 sanitizeAllAndAssignField(L0 o) {
        cleanAll(o);
        o.f = new L1();
        return o;
    }

    // Positive: no sanitizer on the path.
    final static class PositiveUnsanitized extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            sink(unwrap(o));
        }
    }

    // Negative: starred clean, then the SAME flow continues through the plain-sanitizer
    // summary as well — the later mixed refinement must keep the deep entry.
    final static class NegativeStarThenPlain extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            L0 c = sanitizeAll(o);
            String s = unwrap(c);
            String t = sanitizeValue(s);
            sink(t);
        }
    }

    // Negative: starred clean alone through the wrapper — deep exclusion baseline.
    final static class NegativeStarOnly extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            L0 c = sanitizeAll(o);
            sink(unwrap(c));
        }
    }

    // Negative: starred clean followed by a constant store into the cleaned region, both
    // behind one helper summary.
    final static class NegativeStarThenAssign extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            L0 c = sanitizeAllAndAssign(o);
            sink(unwrap(c));
        }
    }

    // Negative: starred clean followed by a field-level overwrite (fresh subtree), both
    // behind one helper summary.
    final static class NegativeStarThenAssignField extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            L0 c = sanitizeAllAndAssignField(o);
            sink(unwrap(c));
        }
    }

    // Negative: plain clean alone — depth-1 exclusion baseline.
    final static class NegativePlainOnly extends StarMixedExclusionSanitizer {
        @Override public void entrypoint() {
            L0 o = srcWrapped();
            String s = unwrap(o);
            sink(sanitizeValue(s));
        }
    }
}

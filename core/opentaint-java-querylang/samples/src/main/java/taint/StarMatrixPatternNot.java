package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred PATTERN-NOT sink, 5+ interprocedural depth x 5+ field depth combined. The sink is
 * `emit($*Y, $MODE)` with `pattern-not: emit($*Y, "safe")` — the starred metavar occurrence
 * appears in BOTH the pattern and the pattern-not (the constraint solver keeps $Y and $*Y
 * distinct, so the forms must agree). A starred source five calls deep taints a whole L0; the
 * object travels five hops and is emitted five calls deep — flagged in "html" mode, excluded
 * by the pattern-not in "safe" mode.
 */
@RuleSet("taint/StarMatrixPatternNot.yaml")
public abstract class StarMatrixPatternNot implements RuleSample {
    L0 src() { return new L0(); }
    void emit(L0 o, String mode) {}

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

    // Five object pass-hops.
    protected L0 p1(L0 o) { return o; }
    protected L0 p2(L0 o) { return o; }
    protected L0 p3(L0 o) { return o; }
    protected L0 p4(L0 o) { return o; }
    protected L0 p5(L0 o) { return o; }

    // Two sink chains five calls deep: one emits in a flagged mode, one in the excluded mode.
    protected void k1(L0 o) { k2(o); }
    protected void k2(L0 o) { k3(o); }
    protected void k3(L0 o) { k4(o); }
    protected void k4(L0 o) { k5(o); }
    protected void k5(L0 o) { emit(o, "html"); }      // matches the sink, depth 5

    protected void j1(L0 o) { j2(o); }
    protected void j2(L0 o) { j3(o); }
    protected void j3(L0 o) { j4(o); }
    protected void j4(L0 o) { j5(o); }
    protected void j5(L0 o) { emit(o, "safe"); }      // excluded by pattern-not, depth 5

    // Positive: tainted object emitted in a non-excluded mode.
    final static class PositiveEmitHtml extends StarMatrixPatternNot {
        @Override public void entrypoint() {
            L0 o = src5();
            k1(p5(p4(p3(p2(p1(o))))));
        }
    }

    // Negative: same tainted object, but the emit call matches the pattern-not.
    final static class NegativeEmitSafe extends StarMatrixPatternNot {
        @Override public void entrypoint() {
            L0 o = src5();
            j1(p5(p4(p3(p2(p1(o))))));
        }
    }
}

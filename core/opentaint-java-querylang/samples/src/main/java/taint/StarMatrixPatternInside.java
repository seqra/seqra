package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred sink gated by PATTERN-INSIDE, 5+ interprocedural depth x 5+ field depth combined.
 * The sink `$R.consume($*Y)` only counts when the receiver comes from `openSink()` in the same
 * method (pattern-inside). A starred source five calls deep taints a whole L0; the object
 * travels five hops; the consume call sits five calls deep. The gated method uses openSink()
 * (flagged); the ungated one obtains its receiver elsewhere (not a sink at all).
 */
@RuleSet("taint/StarMatrixPatternInside.yaml")
public abstract class StarMatrixPatternInside implements RuleSample {
    L0 src() { return new L0(); }
    Out openSink() { return new Out(); }
    Out plainOut() { return new Out(); }

    static final class Out { void consume(L0 o) {} }

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

    // Sink chain five calls deep, ending in the pattern-inside-gated consume.
    protected void k1(L0 o) { k2(o); }
    protected void k2(L0 o) { k3(o); }
    protected void k3(L0 o) { k4(o); }
    protected void k4(L0 o) { k5(o); }
    protected void k5(L0 o) {
        Out r = openSink();     // pattern-inside context
        r.consume(o);           // starred sink matches HERE, depth 5
    }

    // Same-depth chain whose consume receiver does NOT come from openSink().
    protected void j1(L0 o) { j2(o); }
    protected void j2(L0 o) { j3(o); }
    protected void j3(L0 o) { j4(o); }
    protected void j4(L0 o) { j5(o); }
    protected void j5(L0 o) {
        Out r = plainOut();     // no pattern-inside context
        r.consume(o);
    }

    // Positive: tainted object consumed inside the gated context.
    final static class PositiveGatedConsume extends StarMatrixPatternInside {
        @Override public void entrypoint() {
            L0 o = src5();
            k1(p5(p4(p3(p2(p1(o))))));
        }
    }

    // Negative: same tainted object, but the consume call lacks the pattern-inside context.
    final static class NegativeUngatedConsume extends StarMatrixPatternInside {
        @Override public void entrypoint() {
            L0 o = src5();
            j1(p5(p4(p3(p2(p1(o))))));
        }
    }
}

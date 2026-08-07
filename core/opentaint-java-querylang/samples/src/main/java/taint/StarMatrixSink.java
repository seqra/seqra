package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SINK, 5+ interprocedural depth x 5+ field depth combined. A starred source taints the
 * INNERMOST object (L4) five calls deep; five hops then each WRAP it one level deeper
 * (L4->L3->..->L0), and the outermost object travels five calls down a sink chain to
 * `sink($*Y)` — the starred sink must observe the whole-object taint buried five field levels
 * down the wrapped object.
 */
@RuleSet("taint/StarMatrixSink.yaml")
public abstract class StarMatrixSink implements RuleSample {
    L4 src() { return new L4(); }
    void sink(L0 o) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    // Source five calls deep: the starred source statement is inside src1.
    protected L4 src5() { return src4(); }
    protected L4 src4() { return src3(); }
    protected L4 src3() { return src2(); }
    protected L4 src2() { return src1(); }
    protected L4 src1() { L4 o = src(); return o; }   // $*X = src() matches HERE, depth 5

    // Five hops, each WRAPPING one field level (hide direction).
    protected L3 w1(L4 o) { L3 n = new L3(); n.f = o; return n; }
    protected L2 w2(L3 o) { L2 n = new L2(); n.f = o; return n; }
    protected L1 w3(L2 o) { L1 n = new L1(); n.f = o; return n; }
    protected L0 w4(L1 o) { L0 n = new L0(); n.f = o; return n; }
    protected L0 w5(L0 o) { return o; }

    // Sink five calls deep.
    protected void k1(L0 o) { k2(o); }
    protected void k2(L0 o) { k3(o); }
    protected void k3(L0 o) { k4(o); }
    protected void k4(L0 o) { k5(o); }
    protected void k5(L0 o) { sink(o); }              // sink($*Y) matches HERE, depth 5

    // Positive: the tainted L4 is wrapped five levels deep; the starred sink observes it.
    final static class PositiveWrappedDeep extends StarMatrixSink {
        @Override public void entrypoint() {
            L4 t = src5();
            L0 o = w5(w4(w3(w2(w1(t)))));
            k1(o);
        }
    }

    // Negative: an untainted L4 wrapped and threaded through the identical chains.
    final static class NegativeCleanWrapped extends StarMatrixSink {
        @Override public void entrypoint() {
            L4 t = new L4();
            t.v = "safe";
            L0 o = w5(w4(w3(w2(w1(t)))));
            k1(o);
        }
    }
}

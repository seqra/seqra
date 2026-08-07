package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SOURCE, 5+ interprocedural depth x 5+ field depth combined. The source statement
 * `$*X = src()` sits FIVE calls deep (src1..src5); the tainted whole object then climbs back
 * up and is unwrapped ONE field level per hop across five more calls (u1..u5, L0->..->String),
 * and the scalar finally travels five calls down a sink chain (k1..k5) to a plain sink.
 * The 5-level field taint is carried by the $* source's abstract any-field mark.
 */
@RuleSet("taint/StarMatrixSource.yaml")
public abstract class StarMatrixSource implements RuleSample {
    L0 src() { return new L0(); }
    void sink(String s) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    // Source five calls deep: the starred source statement is inside src1.
    protected L0 src5() { return src4(); }
    protected L0 src4() { return src3(); }
    protected L0 src3() { return src2(); }
    protected L0 src2() { return src1(); }
    protected L0 src1() { L0 o = src(); return o; }   // $*X = src() matches HERE, depth 5

    // Five hops, each unwrapping exactly one field level: interproc depth x field depth.
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

    // Positive: deep source -> 5x1-field unwrap hops -> deep sink.
    final static class PositiveDeepChain extends StarMatrixSource {
        @Override public void entrypoint() {
            L0 o = src5();
            String s = u5(u4(u3(u2(u1(o)))));
            k1(s);
        }
    }

    // Negative: an untainted object through the identical chains.
    final static class NegativeCleanChain extends StarMatrixSource {
        @Override public void entrypoint() {
            L0 o = new L0();
            o.f = new L1();
            o.f.f = new L2();
            o.f.f.f = new L3();
            o.f.f.f.f = new L4();
            o.f.f.f.f.v = "safe";
            String s = u5(u4(u3(u2(u1(o)))));
            k1(s);
        }
    }
}

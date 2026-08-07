package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred PROPAGATOR — BOTH occurrences starred (`$*T = pass($*F)`) — at 5+ interprocedural
 * depth x 5+ field depth. A starred source five calls deep taints a whole L0; the object
 * travels five pass-hops to the propagator call, whose starred FROM observes the any-field
 * taint of the whole argument and whose starred TO assigns whole-object taint to the fresh
 * M0 result. The M0 is then unwrapped ONE field level per hop across five calls
 * (M0->..->String) — only possible if the TO really carries any-field taint — and the scalar
 * travels five calls down a sink chain to a plain sink.
 */
@RuleSet("taint/StarMatrixPropagator.yaml")
public abstract class StarMatrixPropagator implements RuleSample {
    L0 src() { return new L0(); }
    M0 pass(L0 o) { return new M0(); }
    void sink(String s) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    static final class M0 { public M1 f; }
    static final class M1 { public M2 f; }
    static final class M2 { public M3 f; }
    static final class M3 { public M4 f; }
    static final class M4 { public String v; }

    // Source five calls deep.
    protected L0 src5() { return src4(); }
    protected L0 src4() { return src3(); }
    protected L0 src3() { return src2(); }
    protected L0 src2() { return src1(); }
    protected L0 src1() { L0 o = src(); return o; }   // $*X = src() matches HERE, depth 5

    // Five object pass-hops before the propagator.
    protected L0 p1(L0 o) { return o; }
    protected L0 p2(L0 o) { return o; }
    protected L0 p3(L0 o) { return o; }
    protected L0 p4(L0 o) { return o; }
    protected L0 p5(L0 o) { return o; }

    // Five hops, each unwrapping one field level of the PROPAGATED object: taint reaches the
    // scalar only if the starred TO assigned any-field taint to the M0.
    protected M1 u1(M0 o) { return o.f; }
    protected M2 u2(M1 o) { return o.f; }
    protected M3 u3(M2 o) { return o.f; }
    protected M4 u4(M3 o) { return o.f; }
    protected String u5(M4 o) { return o.v; }

    // Sink five calls deep.
    protected void k1(String s) { k2(s); }
    protected void k2(String s) { k3(s); }
    protected void k3(String s) { k4(s); }
    protected void k4(String s) { k5(s); }
    protected void k5(String s) { sink(s); }          // sink() called HERE, depth 5

    // Positive: deep source -> 5 hops -> starred propagator -> per-hop unwrap -> deep sink.
    final static class PositivePropagatedDeep extends StarMatrixPropagator {
        @Override public void entrypoint() {
            L0 o = src5();
            L0 o5 = p5(p4(p3(p2(p1(o)))));
            M0 t = pass(o5);               // $*T = pass($*F): whole object in, whole object out
            String s = u5(u4(u3(u2(u1(t)))));
            k1(s);
        }
    }

    // Negative: an untainted object through the identical propagator and chains.
    final static class NegativeCleanPropagated extends StarMatrixPropagator {
        @Override public void entrypoint() {
            L0 o = new L0();
            L0 o5 = p5(p4(p3(p2(p1(o)))));
            M0 t = pass(o5);
            String s = u5(u4(u3(u2(u1(t)))));
            k1(s);
        }
    }
}

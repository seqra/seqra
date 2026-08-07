package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred sink guarded by PATTERN-NOT-INSIDE, 5+ interprocedural depth x 5+ field depth
 * combined. The sink `use($*Y)` sits in a `pattern-inside` context that INTRODUCES the guard
 * receiver (`$G = checker(); ...`), and `pattern-not-inside: $G.check($*Y); ...` suppresses it
 * — every not-inside metavar must be introduced and wired by the pattern-inside/sink patterns
 * (the shipped setContentType suppression idiom; a not-inside with unbound metavars is dropped
 * during automata-to-taint-rule conversion). A starred source five
 * calls deep taints a whole L0; the object travels five hops; the use call sits five calls
 * deep — flagged in the unguarded method, suppressed in the guarded one.
 */
@RuleSet("taint/StarMatrixPatternNotInside.yaml")
public abstract class StarMatrixPatternNotInside implements RuleSample {
    L0 src() { return new L0(); }
    void use(L0 o) {}
    Checker checker() { return new Checker(); }

    static final class Checker { void check(L0 o) {} }

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

    // Unguarded sink chain five calls deep.
    protected void k1(L0 o) { k2(o); }
    protected void k2(L0 o) { k3(o); }
    protected void k3(L0 o) { k4(o); }
    protected void k4(L0 o) { k5(o); }
    protected void k5(L0 o) {
        Checker g = checker();  // pattern-inside context (binds $G), no check() -> flagged
        use(o);                 // starred sink matches HERE, depth 5
    }

    // Guarded sink chain five calls deep: guard() precedes the use in the same method.
    protected void j1(L0 o) { j2(o); }
    protected void j2(L0 o) { j3(o); }
    protected void j3(L0 o) { j4(o); }
    protected void j4(L0 o) { j5(o); }
    protected void j5(L0 o) {
        Checker g = checker();  // pattern-inside context (binds $G)
        g.check(o);             // pattern-not-inside: $G.check($*Y) precedes -> suppressed
        use(o);
    }

    // Positive: tainted object used without the guard.
    final static class PositiveUnguardedUse extends StarMatrixPatternNotInside {
        @Override public void entrypoint() {
            L0 o = src5();
            k1(p5(p4(p3(p2(p1(o))))));
        }
    }

    // Negative: same tainted object, but the use is preceded by guard().
    final static class NegativeGuardedUse extends StarMatrixPatternNotInside {
        @Override public void entrypoint() {
            L0 o = src5();
            j1(p5(p4(p3(p2(p1(o))))));
        }
    }
}

package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SOURCE, taint hidden 5 fields deep. `$*X = src()` taints the whole L0 object AND
 * every nested field at every depth; a 5-level field read must still observe the taint once
 * the any-accessor is unrolled to concrete field reads (AnyAccessorEnabled).
 *
 * Depth axis: field nesting L0.f.f.f.f.v (5 hops). Removing the source `*` makes every
 * Positive a false negative, proving the star is load-bearing.
 */
@RuleSet("taint/StarDeepSource.yaml")
public abstract class StarDeepSource implements RuleSample {
    L0 src() { return new L0(); }
    void sink(String s) {}

    static final class L0 { public L1 f; }
    static final class L1 { public L2 f; }
    static final class L2 { public L3 f; }
    static final class L3 { public L4 f; }
    static final class L4 { public String v; }

    // Positive: starred source's any-field taint reaches a depth-5 field read.
    final static class PositiveDeepFieldRead extends StarDeepSource {
        @Override public void entrypoint() {
            L0 o = src();                 // $*X = src(): whole object + any-field taint
            String v = o.f.f.f.f.v;       // depth-5 read, any-accessor unrolled
            sink(v);
        }
    }

    // Positive: read a shallower (depth-3) field — still tainted by the whole-object star.
    final static class PositiveShallowFieldRead extends StarDeepSource {
        @Override public void entrypoint() {
            L0 o = src();
            L3 mid = o.f.f.f;             // depth-3 read: a sub-object is still tainted
            String v = mid.f.v;
            sink(v);
        }
    }

    // Negative: object built locally, no source flows in, so no field is tainted.
    final static class NegativeCleanDeep extends StarDeepSource {
        @Override public void entrypoint() {
            L0 o = new L0();
            o.f = new L1();
            o.f.f = new L2();
            o.f.f.f = new L3();
            o.f.f.f.f = new L4();
            o.f.f.f.f.v = "safe";
            sink(o.f.f.f.f.v);
        }
    }
}

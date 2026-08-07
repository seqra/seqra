package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SINK, taint hidden at graduated field depths. A plain source taints a field N levels
 * down; the starred whole-object sink `sink($*Y)` must observe the any-field taint.
 *
 * The deep cases were parked as KnownFn* while deep concrete field-store FACT PRODUCTION was
 * broken (the IR lowers `o.f.v1 = src()` through a temp, so the fact was rooted at the temp
 * and never at `o.f.v1` — see DeepFieldStoreFn). The upstream fix (49c8792b9, #304) makes the
 * interprocedural precise READ work (DeepFieldStoreFn is green) and the DEPTH-5 starred-sink
 * observation work (PositiveDepth5 live below). RESIDUAL GAP: the DEPTH-2 starred-sink
 * observation still misses (stable repro, independent of the any-accessor unroll strategy) —
 * KnownFnDepth2 stays parked, see its comment for the root cause.
 */
@RuleSet("taint/StarDeepSink.yaml")
public abstract class StarDeepSink implements RuleSample {
    String src() { return "tainted"; }
    void sink(L0 b) {}

    static final class L0 { public String v0; public L1 f; }
    static final class L1 { public String v1; public L2 f; }
    static final class L2 { public String v2; public L3 f; }
    static final class L3 { public String v3; public L4 f; }
    static final class L4 { public String v; }

    private static L0 build() {
        L0 o = new L0();
        o.f = new L1();
        o.f.f = new L2();
        o.f.f.f = new L3();
        o.f.f.f.f = new L4();
        return o;
    }

    // Positive (WORKS): taint at field depth 1 — the starred sink observes it.
    final static class PositiveDepth1 extends StarDeepSink {
        @Override public void entrypoint() {
            L0 o = build();
            o.v0 = src();   // depth-1 field
            sink(o);
        }
    }

    // KNOWN FALSE NEGATIVE: the depth-2 concrete field mark is not observed by the starred sink,
    // while the DEEPER depth-5 case works and the same store on a LOCALLY ALLOCATED base works
    // (DeepFieldStoreFn). Root cause: the base comes from the opaque `build()` call, so the store
    // is lowered to `%tmp = o.f; %tmp.v1 = src()` with `%tmp` live across the opaque `src()` call;
    // DSUAliasAnalysis.invalidateOuterHeapAliases must break the live `%tmp ~ o.f` link there, and
    // since the DSU cannot hold a singleton set the whole pair is dropped, so the tainted store
    // through the temp is never rebased onto `o.f.v1`. Depth >= 3 escapes only by accident: those
    // temps are dead at the call and dead-local cleanup has already orphaned the chain.
    // See DSUAliasAnalysisInvalidateOuterHeapAliasesTest.invalidateDropsLiveHeapAliasLosingPathRelation,
    // which pins the same loss at the alias-analysis level. Unpark both together.
    final static class KnownFnDepth2 extends StarDeepSink {
        @Override public void entrypoint() {
            L0 o = build();
            o.f.v1 = src();
            sink(o);
        }
    }

    // Positive: depth-5 concrete field mark observed by the starred sink.
    final static class PositiveDepth5 extends StarDeepSink {
        @Override public void entrypoint() {
            L0 o = build();
            o.f.f.f.f.v = src();
            sink(o);
        }
    }

    // Negative: no field ever tainted.
    final static class NegativeCleanObject extends StarDeepSink {
        @Override public void entrypoint() {
            L0 o = build();
            o.f.f.f.f.v = "safe";
            sink(o);
        }
    }
}

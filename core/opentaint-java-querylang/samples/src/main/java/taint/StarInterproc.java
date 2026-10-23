package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * Starred SOURCE threaded through a 5+ deep interprocedural call chain that ALTERNATELY hides
 * taint inside an object and exposes it again. `$*X = src()` taints the whole Box + any-field;
 * the chain unwraps to a scalar, re-wraps into a fresh Box field, unwraps again, and finally
 * reaches a plain sink. Needs AnyAccessorEnabled so the source star reaches the first concrete
 * field read.
 */
@RuleSet("taint/StarInterproc.yaml")
public abstract class StarInterproc implements RuleSample {
    Box src() { return new Box(); }
    void sink(String s) {}

    static final class Box { public String v; }

    // step1..step5: 5 interprocedural hops. Alternation:
    //   step1 pass object -> step2 EXPOSE field to scalar -> step3 HIDE scalar in new Box
    //   -> step4 pass object -> step5 EXPOSE field to scalar reaching the sink.
    protected Box step1(Box b) { return b; }
    protected String step2(Box b) { return b.v; }
    protected Box step3(String s) { Box n = new Box(); n.v = s; return n; }
    protected Box step4(Box b) { return b; }
    protected String step5(Box b) { return b.v; }

    // Positive: taint survives 5 hops of hide/expose alternation from a starred source.
    final static class PositiveAlternatingChain extends StarInterproc {
        @Override public void entrypoint() {
            Box b = src();                 // $*X = src(): whole-object + any-field taint
            Box b1 = step1(b);             // hop 1: object passes through
            String s2 = step2(b1);         // hop 2: EXPOSE (any-field unrolls to b1.v)
            Box b3 = step3(s2);            // hop 3: HIDE the scalar back into a field
            Box b4 = step4(b3);            // hop 4: object passes through
            String s5 = step5(b4);         // hop 5: EXPOSE the field again
            sink(s5);
        }
    }

    // Positive: simplest 5-hop pass-through, field exposed only at the end.
    final static class PositivePassThroughChain extends StarInterproc {
        @Override public void entrypoint() {
            Box b = src();
            Box b1 = step1(b);
            Box b2 = step1(b1);
            Box b3 = step1(b2);
            Box b4 = step4(b3);
            String s = step5(b4);
            sink(s);
        }
    }

    // Negative: fresh untainted Box threaded through the same chain.
    final static class NegativeCleanChain extends StarInterproc {
        @Override public void entrypoint() {
            Box b = new Box();
            b.v = "safe";
            Box b1 = step1(b);
            String s2 = step2(b1);
            Box b3 = step3(s2);
            Box b4 = step4(b3);
            String s5 = step5(b4);
            sink(s5);
        }
    }
}

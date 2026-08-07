package taint;

import base.RuleSample;
import base.RuleSet;

/**
 * BOTH ends starred: `$*X = src()` (whole-object source) and `sink($*Y)` (whole-object sink),
 * with a nested object extracted in between. The source star taints every field of the outer
 * object; a nested sub-object is pulled out and handed to the starred sink, which must observe
 * it as tainted. Uses AnyAccessorEnabled so the source star reaches the extracted sub-object.
 */
@RuleSet("taint/StarSourceAndSink.yaml")
public abstract class StarSourceAndSink implements RuleSample {
    Outer src() { return new Outer(); }
    void sink(Inner i) {}

    static final class Outer { public Mid f; }
    static final class Mid { public Inner f; }
    static final class Inner { public String v; }

    // Positive: whole-object source taint reaches a nested sub-object handed to the starred sink.
    final static class PositiveNestedObjectToStarSink extends StarSourceAndSink {
        @Override public void entrypoint() {
            Outer o = src();       // $*X: whole object + any-field taint
            Inner inner = o.f.f;   // extract a depth-2 nested object
            sink(inner);           // $*Y: starred sink observes the tainted sub-object
        }
    }

    // Negative: locally-built object, nothing tainted.
    final static class NegativeCleanNested extends StarSourceAndSink {
        @Override public void entrypoint() {
            Outer o = new Outer();
            o.f = new Mid();
            o.f.f = new Inner();
            o.f.f.v = "safe";
            sink(o.f.f);
        }
    }
}

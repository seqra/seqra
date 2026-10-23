package taint;

import base.RuleSample;
import base.RuleSet;

@RuleSet("taint/StarSource.yaml")
public abstract class StarSource implements RuleSample {
    Box src() { return new Box(); }
    void sink(String s) {}

    static final class Box {
        private String value;
        String getValue() { return value; }
        void setValue(String value) { this.value = value; }
    }

    // Positive: the STARRED source ($*X = src()) taints the whole Box AND every field.
    // The concrete field read b.getValue() therefore inherits the taint (the source-star's
    // any-field taint is unrolled to the field read) and reaches the plain sink.
    final static class PositiveStarredSourceField extends StarSource {
        @Override public void entrypoint() {
            Box b = src();            // $*X = src(): whole-object + any-field taint
            String v = b.getValue();  // any-accessor taint unrolls to the concrete field
            sink(v);                  // plain sink observes the tainted field
        }
    }

    // Negative: the Box is built locally (not from the starred source), so no field is
    // tainted and the extracted value stays clean.
    final static class NegativeCleanField extends StarSource {
        @Override public void entrypoint() {
            Box b = new Box();
            b.setValue("safe");
            String v = b.getValue();
            sink(v);
        }
    }
}

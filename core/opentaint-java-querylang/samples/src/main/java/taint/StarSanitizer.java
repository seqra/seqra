package taint;

import base.RuleSample;
import base.RuleSet;

@RuleSet("taint/StarSanitizer.yaml")
public abstract class StarSanitizer implements RuleSample {
    String src() { return "tainted"; }
    static final class Box { String value; String getValue() { return value; } }
    Box clean(Box b) { return b; }   // $*C sanitizer: cleans the object + all its fields
    void sink(String data) {}

    // Positive: tainted field reaches sink with NO sanitizer between
    final static class PositiveTaintedField extends StarSanitizer {
        @Override public void entrypoint() {
            Box b = new Box();
            b.value = src();
            sink(b.getValue());
        }
    }

    // Negative: the $*C sanitizer must clean the field taint on the value flowing onward
    final static class NegativeSanitizedField extends StarSanitizer {
        @Override public void entrypoint() {
            Box b = new Box();
            b.value = src();
            Box cleaned = clean(b);
            sink(cleaned.getValue());   // field taint must be gone after $*C sanitizer
        }
    }
}

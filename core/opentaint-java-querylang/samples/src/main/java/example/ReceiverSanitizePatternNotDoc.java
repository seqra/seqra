package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation: multi-event pattern-not where the excluded on-path event is
 * a receiver call on the produced value.
 */
@RuleSet("example/ReceiverSanitizePatternNotDoc.yaml")
public abstract class ReceiverSanitizePatternNotDoc implements RuleSample {

    static class Value {
        Value sanitized() { return this; }
    }

    static Value decode(Object o) { return new Value(); }
    static void consume(Value v) {}

    static class Positive extends ReceiverSanitizePatternNotDoc {
        @Override
        public void entrypoint() {
            Value r = decode("x");
            consume(r);
        }
    }

    static class Negative extends ReceiverSanitizePatternNotDoc {
        @Override
        public void entrypoint() {
            Value r = decode("x");
            r = r.sanitized();
            consume(r);
        }
    }
}

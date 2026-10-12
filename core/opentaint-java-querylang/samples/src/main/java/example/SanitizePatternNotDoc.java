package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation: documents the current argument-position restriction: a
 * pattern-not event that uses the tracked value only as a call argument
 * (static-call sanitize) does not yet anchor the exclusion. See
 * ReceiverSanitizePatternNotDoc for the working receiver-shaped form.
 */
@RuleSet("example/SanitizePatternNotDoc.yaml")
public abstract class SanitizePatternNotDoc implements RuleSample {

    static String decode(Object o) { return String.valueOf(o); }
    static String sanitize(String o) { return o; }
    static void consume(String o) {}

    static class Positive extends SanitizePatternNotDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            consume(r);
        }
    }

    static class Negative extends SanitizePatternNotDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            r = sanitize(r);
            consume(r);
        }
    }
}

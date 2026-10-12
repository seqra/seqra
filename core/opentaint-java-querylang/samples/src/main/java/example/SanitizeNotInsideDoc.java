package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation: pattern-not-inside whose excluded event is the
 * self-sanitizing reassignment, in the anchored shape: producer in
 * pattern-inside, single-event main pattern, so the excluded context can
 * enclose the match. The identical event matches positively
 * (SanitizeEventSanityDoc); the receiver-position counterpart excludes
 * (ReceiverSanitizePatternNotDoc).
 */
@RuleSet("example/SanitizeNotInsideDoc.yaml")
public abstract class SanitizeNotInsideDoc implements RuleSample {

    static String decode(Object o) { return String.valueOf(o); }
    static String sanitize(String o) { return o; }
    static void consume(String o) {}

    static class Positive extends SanitizeNotInsideDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            consume(r);
        }
    }

    static class Negative extends SanitizeNotInsideDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            r = sanitize(r);
            consume(r);
        }
    }
}

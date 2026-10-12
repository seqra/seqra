package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation sanity probe: the self-sanitizing reassignment event
 * required POSITIVELY. If this matches, the event exists as an automaton
 * step and the negative failures for the same event are specific to
 * negative clauses.
 */
@RuleSet("example/SanitizeEventSanityDoc.yaml")
public abstract class SanitizeEventSanityDoc implements RuleSample {

    static Object decode(Object o) { return o; }
    static Object sanitize(Object o) { return o; }
    static void consume(Object o) {}

    static class Positive extends SanitizeEventSanityDoc {
        @Override
        public void entrypoint() {
            Object r = decode("x");
            r = sanitize(r);
            consume(r);
        }
    }

    static class Negative extends SanitizeEventSanityDoc {
        @Override
        public void entrypoint() {
            Object r = decode("x");
            consume(r);
        }
    }
}

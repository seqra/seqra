package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation sanity probe: the same argument-position check event used in
 * the negative probes, but required POSITIVELY. If this matches, the event
 * exists as an automaton step and the negative failures are specific to
 * negative clauses.
 */
@RuleSet("example/ArgEventSanityDoc.yaml")
public abstract class ArgEventSanityDoc implements RuleSample {

    static Object decode(Object o) { return o; }
    static int checksum;

    static void check(Object o) { checksum += o.hashCode(); }
    static void consume(Object o) {}

    static class Positive extends ArgEventSanityDoc {
        @Override
        public void entrypoint() {
            Object r = decode("x");
            check(r);
            consume(r);
        }
    }

    static class Negative extends ArgEventSanityDoc {
        @Override
        public void entrypoint() {
            Object r = decode("x");
            consume(r);
        }
    }
}

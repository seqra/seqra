package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation: documents the current argument-position restriction: an
 * excluded event that uses the produced value only as a call argument does
 * not yet anchor the exclusion.
 */
@RuleSet("example/ArgObserverPatternNotDoc.yaml")
public abstract class ArgObserverPatternNotDoc implements RuleSample {

    static String decode(Object o) { return String.valueOf(o); }
    static void check(String o) {}
    static void consume(String o) {}

    static class Positive extends ArgObserverPatternNotDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            consume(r);
        }
    }

    static class Negative extends ArgObserverPatternNotDoc {
        @Override
        public void entrypoint() {
            String r = decode("x");
            check(r);
            consume(r);
        }
    }
}

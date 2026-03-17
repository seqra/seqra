package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithState.yaml")
public abstract class RuleWithState implements RuleSample {
    void f() {
    }

    void g() {
    }

    static class PositiveSimple extends RuleWithState {
        @Override
        public void entrypoint() {
            f();
            g();
        }
    }

    static class NegativeSimple1 extends RuleWithState {
        @Override
        public void entrypoint() {
            f();
        }
    }

    static class NegativeSimple2 extends RuleWithState {
        @Override
        public void entrypoint() {
            g();
        }
    }
}

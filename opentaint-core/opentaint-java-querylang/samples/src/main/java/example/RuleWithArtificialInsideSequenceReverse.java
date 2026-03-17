package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithArtificialInsideSequenceReverse.yaml")
public abstract class RuleWithArtificialInsideSequenceReverse implements RuleSample {
    void f(String x) {}
    void g(String x) {}
    void h(String x) {}
    void clean(String x) {}

    final static class PositiveSimple extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            g(data);
            h(data);
        }
    }

    final static class PositiveReverseInside extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            h(data);
            g(data);
        }
    }

    final static class PositiveCleanFirst extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            clean(data);
            f(data);
            g(data);
            h(data);
        }
    }

    final static class NegativeWithClean extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            clean(data);
            g(data);
            h(data);
        }
    }

    final static class NegativeNoG extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            h(data);
        }
    }

    final static class NegativeNoH extends RuleWithArtificialInsideSequenceReverse {
        @Override
        public void entrypoint() {
            String data = "";
            f(data);
            g(data);
        }
    }
}

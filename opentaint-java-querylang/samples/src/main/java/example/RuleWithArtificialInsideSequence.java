package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithArtificialInsideSequence.yaml")
public abstract class RuleWithArtificialInsideSequence implements RuleSample {
    void f(String x) {}
    void g(String x) {}
    void h(String x) {}
    void clean(String x) {}

    final static class PositiveSimple extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            g(data);
            h(data);
            f(data);
        }
    }

    final static class PositiveReverseInside extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            h(data);
            g(data);
            f(data);
        }
    }

    final static class PositiveCleanLast extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            g(data);
            h(data);
            f(data);
            clean(data);
        }
    }

    final static class NegativeWithClean extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            g(data);
            h(data);
            clean(data);
            f(data);
        }
    }

    final static class NegativeNoG extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            h(data);
            f(data);
        }
    }

    final static class NegativeNoH extends RuleWithArtificialInsideSequence {
        @Override
        public void entrypoint() {
            String data = "";
            g(data);
            f(data);
        }
    }
}

package example;

import base.RuleSample;

public abstract class RuleWithStaticField implements RuleSample {

    void sink(StaticConstantStorage condition) {
    }

    final static class PositiveSimple extends RuleWithStaticField {
        @Override
        public void entrypoint() {
            sink(StaticConstantStorage.FIRST);
        }
    }

    final static class NegativeSimple extends RuleWithStaticField {
        @Override
        public void entrypoint() {
            sink(StaticConstantStorage.SECOND);
        }
    }
}

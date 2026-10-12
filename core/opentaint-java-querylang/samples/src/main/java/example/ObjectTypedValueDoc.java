package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/ObjectTypedValueDoc.yaml")
public abstract class ObjectTypedValueDoc implements RuleSample {
     Object src() { return null; }
     void clean(Object data) {}
     void sink(Object data) {}

    final static class PositiveSimple extends ObjectTypedValueDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            sink(data);
        }
    }

    final static class NegativeSimple extends ObjectTypedValueDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            clean(data);
            sink(data);
        }
    }
}

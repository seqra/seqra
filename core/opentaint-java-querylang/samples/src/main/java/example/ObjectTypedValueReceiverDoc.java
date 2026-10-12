package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/ObjectTypedValueReceiverDoc.yaml")
public abstract class ObjectTypedValueReceiverDoc implements RuleSample {
    Object src() { return null; }
    void sink(Object data) {}

    final static class PositiveSimple extends ObjectTypedValueReceiverDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            sink(data);
        }
    }

    final static class NegativeSimple extends ObjectTypedValueReceiverDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            data = data.toString();
            sink(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/ObjectTypedValueCastDoc.yaml")
public abstract class ObjectTypedValueCastDoc implements RuleSample {
    static class Payload {}

    Object src() { return new Payload(); }   // value's static type is Object
    void clean(Payload data) {}
    void sink(Object data) {}

    final static class PositiveSimple extends ObjectTypedValueCastDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            sink(data);
        }
    }

    final static class NegativeSimple extends ObjectTypedValueCastDoc {
        @Override
        public void entrypoint() {
            Object data = src();
            clean((Payload) data);
            sink(data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/TypedValueControlDoc.yaml")
public abstract class TypedValueControlDoc implements RuleSample {
    static class Payload {}

    Payload src() { return new Payload(); }
    void clean(Payload data) {}
    void sink(Payload data) {}

    final static class PositiveSimple extends TypedValueControlDoc {
        @Override
        public void entrypoint() {
            Payload data = src();
            sink(data);
        }
    }

    final static class NegativeSimple extends TypedValueControlDoc {
        @Override
        public void entrypoint() {
            Payload data = src();
            clean(data);
            sink(data);
        }
    }
}

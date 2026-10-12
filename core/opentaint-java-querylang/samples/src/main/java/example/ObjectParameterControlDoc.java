package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/ObjectParameterControlDoc.yaml")
public abstract class ObjectParameterControlDoc implements RuleSample {
    static class Payload {}

    Payload src() { return new Payload(); }
    void clean(Object data) {}   // parameter widened to Object
    void sink(Payload data) {}

    final static class PositiveSimple extends ObjectParameterControlDoc {
        @Override
        public void entrypoint() {
            Payload data = src();
            sink(data);
        }
    }

    final static class NegativeSimple extends ObjectParameterControlDoc {
        @Override
        public void entrypoint() {
            Payload data = src();
            clean(data);
            sink(data);
        }
    }
}

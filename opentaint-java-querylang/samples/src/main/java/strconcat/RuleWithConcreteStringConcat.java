package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithConcreteStringConcat.yaml")
public abstract class RuleWithConcreteStringConcat implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {}

    final static class PositiveSimple extends RuleWithConcreteStringConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = data + "taint2";
            sink(data);
        }
    }

    final static class NegativeSimple extends RuleWithConcreteStringConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = data + "taint3";
            sink(data);
        }
    }
}


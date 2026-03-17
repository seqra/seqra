package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithUnboundConcat.yaml")
public abstract class RuleWithUnboundConcat implements RuleSample {
    void sink() {
    }

    void sink(String data) {
    }

    void sink(String data, String other) {
    }

    void sink(int data, String other) {
    }

    static class PositiveSimple extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            sink("data");
        }
    }

    static class PositiveSimple2 extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            String a = "data";
            String b = "data";
            sink(a + b);
        }
    }

    static class PositiveSimple3 extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            sink("data", "other");
        }
    }

    static class NegativeSimple extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            sink();
        }
    }


    static class NegativeSimple2 extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            sink();
        }
    }

    static class NegativeSimple3 extends RuleWithUnboundConcat {
        @Override
        public void entrypoint() {
            sink(0, "other");
        }
    }
}

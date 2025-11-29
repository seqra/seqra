package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithEllipsisStringConcat.yaml")
public abstract class RuleWithEllipsisStringConcat implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {}

    final static class PositiveLeft extends RuleWithEllipsisStringConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = "abc" + data;
            sink(data);
        }
    }

    final static class PositiveRight extends RuleWithEllipsisStringConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = data + "abc";
            sink(data);
        }
    }

    final static class NegativeNoSource extends RuleWithEllipsisStringConcat {
        @Override
        public void entrypoint() {
            String data = src();
            String concatenated = "abc" + "def";
            sink(concatenated);
        }
    }
}


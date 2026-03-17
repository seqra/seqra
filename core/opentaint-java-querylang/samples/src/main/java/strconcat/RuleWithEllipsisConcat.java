package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithEllipsisConcat.yaml")
public abstract class RuleWithEllipsisConcat implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {}

    private static Object obj = new Object();

    final static class PositiveLeft extends RuleWithEllipsisConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = obj.toString() + data;
            sink(data);
        }
    }

    final static class PositiveRight extends RuleWithEllipsisConcat {
        @Override
        public void entrypoint() {
            String data = src();
            data = data + obj.toString();
            sink(data);
        }
    }

    final static class NegativeNoSource extends RuleWithEllipsisConcat {
        @Override
        public void entrypoint() {
            String data = src();
            String concatenated = "abc" + "def";
            sink(concatenated);
        }
    }
}


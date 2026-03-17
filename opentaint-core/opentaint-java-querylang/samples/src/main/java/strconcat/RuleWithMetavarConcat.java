package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithMetavarConcat.yaml")
public abstract class RuleWithMetavarConcat implements RuleSample {
    String src1() {
        return "tainted string 1";
    }

    String src2() {
        return "tainted string 2";
    }

    void sink(String data) {}

    final static class PositiveCorrectOrder extends RuleWithMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String concatenated = data1 + data2;
            sink(concatenated);
        }
    }

    final static class PositiveIncorrectOrder extends RuleWithMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String concatenated = data2 + data1;
            sink(concatenated);
        }
    }

    final static class NegativeOnlyOneMetavar1 extends RuleWithMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String concatenated = data1 + "abc";
            sink(data1);
            sink(concatenated);
        }
    }

    final static class NegativeOnlyOneMetavar2 extends RuleWithMetavarConcat {
        @Override
        public void entrypoint() {
            String data2 = src2();
            String concatenated = "abc" + data2;
            sink(data2);
            sink(concatenated);
        }
    }
}


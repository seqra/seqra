package strconcat;

import base.RuleSample;
import base.RuleSet;

@RuleSet("strconcat/RuleWithMultipleMetavarConcat.yaml")
public abstract class RuleWithMultipleMetavarConcat implements RuleSample {
    String src1() {
        return "tainted string 1";
    }

    String src2() {
        return "tainted string 2";
    }

    String src3() {
        return "tainted string 3";
    }

    String src4() {
        return "tainted string 4";
    }

    void sink(String data) {}

    final static class PositiveSimple extends RuleWithMultipleMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String data3 = src3();
            String data4 = src4();
            String concatenatedLeft = data1 + data2;
            String concatenatedRight = data3 + data4;
            String concatenated = concatenatedLeft + concatenatedRight;
            sink(concatenated);
        }
    }

    final static class PositiveWithRepeatAndTrash extends RuleWithMultipleMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String data3 = src3();
            String data4 = src4();
            String concatenatedLeft = data1 + data2 + "aaa";
            String concatenatedRight = data2 + data3 + data4 + "bbb";
            String concatenated = concatenatedLeft + concatenatedRight + "ccc";
            sink(concatenated);
        }
    }

    final static class NegativeOnly123 extends RuleWithMultipleMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String data3 = src3();
            String concatenatedLeft = data1 + data2;
            String concatenatedRight = data2 + data3;
            String concatenated = concatenatedLeft + concatenatedRight;
            sink(concatenated);
        }
    }

    final static class NegativeOnly124 extends RuleWithMultipleMetavarConcat {
        @Override
        public void entrypoint() {
            String data1 = src1();
            String data2 = src2();
            String data4 = src4();
            String concatenatedLeft = data1 + data2;
            String concatenatedRight = data2 + data4;
            String concatenated = concatenatedLeft + concatenatedRight;
            sink(concatenated);
        }
    }
}


package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/NDRule.yaml")
public abstract class NDRule implements RuleSample {
    String src() {
        return "src";
    }

    String pass(String first, String second) {
        return "pass";
    }

    void sink(String s) {

    }

    static class PositiveNdRule extends NDRule {
        @Override
        public void entrypoint() {
            String A = src();
            String B = src();
            String x = f1(A, B);
            sink(x);
        }

        private String f1(String x, String y) {
            String xCopy = copy(x);
            String C = pass(xCopy, y);
            String CCopy = copy(C);
            return CCopy;
        }

        private String copy(String x) {
            return x;
        }
    }

    static class PositiveNdRule2 extends NDRule {
        @Override
        public void entrypoint() {
            String A = src();
            String B = src();
            f1(A, B);
        }

        private void f1(String x, String y) {
            String xCopy = copy(x);
            String C = pass(xCopy, y);
            f2(C);
        }

        private void f2(String c) {
            sink(c);
        }

        private String copy(String x) {
            return x;
        }
    }
}

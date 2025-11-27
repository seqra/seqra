package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithPatternInside.yaml")
public abstract class RuleWithPatternInside implements RuleSample {
    String src() {
        return "tainted string";
    }

    String src1() {
        return "not tainted string";
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends RuleWithPatternInside {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }

    final static class PositiveWithEllipsis extends RuleWithPatternInside {
        @Override
        public void entrypoint() {
            String data = src();
            System.out.println(data);
            sink(data);
        }
    }

    final static class PositiveIterProc extends RuleWithPatternInside {
        @Override
        public void entrypoint() {
            String data = src();
            sinkWrapper(data);
        }

        void sinkWrapper(String data) {
            System.out.println(data);
            sink(data);
        }
    }

    final static class NegativeNoSink extends RuleWithPatternInside {
        @Override
        public void entrypoint() {
            String data = src();
        }
    }

    final static class NegativeNoSource extends RuleWithPatternInside {
        @Override
        public void entrypoint() {
            String data = src1();
            sink(data);
        }
    }
}

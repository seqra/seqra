package taint;

import base.RuleSample;
import base.RuleSet;

@RuleSet("taint/RuleComplexSourceSinkNoFocus.yaml")
public abstract class RuleComplexSourceSinkNoFocus implements RuleSample {
    String srcInit() {
        return "src init data";
    }

    String sinkInit() {
        return "sink init data";
    }

    String src(String initData) {
        return "tainted string";
    }


    void sink(String initData, String data) {
    }

    final static class PositiveSimple extends RuleComplexSourceSinkNoFocus {
        @Override
        public void entrypoint() {
            String srcInit = srcInit();
            String sinkInit = sinkInit();

            String data = src(srcInit);
            sink(sinkInit, data);
        }
    }

    final static class NegativeSimple extends RuleComplexSourceSinkNoFocus {
        @Override
        public void entrypoint() {
            String srcInit = srcInit();

            String data = src(srcInit);
            sink(srcInit, data);
        }
    }

    final static class NegativeSimple2 extends RuleComplexSourceSinkNoFocus {
        @Override
        public void entrypoint() {
            String srcInit = sinkInit();

            String data = src(srcInit);
            sink(srcInit, data);
        }
    }

    final static class NegativeSimple3 extends RuleComplexSourceSinkNoFocus {
        @Override
        public void entrypoint() {
            String srcInit = srcInit();
            String sinkInit = sinkInit();

            sink(sinkInit, srcInit);
        }
    }
}

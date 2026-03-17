package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/TrickyPatternNot.yaml")
public abstract class TrickyPatterNot implements RuleSample {
    String src() {
        return "data";
    }

    String clean(String s) {
        return s;
    }

    static class PositiveSimple extends TrickyPatterNot {
        @Override
        public void entrypoint() {
            method();
        }

        String method() {
            String s = src();
            return s;
        }
    }

    static class NegativeSimple extends TrickyPatterNot {
        @Override
        public void entrypoint() {
            method();
        }

        String method() {
            String s = src();
            String c = clean(s);
            return c;
        }
    }
}

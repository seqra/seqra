package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.Util;

@RuleSet("issues/issue98.yaml")
public abstract class issue98 implements RuleSample {
    static class PositivePattern extends issue98 {
        @Override
        public void entrypoint() {
            Util u = new Util();
            u.sink("hi");
        }
    }

    static class NegativePatternA extends issue98 {
        @Override
        public void entrypoint() {
            Util u = new Util();
            u.safeA();
            u.sink("hi");
        }
    }

    static class NegativePatternB extends issue98 {
        @Override
        public void entrypoint() {
            Util u = new Util();
            u.safeB();
            u.sink("hi");
        }
    }
}

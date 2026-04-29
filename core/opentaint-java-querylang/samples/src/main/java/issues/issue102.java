package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i102.Util;

@RuleSet("issues/issue102.yaml")
public abstract class issue102 implements RuleSample {
    static class PositivePattern extends issue102 {
        @Override
        public void entrypoint() {
            Util u = new Util();
            u.sink("hi");
        }
    }

    static class NegativePattern extends issue102 {
        @Override
        public void entrypoint() {
            Util u = new Util();
            u.safe00();
            u.sink("hi");
        }
    }
}

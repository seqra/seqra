package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.User;

@RuleSet("issues/issue98.yaml")
public abstract class issue98 implements RuleSample {
    static class NegativeTaint extends issue98 {
        @Override
        public void entrypoint() {
            new User().peculiarMethod();
        }
    }

    static class PositiveTaint extends issue98 {
        @Override
        public void entrypoint() {
            new User().badMethod();
        }
    }
}

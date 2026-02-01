package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i97.User;

@RuleSet("issues/issue97.yaml")
public abstract class issue97 implements RuleSample {
    static class PositiveTaint extends issue97 {
        @Override
        public void entrypoint() {
            User.badMethod();
        }
    }
}

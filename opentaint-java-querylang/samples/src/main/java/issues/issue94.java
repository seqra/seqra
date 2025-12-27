package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i94.SpecificData;
import issues.i94.User;

@RuleSet("issues/issue94.yaml")
public abstract class issue94 implements RuleSample {
    static class PositiveTaint extends issue94 {
        @Override
        public void entrypoint() {
            SpecificData d = new SpecificData();
            (new User()).vulnerableMethod(d);
        }
    }
}

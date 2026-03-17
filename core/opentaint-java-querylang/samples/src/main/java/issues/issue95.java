package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i95.SpecificData;
import issues.i95.User;

@RuleSet("issues/issue95.yaml")
public abstract class issue95 implements RuleSample {
    static class PositiveTaint extends issue95 {
        @Override
        public void entrypoint() {
            SpecificData danger = new SpecificData();
            (new User()).outsideControlledData(danger);
        }
    }

    static class NegativeTaint extends issue95 {
        @Override
        public void entrypoint() {
            (new User()).privateControlledData();
        }
    }
}

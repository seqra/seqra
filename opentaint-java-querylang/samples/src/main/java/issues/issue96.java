package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i96.SpecificData;
import issues.i96.User;

@RuleSet("issues/issue96.yaml")
public abstract class issue96 implements RuleSample {
    static class NegativeTaint1 extends issue96 {
        @Override
        public void entrypoint() {
            SpecificData danger = new SpecificData();
            (new User()).dataPassThrough(danger);
        }
    }

    static class NegativeTaint2 extends issue96 {
        @Override
        public void entrypoint() {
            (new User()).privateControlledData();
        }
    }

    static class PositiveTaint extends issue96 {
        @Override
        public void entrypoint() {
            SpecificData danger = new SpecificData();
            (new User()).outsideControlledData(danger);
        }
    }
}

package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i84.ArrayExample;

@RuleSet("issues/issue84.yaml")
public abstract class issue84 implements RuleSample {
    static class PositiveArr extends issue84 {
        @Override
        public void entrypoint() {
            ArrayExample val = new ArrayExample();
            String[] arr = val.getArray();
            val.useString(arr[0]);
        }
    }
}

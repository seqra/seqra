package issues;

import base.RuleSample;
import issues.i77.BenchmarkTest00023_min;

import base.RuleSet;

@RuleSet("issues/issue77.yaml")
public abstract class issue77 implements RuleSample {
    static class PositiveMin extends issue77 {
        @Override
        public void entrypoint() {
            new BenchmarkTest00023_min().doPost();
        }
    }
}

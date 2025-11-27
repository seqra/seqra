package issues;

import base.RuleSample;
import issues.i78.BenchmarkTest00087_min;

import base.RuleSet;

@RuleSet("issues/issue78.yaml")
public abstract class issue78 implements RuleSample {

    static class PositiveMin extends issue78 {
        @Override
        public void entrypoint() {
            new BenchmarkTest00087_min().doPost();
        }
    }
}

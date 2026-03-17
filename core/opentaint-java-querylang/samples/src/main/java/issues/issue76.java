package issues;

import base.RuleSample;
import issues.i76.BenchmarkTest00005_min;

import base.RuleSet;

@RuleSet("issues/issue76.yaml")
public abstract class issue76 implements RuleSample {
    static class PositiveMin extends issue76 {

        @Override
        public void entrypoint() {
            try {
                new BenchmarkTest00005_min().doPost();
            } catch (Exception ignored) {

            }
        }
    }
}

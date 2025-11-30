package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i83.BenchmarkTest00020_min;

import javax.servlet.http.HttpServletRequest;

@RuleSet("issues/issue83.yaml")
public abstract class issue83 implements RuleSample {

    static class PositiveMin extends issue83 {
        @Override
        public void entrypoint() {
            new BenchmarkTest00020_min().doPost(HttpServletRequest.create());
        }
    }
}

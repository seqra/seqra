package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i91.BenchmarkTest01082_min;

import javax.servlet.http.HttpServletRequest;

@RuleSet("issues/issue91.yaml")
public abstract class issue91 implements RuleSample {
    static class PositiveTaint extends issue91 {
        @Override
        public void entrypoint() {
            BenchmarkTest01082_min t = new BenchmarkTest01082_min();
            t.doPost(new HttpServletRequest.Impl());
        }
    }
}

package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i87.BenchmarkTest01241_min;

import javax.servlet.http.HttpServletRequest;

@RuleSet("issues/issue87.yaml")
public abstract class issue87 implements RuleSample {
    static class PositiveTaint extends issue87 {
        @Override
        public void entrypoint() {
            BenchmarkTest01241_min t = new BenchmarkTest01241_min();
            t.doPost(new HttpServletRequest.Impl());
        }
    }
}

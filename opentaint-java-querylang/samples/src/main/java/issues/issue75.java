package issues;

import base.RuleSample;
import issues.i75.BenchmarkTest00001_min;

import javax.servlet.http.HttpServletResponse;

import base.RuleSet;

@RuleSet("issues/issue75.yaml")
public abstract class issue75 implements RuleSample {
    static class PositiveMin extends issue75 {
        @Override
        public void entrypoint() {
            final HttpServletResponse response = HttpServletResponse.create();
            new BenchmarkTest00001_min().doGet(response);
        }
    }
}

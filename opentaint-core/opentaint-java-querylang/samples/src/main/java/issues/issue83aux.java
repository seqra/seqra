package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i83aux.I83Example;

import javax.servlet.http.HttpServletRequest;

@RuleSet("issues/issue83aux.yaml")
public abstract class issue83aux implements RuleSample {

    static class PositiveMin extends issue83aux {
        @Override
        public void entrypoint() {
            new I83Example().doGet(HttpServletRequest.create());
        }
    }
}

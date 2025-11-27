package custom;

import base.RuleSample;
import base.RuleSet;
import custom.logInjection.LogInjection;

import javax.servlet.http.HttpServletRequest;

@RuleSet("custom/springLogInjection.yaml")
public abstract class springLogInjection implements RuleSample {
    static class PositiveLogInjection extends springLogInjection {
        @Override
        public void entrypoint() {
            new LogInjection().LogInjectionVuln(HttpServletRequest.create());
        }
    }
}

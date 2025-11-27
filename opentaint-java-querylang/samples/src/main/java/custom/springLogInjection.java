package custom;

import base.RuleSample;
import custom.logInjection.LogInjection;

import javax.servlet.http.HttpServletRequest;

public abstract class springLogInjection implements RuleSample {
    static class PositiveLogInjection extends springLogInjection {
        @Override
        public void entrypoint() {
            new LogInjection().LogInjectionVuln(HttpServletRequest.create());
        }
    }
}

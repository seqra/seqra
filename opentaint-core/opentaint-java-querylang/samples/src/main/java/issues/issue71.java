package issues;

import base.RuleSample;
import issues.i71.HibernateUtil_min;

import base.RuleSet;

@RuleSet("issues/issue71.yaml")
public abstract class issue71 implements RuleSample {
    static class PositiveMin extends issue71 {
        @Override
        public void entrypoint() {
            try {
                new HibernateUtil_min();
            } catch (Exception ignored) {

            }
        }
    }
}

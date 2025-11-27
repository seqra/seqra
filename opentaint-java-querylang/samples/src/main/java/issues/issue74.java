package issues;

import base.RuleSample;
import issues.i74.HibernateUtil_min;

import base.RuleSet;

@RuleSet("issues/issue74.yaml")
public abstract class issue74 implements RuleSample {
    static class PositiveMin extends issue74 {
        @Override
        public void entrypoint() {
            try {
                new HibernateUtil_min();
            } catch (Exception ignored) {

            }
        }
    }
}

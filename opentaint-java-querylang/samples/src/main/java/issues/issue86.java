package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i86.PatternEither;

@RuleSet("issues/issue86.yaml")
public abstract class issue86 implements RuleSample {
    static class PositiveEither extends issue86 {
        @Override
        public void entrypoint() {
            PatternEither pe = new PatternEither();
            String a = pe.getString2();
            pe.useString(a);
        }
    }
}

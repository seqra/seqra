package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i100.Box;
import issues.i100.Other;

@RuleSet("issues/issue100.yaml")
public abstract class issue100 implements RuleSample {
    static class PositiveDiamond extends issue100 {
        @Override
        public void entrypoint() {
            Box<String> b = new Box<>();
        }
    }

    static class PositiveExplicit extends issue100 {
        @Override
        public void entrypoint() {
            Box<String> b = new Box<String>();
        }
    }

    static class NegativeOther extends issue100 {
        @Override
        public void entrypoint() {
            // $T = Other, not Box → no match
            Other o = new Other();
        }
    }
}

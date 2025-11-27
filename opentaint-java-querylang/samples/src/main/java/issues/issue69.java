package issues;

import base.RuleSample;
import issues.i69.DataBaseServer_min;

import base.RuleSet;

@RuleSet("issues/issue69.yaml")
public abstract class issue69 implements RuleSample {
    static class PositiveGet extends issue69 {
        @Override
        public void entrypoint() {
            new DataBaseServer_min().get();
        }
    }

    static class PositivePost extends issue69 {
        @Override
        public void entrypoint() {
            new DataBaseServer_min().post();
        }
    }
}

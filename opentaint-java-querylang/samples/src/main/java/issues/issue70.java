package issues;

import base.RuleSample;
import issues.i70.DatabaseHelper_min;

import base.RuleSet;

@RuleSet("issues/issue70.yaml")
public abstract class issue70 implements RuleSample {
    static class Positive1 extends issue70 {
        @Override
        public void entrypoint() {
            DatabaseHelper_min.sample1("");
        }
    }

    static class Positive2 extends issue70 {
        @Override
        public void entrypoint() {
            DatabaseHelper_min.sample2("");
        }
    }

    static class Positive3 extends issue70 {
        @Override
        public void entrypoint() {
            DatabaseHelper_min.sample3();
        }
    }

    static class Positive4 extends issue70 {
        @Override
        public void entrypoint() {
            DatabaseHelper_min.sample4();
        }
    }

    static class Positive5 extends issue70 {
        @Override
        public void entrypoint() {
            DatabaseHelper_min.sample5();
        }
    }
}

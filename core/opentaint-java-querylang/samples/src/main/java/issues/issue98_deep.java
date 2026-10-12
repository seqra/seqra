package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i98.User_i98_deep;

@RuleSet("issues/issue98.yaml")
public abstract class issue98_deep implements RuleSample {
    static String badString() {
        return "42";
    }

    static void sink(String data) {
    }

    static class PositiveTaint extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUser(badString())); }
    }

    static class PositiveDepth4 extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUserDepth4(badString())); }
    }

    static class PositiveDepth3 extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUserDepth3(badString())); }
    }

    static class PositiveDepth2 extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUserDepth2(badString())); }
    }

    static class PositiveDepth4Call2 extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUserDepth4Call2(badString())); }
    }

    static class PositiveDepth4Call3 extends issue98_deep {
        @Override
        public void entrypoint() { sink((new User_i98_deep()).badUserDepth4Call3(badString())); }
    }
}

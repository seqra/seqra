package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i88.IntConsumer;

import java.security.SecureRandom;
import java.util.Random;

@RuleSet("issues/issue88.yaml")
public abstract class issue88 implements RuleSample {
    static class PositiveTaint extends issue88 {
        @Override
        public void entrypoint() {
            Random r = new Random();
            Integer a = r.nextInt();
            (new IntConsumer()).useInt(a);
        }
    }

    static class NegativeTaint extends issue88 {
        @Override
        public void entrypoint() {
            SecureRandom r = new SecureRandom();
            Integer a = r.nextInt();
            (new IntConsumer()).useInt(a);
        }
    }
}

package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i89.AlgoChooser;

@RuleSet("issues/issue89.yaml")
public abstract class issue89 implements RuleSample {
    static class PositiveAlgo extends issue89 {
        @Override
        public void entrypoint() {
            AlgoChooser ac = new AlgoChooser();
            String wow = ac.getAlgo("BadAlgo");
            ac.useString(wow);
        }
    }

    static class NegativeAlgo extends issue89 {
        @Override
        public void entrypoint() {
            AlgoChooser ac = new AlgoChooser();
            String wow = ac.getAlgo("GoodAlgo");
            ac.useString(wow);
        }
    }
}

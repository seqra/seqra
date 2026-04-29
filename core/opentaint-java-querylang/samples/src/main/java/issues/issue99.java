package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i99.Chain;

@RuleSet("issues/issue99.yaml")
public abstract class issue99 implements RuleSample {
    static class PositiveChain extends issue99 {
        @Override
        public void entrypoint() {
            new Chain().getA().doX();
        }
    }

    static class NegativeChainM1 extends issue99 {
        @Override
        public void entrypoint() {
            // $M1=getC, not in {getA, getB} → no match
            new Chain().getC().doX();
        }
    }

    static class NegativeChainM2 extends issue99 {
        @Override
        public void entrypoint() {
            // $M2=doZ, not in {doX, doY} → no match
            new Chain().getA().doZ();
        }
    }
}

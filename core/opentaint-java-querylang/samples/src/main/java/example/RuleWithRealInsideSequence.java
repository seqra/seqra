package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithRealInsideSequence.yaml")
public abstract class RuleWithRealInsideSequence implements RuleSample {
    final static class PositiveSimple extends RuleWithRealInsideSequence {
        @Override
        public void entrypoint() {
            ObjectMapper om = new ObjectMapper();
            om.enableDefaultTyping();
            om.readValue("");
        }
    }

    final static class NegativeSimple extends RuleWithRealInsideSequence {
        @Override
        public void entrypoint() {
            ObjectMapper om = new ObjectMapper();
            om.readValue("{}");
            om.enableDefaultTyping();
        }
    }
}

package custom;

import base.RuleSample;
import base.RuleSet;
import custom.sampleWithAlias.Main;

@RuleSet("custom/SampleWithAlias.yaml")
public abstract class SampleWithAlias implements RuleSample {
    static class PositiveMain extends SampleWithAlias {
        @Override
        public void entrypoint() {
            new Main().count("tainted");
        }
    }
}

package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i85.OutsideClass;
import issues.i85.AsClassField;

@RuleSet("issues/issue85.yaml")
public abstract class issue85 implements RuleSample {
    static class PositiveField extends issue85 {
        @Override
        public void entrypoint() {
            AsClassField acf = new AsClassField();
            OutsideClass oc = new OutsideClass(acf);
            String bad = oc.getValue();
            acf.useString(bad);
        }
    }
}

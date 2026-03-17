package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i92.Creator;
import issues.i92.Searcher;

@RuleSet("issues/issue92.yaml")
public abstract class issue92 implements RuleSample {
    static class PositiveTaint extends issue92 {
        @Override
        public void entrypoint() {
            Creator cr = new Creator();
            String p = cr.generate(42);
            Searcher idc = new Searcher();
            idc.search(p);
        }
    }
}

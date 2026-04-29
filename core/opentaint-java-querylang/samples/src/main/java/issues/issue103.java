package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i103.Builder;
import issues.i103.Holder;

@RuleSet("issues/issue103.yaml")
public abstract class issue103 implements RuleSample {
    // Direct flow: src → sink. Should fire.
    static class PositiveDirect extends issue103 {
        @Override
        public void entrypoint() {
            Object data = Holder.src();
            Holder.sink(data);
        }
    }

    // Sanitized via builder chain assignment. The `$RESULT = $X.safe($CT).body(...)`
    // sanitizer with focus-metavariable: $RESULT should clean the value
    // assigned to `result` so `sink(result)` doesn't fire.
    static class NegativeBuilderChain extends issue103 {
        @Override
        public void entrypoint() {
            Object data = Holder.src();
            Object result = Builder.make().safe("ct").body(data);
            Holder.sink(result);
        }
    }
}

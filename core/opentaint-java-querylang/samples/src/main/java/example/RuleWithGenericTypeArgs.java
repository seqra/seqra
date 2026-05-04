package example;

import base.RuleSample;
import base.RuleSet;
import java.util.Map;

@RuleSet("example/RuleWithGenericTypeArgs.yaml")
public abstract class RuleWithGenericTypeArgs implements RuleSample {

    void sink(String data) {}

    void methodWithGenericParam(Map<String, Object> m, String data) {
        sink(data);
    }

    void methodWithDifferentGenericParam(Map<String, String> m, String data) {
        sink(data);
    }

    final static class PositiveMatchingGenericParam extends RuleWithGenericTypeArgs {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, Object> m = null;
            methodWithGenericParam(m, data);
        }
    }

    final static class NegativeDifferentGenericParam extends RuleWithGenericTypeArgs {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, String> m = null;
            methodWithDifferentGenericParam(m, data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import java.util.Map;

/**
 * A8. Mixed metavar + concrete — {@code Map<$K, String>}.
 *
 * First slot is a metavariable {@code $K} (binds to any concrete type);
 * second slot is the concrete type {@code String}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: {@code Map<Integer, String>} — {@code $K} binds to
 *   {@code Integer}; second slot is {@code String}.</li>
 *   <li>Positive: {@code Map<String, String>} — {@code $K} binds to
 *   {@code String}; second slot is {@code String}.</li>
 *   <li>Negative: {@code Map<String, Integer>} — second slot is not
 *   {@code String}, rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithMixedMetavarConcrete.yaml")
public abstract class RuleWithMixedMetavarConcrete implements RuleSample {

    void sink(String data) {}

    void methodWithIntegerStringMap(Map<Integer, String> m, String data) {
        sink(data);
    }

    void methodWithStringStringMap(Map<String, String> m, String data) {
        sink(data);
    }

    void methodWithStringIntegerMap(Map<String, Integer> m, String data) {
        sink(data);
    }

    final static class PositiveIntegerStringMap extends RuleWithMixedMetavarConcrete {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<Integer, String> m = null;
            methodWithIntegerStringMap(m, data);
        }
    }

    final static class PositiveStringStringMap extends RuleWithMixedMetavarConcrete {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, String> m = null;
            methodWithStringStringMap(m, data);
        }
    }

    /**
     * Honest Negative: the second type argument is {@code Integer}, not the
     * required concrete {@code String}; the rule must NOT fire.
     */
    final static class NegativeSecondSlotNotString extends RuleWithMixedMetavarConcrete {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, Integer> m = null;
            methodWithStringIntegerMap(m, data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;

/**
 * A12. Parameter-position concrete-vs-metavar discrimination.
 *
 * Rule: {@code $RET $METHOD(List<String> $A, String $DATA) { ... sink($DATA); ... }}
 * — the first parameter is the concrete type {@code List<String>} (not a
 * metavar).
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: {@code void foo(List<String> x, String data)} — matches.</li>
 *   <li>Negative: {@code void foo(List<Integer> x, String data)} — first
 *   parameter type argument is {@code Integer}, not the required
 *   {@code String}; rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithParamConcreteListString.yaml")
public abstract class RuleWithParamConcreteListString implements RuleSample {

    void sink(String data) {}

    void methodWithListString(List<String> x, String data) {
        sink(data);
    }

    void methodWithListInteger(List<Integer> x, String data) {
        sink(data);
    }

    final static class PositiveListString extends RuleWithParamConcreteListString {
        @Override
        public void entrypoint() {
            String data = "tainted";
            List<String> x = null;
            methodWithListString(x, data);
        }
    }

    /**
     * Honest Negative: first parameter type argument is {@code Integer}, not
     * the required {@code String}.
     */
    final static class NegativeListInteger extends RuleWithParamConcreteListString {
        @Override
        public void entrypoint() {
            String data = "tainted";
            List<Integer> x = null;
            methodWithListInteger(x, data);
        }
    }
}

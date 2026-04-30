package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;

/**
 * A10. Deep nesting — {@code List<List<String>>}.
 *
 * Rule return type {@code List<List<String>> $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code List<List<String>>} — matches.</li>
 *   <li>Negative: method returns {@code List<String>} — missing outer
 *   nesting; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code List<List<Integer>>} — inner type
 *   argument mismatch; rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithDeepNesting.yaml")
public abstract class RuleWithDeepNesting implements RuleSample {

    void sink(String data) {}

    List<List<String>> methodReturningListListString(String data) {
        sink(data);
        return null;
    }

    List<String> methodReturningListString(String data) {
        sink(data);
        return null;
    }

    List<List<Integer>> methodReturningListListInteger(String data) {
        sink(data);
        return null;
    }

    final static class PositiveListListString extends RuleWithDeepNesting {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListListString(data);
        }
    }

    /**
     * Honest Negative: rule requires the outer nesting
     * {@code List<List<String>>}; {@code List<String>} is missing the outer
     * list.
     */
    final static class NegativeFlatListString extends RuleWithDeepNesting {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListString(data);
        }
    }

    /**
     * Honest Negative: inner type arg is {@code Integer}, not the required
     * {@code String}.
     */
    final static class NegativeInnerTypeMismatch extends RuleWithDeepNesting {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListListInteger(data);
        }
    }
}

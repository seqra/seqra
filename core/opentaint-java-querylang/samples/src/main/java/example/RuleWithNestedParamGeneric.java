package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;
import java.util.Map;

/**
 * A19. Nested generic in parameter — {@code List<Map<String, Integer>>}.
 *
 * Complement to A10 (nested generic in return position). The nested
 * container appears in parameter position.
 *
 * Rule: {@code $RET $METHOD(List<Map<String, Integer>> $X, ..., String $A, ...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method takes {@code List<Map<String, Integer>>, String}
 *   — matches.</li>
 *   <li>Negative: method takes {@code List<Map<String, String>>, String}
 *   — the inner-inner type arg differs; rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithNestedParamGeneric.yaml")
public abstract class RuleWithNestedParamGeneric implements RuleSample {

    void sink(String data) {}

    void methodWithListMapStringInteger(List<Map<String, Integer>> x, String data) {
        sink(data);
    }

    void methodWithListMapStringString(List<Map<String, String>> x, String data) {
        sink(data);
    }

    final static class PositiveListMapStringInteger extends RuleWithNestedParamGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodWithListMapStringInteger(null, data);
        }
    }

    /**
     * Honest Negative: inner-inner type argument is {@code String}, not the
     * required {@code Integer}.
     */
    final static class NegativeInnerInnerMismatch extends RuleWithNestedParamGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodWithListMapStringString(null, data);
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A22. Nested mixed containers — {@code Map<String, List<Integer>>}.
 *
 * Rule return type {@code Map<String, List<Integer>> $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code Map<String, List<Integer>>} —
 *   matches.</li>
 *   <li>Negative: method returns {@code Map<String, List<String>>} —
 *   inner-inner type arg differs; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code Map<Integer, List<Integer>>} —
 *   outer key type differs; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code Map<String, Set<Integer>>} — the
 *   middle container is {@code Set}, not {@code List}; rule must NOT
 *   fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithNestedMapListReturn.yaml")
public abstract class RuleWithNestedMapListReturn implements RuleSample {

    void sink(String data) {}

    Map<String, List<Integer>> methodReturningMapStringListInteger(String data) {
        sink(data);
        return null;
    }

    Map<String, List<String>> methodReturningMapStringListString(String data) {
        sink(data);
        return null;
    }

    Map<Integer, List<Integer>> methodReturningMapIntegerListInteger(String data) {
        sink(data);
        return null;
    }

    Map<String, Set<Integer>> methodReturningMapStringSetInteger(String data) {
        sink(data);
        return null;
    }

    final static class PositiveMapStringListInteger extends RuleWithNestedMapListReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningMapStringListInteger(data);
        }
    }

    /**
     * Honest Negative: inner-inner type arg is {@code String}, not the
     * required {@code Integer}.
     */
    final static class NegativeInnerInnerMismatch extends RuleWithNestedMapListReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningMapStringListString(data);
        }
    }

    /**
     * Honest Negative: outer key type is {@code Integer}, not the required
     * {@code String}.
     */
    final static class NegativeOuterKeyMismatch extends RuleWithNestedMapListReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningMapIntegerListInteger(data);
        }
    }

    /**
     * Honest Negative: middle container is {@code Set}, not the required
     * {@code List}.
     */
    final static class NegativeMiddleContainerMismatch extends RuleWithNestedMapListReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningMapStringSetInteger(data);
        }
    }
}

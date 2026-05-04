package example;

import base.RuleSample;
import base.RuleSet;

/**
 * A23. Array dimension mismatch — {@code String[][]}.
 *
 * Rule return type {@code String[][] $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code String[][]} — matches.</li>
 *   <li>Negative: method returns {@code String[]} — one fewer dimension;
 *   rule must NOT fire.</li>
 *   <li>Negative: method returns {@code String[][][]} — one extra
 *   dimension; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code Integer[][]} — wrong element type;
 *   rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithTwoDimArrayReturn.yaml")
public abstract class RuleWithTwoDimArrayReturn implements RuleSample {

    void sink(String data) {}

    String[][] methodReturningStringTwoDim(String data) {
        sink(data);
        return null;
    }

    String[] methodReturningStringOneDim(String data) {
        sink(data);
        return null;
    }

    String[][][] methodReturningStringThreeDim(String data) {
        sink(data);
        return null;
    }

    Integer[][] methodReturningIntegerTwoDim(String data) {
        sink(data);
        return null;
    }

    final static class PositiveStringTwoDim extends RuleWithTwoDimArrayReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringTwoDim(data);
        }
    }

    /**
     * Honest Negative: return type has one fewer dimension than required.
     */
    final static class NegativeStringOneDim extends RuleWithTwoDimArrayReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringOneDim(data);
        }
    }

    /**
     * Honest Negative: return type has one extra dimension beyond
     * required.
     */
    final static class NegativeStringThreeDim extends RuleWithTwoDimArrayReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringThreeDim(data);
        }
    }

    /**
     * Honest Negative: element type is {@code Integer}, not the required
     * {@code String}.
     */
    final static class NegativeIntegerTwoDim extends RuleWithTwoDimArrayReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningIntegerTwoDim(data);
        }
    }
}

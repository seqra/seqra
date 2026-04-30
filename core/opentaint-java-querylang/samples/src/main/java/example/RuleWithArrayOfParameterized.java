package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;

/**
 * A15. Array of parameterized type — {@code List<String>[]}.
 *
 * Rule return type {@code List<String>[] $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code List<String>[]}.</li>
 *   <li>Negative: method returns {@code List<String>} — missing array
 *   dimension; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code List<Integer>[]} — inner type arg
 *   differs; rule must NOT fire.</li>
 *   <li>Negative: method returns {@code String[]} — wrong outer type.</li>
 * </ul>
 *
 * {@code List<String>[]} is a legal return-type declaration in Java; we use
 * {@code @SuppressWarnings("unchecked")} to silence the generic-array
 * creation warning on the negative helpers.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@RuleSet("example/RuleWithArrayOfParameterized.yaml")
public abstract class RuleWithArrayOfParameterized implements RuleSample {

    void sink(String data) {}

    List<String>[] methodReturningListStringArray(String data) {
        sink(data);
        return null;
    }

    List<String> methodReturningListString(String data) {
        sink(data);
        return null;
    }

    List<Integer>[] methodReturningListIntegerArray(String data) {
        sink(data);
        return null;
    }

    String[] methodReturningStringArray(String data) {
        sink(data);
        return null;
    }

    final static class PositiveListStringArray extends RuleWithArrayOfParameterized {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListStringArray(data);
        }
    }

    /**
     * Honest Negative: missing the outer array dimension — return is
     * {@code List<String>}, not {@code List<String>[]}.
     */
    final static class NegativeListStringNoArray extends RuleWithArrayOfParameterized {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListString(data);
        }
    }

    /**
     * Honest Negative: inner type argument is {@code Integer}, not the
     * required {@code String}.
     */
    final static class NegativeListIntegerArray extends RuleWithArrayOfParameterized {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListIntegerArray(data);
        }
    }

    /**
     * Honest Negative: outer type is {@code String[]}, not {@code List<...>[]}.
     */
    final static class NegativeStringArray extends RuleWithArrayOfParameterized {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningStringArray(data);
        }
    }
}

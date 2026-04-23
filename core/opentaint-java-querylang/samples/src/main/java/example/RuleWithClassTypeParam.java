package example;

import base.RuleSample;
import base.RuleSet;

/**
 * A20. {@code Class<$T>} parameter — reflection-style type token.
 *
 * Rule: {@code $RET $METHOD(Class<$T> $C, ..., String $A, ...)} — first
 * parameter is {@code Class<...>} where {@code $T} metavar binds to any
 * concrete type.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method takes {@code Class<String>, String} — matches.</li>
 *   <li>Positive: method takes {@code Class<Integer>, String} — matches
 *   (metavar binds to any concrete type).</li>
 *   <li>Negative: method takes {@code String, String} — first parameter is
 *   not {@code Class<...>}; rule must NOT fire.</li>
 * </ul>
 */
@RuleSet("example/RuleWithClassTypeParam.yaml")
public abstract class RuleWithClassTypeParam implements RuleSample {

    void sink(String data) {}

    void methodWithClassStringParam(Class<String> c, String data) {
        sink(data);
    }

    void methodWithClassIntegerParam(Class<Integer> c, String data) {
        sink(data);
    }

    void methodWithStringParam(String c, String data) {
        sink(data);
    }

    final static class PositiveClassString extends RuleWithClassTypeParam {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodWithClassStringParam(String.class, data);
        }
    }

    final static class PositiveClassInteger extends RuleWithClassTypeParam {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodWithClassIntegerParam(Integer.class, data);
        }
    }

    /**
     * Honest Negative: first parameter is a plain {@code String}, not
     * {@code Class<...>}; rule must NOT fire.
     */
    final static class NegativeStringParam extends RuleWithClassTypeParam {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodWithStringParam("x", data);
        }
    }
}

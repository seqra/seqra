package example;

import base.RuleSample;
import base.RuleSet;

/**
 * A17. Concrete return type discriminates from a different type.
 *
 * Rule: {@code String $METHOD(String $A, String $DATA) { ... sink($DATA); ... }}
 * — return type is the concrete type {@code String}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: a method returning {@code String} — matches.</li>
 *   <li>Negative: a method returning {@code Integer} — rule must NOT fire,
 *   even though the parameter list matches.</li>
 * </ul>
 *
 * This is the simplified (non-inheritance) formulation. A fuller test of
 * inheritance substitution — where the return type of an overridden method
 * of a parameterized base class surfaces as a concrete substituted type —
 * would require dedicated design and is deferred.
 */
@RuleSet("example/RuleWithConcreteReturnDiscrim.yaml")
public abstract class RuleWithConcreteReturnDiscrim implements RuleSample {

    void sink(String data) {}

    String methodReturningString(String a, String data) {
        sink(data);
        return null;
    }

    Integer methodReturningInteger(String a, String data) {
        sink(data);
        return null;
    }

    final static class PositiveStringReturn extends RuleWithConcreteReturnDiscrim {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningString("x", data);
        }
    }

    /**
     * Honest Negative: the return type is {@code Integer}, not the required
     * concrete {@code String}; rule must NOT fire.
     */
    final static class NegativeIntegerReturn extends RuleWithConcreteReturnDiscrim {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningInteger("x", data);
        }
    }
}

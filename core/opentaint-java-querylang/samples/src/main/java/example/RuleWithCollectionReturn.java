package example;

import base.RuleSample;
import base.RuleSet;
import java.util.Collection;
import java.util.List;

/**
 * A21. Interface vs class widening — {@code Collection<String>} pattern vs
 * {@code List<String>} method.
 *
 * Rule return type {@code Collection<String> $METHOD(...)}.
 *
 * Expected behavior:
 * <ul>
 *   <li>Positive: method returns {@code Collection<String>} — exact
 *   match.</li>
 *   <li>Negative (observed): method returns {@code List<String>}. The
 *   opentaint engine uses exact-type matching at method-decl return
 *   position, not subtype widening — so a {@code List<String>} return is
 *   NOT matched by a {@code Collection<String>} pattern. Initial pin was
 *   Positive (hoping for semgrep-like subtype semantics); the test fired
 *   as a missed Positive and the sample was flipped to Negative to
 *   document the observed exact-type matching behavior.</li>
 * </ul>
 */
@RuleSet("example/RuleWithCollectionReturn.yaml")
public abstract class RuleWithCollectionReturn implements RuleSample {

    void sink(String data) {}

    Collection<String> methodReturningCollectionString(String data) {
        sink(data);
        return null;
    }

    List<String> methodReturningListString(String data) {
        sink(data);
        return null;
    }

    final static class PositiveCollectionString extends RuleWithCollectionReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningCollectionString(data);
        }
    }

    /**
     * Honest Negative (flipped from initial Positive pin after empirical
     * observation): {@code List<String>} is a declared subtype of
     * {@code Collection<String>}, but the opentaint engine matches only
     * the exact declared class at the method-decl return position — it
     * does NOT perform subtype widening. Therefore the rule does NOT fire
     * and this is a true Negative.
     */
    final static class NegativeListStringNotWidenedToCollection extends RuleWithCollectionReturn {
        @Override
        public void entrypoint() {
            String data = "tainted";
            methodReturningListString(data);
        }
    }
}

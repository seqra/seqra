package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Repro for the suspicion that the {@code $UNTRUSTED = ($TYPE $REQ).$METHOD(...)}
 * source pattern only fires when the enclosing entry-point method has a
 * specific name (the one matched by the rule's entry-point alternative).
 *
 * For the production servlet source rule, the entry-point alternative only
 * recognises {@code doGet}/{@code doPost}/... by exact name. The
 * assignment alternative should be independent. We test it here against a
 * method named {@code unrelated} to verify.
 */
@RuleSet("analyzerbugs/AssignmentSourceBug.yaml")
public abstract class AssignmentSourceBug implements RuleSample {

    public static class Holder {
        public String getValue() { return "tainted"; }
    }

    String src() { return "tainted"; }
    void sink(String s) {}

    public static class PositiveAssignmentFromGetter extends AssignmentSourceBug {
        Holder h = new Holder();
        @Override public void entrypoint() {
            // Method name `entrypoint` is the IFDS entry; the assignment is
            // the source.
            String x = h.getValue();
            sink(x);
        }
    }
}

package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Replicates the exact rules/test pattern: a method takes a parameter typed
 * as a JDK/library class, and the body extracts a String via a getter call
 * on that parameter. The {@code $UNTRUSTED = ($TYPE $X).$METHOD(...)}
 * source pattern must fire on the getter call inside any method body.
 */
@RuleSet("analyzerbugs/ServletParamSourceBug.yaml")
public abstract class ServletParamSourceBug implements RuleSample {

    void sink(String s) {}

    public static class FakeReq {
        public String getQueryString() { return null; }
    }

    public static class PositiveAssignmentInsideNonDoGet extends ServletParamSourceBug {
        @Override public void entrypoint() {
            doStuff(new FakeReq());
        }

        // Harness method whose name does NOT match any entry-point alternative.
        void doStuff(FakeReq req) {
            String qs = req.getQueryString();
            sink(qs);
        }
    }
}

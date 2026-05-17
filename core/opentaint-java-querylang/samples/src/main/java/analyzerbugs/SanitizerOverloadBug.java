package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Minimal repro for the analyzer bug where a static-method sanitizer pattern
 * matches a method ONLY when the target class has a single overload of that
 * method, and silently fails when overloads exist.
 *
 * Helper class {@link Helper#singleOverload} has exactly one signature, and
 * {@link Helper#multiOverload} has two; the rule lists both as sanitizers
 * with identical pattern syntax ({@code analyzerbugs.Helper.method(...)}).
 *
 * Negative samples flow the tainted source through each sanitizer once.
 * The single-overload case is correctly recognised today; the multi-overload
 * case fires a false positive — this is the bug.
 */
@RuleSet("analyzerbugs/SanitizerOverloadBug.yaml")
public abstract class SanitizerOverloadBug implements RuleSample {

    public static class Helper {
        // Single overload — analyzer recognises the sanitizer.
        public static String singleOverload(String in) { return in; }

        // Two overloads — analyzer fails to apply the sanitizer.
        public static String multiOverload(String in) { return in; }
        public static String multiOverload(String in, boolean flag) { return in; }
    }

    String src() { return "tainted"; }
    void sink(String s) {}

    /** Positive control: no sanitizer at all, must flag. */
    public static class PositiveDirect extends SanitizerOverloadBug {
        @Override public void entrypoint() {
            String t = src();
            sink(t);
        }
    }

    /** Negative: single-overload sanitizer DOES break the dataflow. */
    public static class NegativeSingleOverload extends SanitizerOverloadBug {
        @Override public void entrypoint() {
            String t = src();
            String clean = Helper.singleOverload(t);
            sink(clean);
        }
    }

    /** Negative: multi-overload sanitizer SHOULD break the dataflow but doesn't. */
    public static class NegativeMultiOverload extends SanitizerOverloadBug {
        @Override public void entrypoint() {
            String t = src();
            String clean = Helper.multiOverload(t);
            sink(clean);
        }
    }
}

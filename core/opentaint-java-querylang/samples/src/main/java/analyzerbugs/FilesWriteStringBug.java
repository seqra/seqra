package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Real-JDK repro of the {@code Files.writeString} mismatch observed in
 * rules/test. The rule's pattern-sinks list both {@code Files.readAllBytes}
 * and {@code Files.writeString}; both calls are static methods on
 * {@code java.nio.file.Files} with structurally identical patterns. In
 * rules/test only the first one fires.
 *
 * Synthetic look-alike (analyzerbugs.SinkPatternBug) works fine, so the bug
 * is specific to the JDK class. This test pins both forms directly.
 */
@RuleSet("analyzerbugs/FilesWriteStringBug.yaml")
public abstract class FilesWriteStringBug implements RuleSample {

    String src() { return "/etc/passwd"; }

    /** Baseline: Files.readAllBytes sink is reached by the tainted Path. */
    public static class PositiveReadAllBytes extends FilesWriteStringBug {
        @Override public void entrypoint() {
            String name = src();
            java.nio.file.Path path = java.nio.file.Paths.get(name);
            try {
                java.nio.file.Files.readAllBytes(path);
            } catch (java.io.IOException ignored) {}
        }
    }

    /** Bug case: Files.writeString sink should be reached by the same Path. */
    public static class PositiveWriteString extends FilesWriteStringBug {
        @Override public void entrypoint() {
            String name = src();
            java.nio.file.Path path = java.nio.file.Paths.get(name);
            try {
                java.nio.file.Files.writeString(path, "data");
            } catch (java.io.IOException ignored) {}
        }
    }

    /**
     * Closest possible mirror of the rules/test
     * PathTraversalNioSinksSamples$UnsafeWriteStringServlet that exhibits the
     * FN: tainted string flows through a constant-prefixed concat, then
     * Paths.get, then a Files.writeString call whose result is discarded.
     */
    public static class PositiveWriteStringConcatThenDiscard extends FilesWriteStringBug {
        @Override public void entrypoint() {
            String fileName = src();
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.writeString(path, "data");
            } catch (java.io.IOException ignored) {}
        }
    }

}

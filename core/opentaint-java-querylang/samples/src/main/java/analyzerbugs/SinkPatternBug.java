package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Minimal repro for an asymmetry observed in rules/test: with two
 * pattern-sinks of identical syntactic shape — {@code Files.readAllBytes($F, ...)}
 * and {@code Files.writeString($F, ...)} — only the first one matches a tainted
 * argument flowing from the entry point through a Path. The other one silently
 * misses the call.
 *
 * This test replays the same shape against hand-rolled static methods so we can
 * decide whether the bug is in the analyzer's pattern matcher, the JIR
 * resolution of {@code java.nio.file.Files}, or something else specific to that
 * JDK class.
 */
@RuleSet("analyzerbugs/SinkPatternBug.yaml")
public abstract class SinkPatternBug implements RuleSample {

    public static class FilesLike {
        // Returns a result; analyzer recognises the matching sink pattern.
        public static byte[] readPath(java.nio.file.Path p) {
            return new byte[0];
        }

        // Returns a result that callers commonly discard. The pattern is
        // structurally identical to readPath; both should match a sink rule.
        public static java.nio.file.Path writePath(java.nio.file.Path p, CharSequence data) {
            return p;
        }
    }

    String src() { return "/etc/passwd"; }

    /** Positive control: read sink reachable from a tainted Path. */
    public static class PositiveReadPath extends SinkPatternBug {
        @Override public void entrypoint() {
            String name = src();
            java.nio.file.Path path = java.nio.file.Paths.get(name);
            FilesLike.readPath(path);
        }
    }

    /** Positive: write sink, return value discarded. */
    public static class PositiveWritePathDiscarded extends SinkPatternBug {
        @Override public void entrypoint() {
            String name = src();
            java.nio.file.Path path = java.nio.file.Paths.get(name);
            FilesLike.writePath(path, "data");
        }
    }

    /** Positive: write sink, return value consumed. */
    public static class PositiveWritePathConsumed extends SinkPatternBug {
        @Override public void entrypoint() {
            String name = src();
            java.nio.file.Path path = java.nio.file.Paths.get(name);
            java.nio.file.Path written = FilesLike.writePath(path, "data");
            consume(written);
        }
    }

    void consume(java.nio.file.Path p) {}
}

package phase3;

import base.RuleSample;
import base.RuleSet;

import java.util.Arrays;

// Phase 3 config coverage: each Positive flows taint from a source, through a
// changed passthrough entry (Phase 1 fold or Phase 2 collapse removal), to a sink.
// Array sources and sinks include their elements; the array identity alone is clean.
@RuleSet("phase3/StdlibCoverage.yaml")
public abstract class StdlibCoverage implements RuleSample {
    public String[] asrc() { return new String[]{"tainted"}; }
    public String[] asrcPlain() { return new String[]{"tainted"}; }
    public char[] csrc() { return new char[]{'x'}; }
    public void arrSink(String[] s) {}
    public void strSink(String s) {}

    // Arrays.copyOf preserves element taint in the newly allocated array.
    static class PositiveArraysCopyOf extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = asrc();
            String[] c = Arrays.copyOf(d, 1);
            arrSink(c);
        }
    }

    // Arrays.copyOfRange preserves element taint in the selected range.
    static class PositiveArraysCopyOfRange extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = asrc();
            String[] c = Arrays.copyOfRange(d, 0, 1);
            arrSink(c);
        }
    }

    // append(char[]) reads array elements into the builder content slot.
    static class PositiveStringBuilderAppendChars extends StdlibCoverage {
        @Override public void entrypoint() {
            char[] ch = csrc();
            StringBuilder sb = new StringBuilder();
            sb.append(ch);
            strSink(sb.toString());
        }
    }

    // Negative: a locally-built clean array must not be reported.
    static class NegativeCleanCopyOf extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = new String[]{"safe"};
            String[] c = Arrays.copyOf(d, 1);
            arrSink(c);
        }
    }

    // A base-only source does not mark the elements copied into a fresh array.
    static class NegativeBaseOnlyArrayCopy extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = asrcPlain();
            arrSink(Arrays.copyOf(d, 1));
        }
    }
}

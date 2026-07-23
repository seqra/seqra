package phase3;

import base.RuleSample;
import base.RuleSet;

import java.util.Arrays;

// Phase 3 config coverage: each Positive flows taint from a source, through a
// changed passthrough entry (Phase 1 fold or Phase 2 collapse removal), to a sink.
// A Positive turning red means the config change dropped a real flow.
@RuleSet("phase3/StdlibCoverage.yaml")
public abstract class StdlibCoverage implements RuleSample {
    public String[] asrc() { return new String[]{"tainted"}; }
    public char[] csrc() { return new char[]{'x'}; }
    public void arrSink(String[] s) {}
    public void strSink(String s) {}

    // Phase 1 fold: java.util.Arrays#copyOf  [arg0,*]->[result,*]  =>  arg0->result
    static class PositiveArraysCopyOf extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = asrc();
            String[] c = Arrays.copyOf(d, 1);
            arrSink(c);
        }
    }

    // Phase 1 fold: java.util.Arrays#copyOfRange
    static class PositiveArraysCopyOfRange extends StdlibCoverage {
        @Override public void entrypoint() {
            String[] d = asrc();
            String[] c = Arrays.copyOfRange(d, 0, 1);
            arrSink(c);
        }
    }

    // Phase 2 collapse removed: java.lang.AbstractStringBuilder#append(char[])
    // kept whole copy arg0->this; whole char[] taint must still reach the builder.
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
}

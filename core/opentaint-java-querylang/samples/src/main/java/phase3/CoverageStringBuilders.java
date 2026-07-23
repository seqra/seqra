package phase3;

import base.RuleSample;
import base.RuleSet;

// Phase 3 core coverage: char[] overloads of the string-builder append/insert
// entries. Each Positive flows a tainted char[] through the builder (arg -> this)
// and reads it back via toString. StringBuilder.append(char[]) is already covered
// in StdlibCoverage; here we exercise the remaining char[] overloads. The abstract
// java.lang.AbstractStringBuilder#append/#insert entries are non-instantiable and
// are therefore covered transitively through StringBuilder / StringBuffer below.
@RuleSet("phase3/CoverageStringBuilders.yaml")
public abstract class CoverageStringBuilders implements RuleSample {
    public char[] csrc() { return new char[]{'x'}; }
    public void strSink(String s) {}

    // java.lang.StringBuffer#append(char[]) : arg0 -> this
    static class PositiveStringBufferAppendChars extends CoverageStringBuilders {
        @Override public void entrypoint() {
            char[] ch = csrc();
            StringBuffer sb = new StringBuffer();
            sb.append(ch);
            strSink(sb.toString());
        }
    }

    // java.lang.StringBuilder#insert(int, char[]) : arg1 -> this
    static class PositiveStringBuilderInsertChars extends CoverageStringBuilders {
        @Override public void entrypoint() {
            char[] ch = csrc();
            StringBuilder sb = new StringBuilder();
            sb.insert(0, ch);
            strSink(sb.toString());
        }
    }

    // java.lang.StringBuffer#insert(int, char[]) : arg1 -> this
    static class PositiveStringBufferInsertChars extends CoverageStringBuilders {
        @Override public void entrypoint() {
            char[] ch = csrc();
            StringBuffer sb = new StringBuffer();
            sb.insert(0, ch);
            strSink(sb.toString());
        }
    }

    // Negative: a clean local char[] must not be reported.
    static class NegativeCleanAppendChars extends CoverageStringBuilders {
        @Override public void entrypoint() {
            char[] ch = new char[]{'y'};
            StringBuffer sb = new StringBuffer();
            sb.append(ch);
            strSink(sb.toString());
        }
    }
}

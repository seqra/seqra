package phase3;

import base.RuleSample;
import base.RuleSet;

import java.nio.charset.StandardCharsets;
import java.text.ChoiceFormat;
import java.text.DecimalFormat;
import java.util.Locale;

// Phase 3 core coverage: java.lang.String factory overloads plus java.text
// pattern/setter entries. Each Positive flows taint from a source, through the
// changed passthrough, and back out to a sink. The java.text cases (ChoiceFormat
// ctor, DecimalFormat set*) rely on arg -> this whole-object taint plus a guessed
// getter accessor (AnyAccessorEnabled) to read the value back.
@RuleSet("phase3/CoverageStrings.yaml")
public abstract class CoverageStrings implements RuleSample {
    public Object[] osrc() { return new Object[]{"tainted"}; }
    public byte[] bsrc() { return new byte[]{1}; }
    public String ssrc() { return "tainted"; }
    public void strSink(String s) {}

    // java.lang.String#format(Locale, String, Object[]) : arg2 -> result
    static class PositiveStringFormatLocale extends CoverageStrings {
        @Override public void entrypoint() {
            Object[] a = osrc();
            String s = String.format(Locale.ROOT, "%s", a);
            strSink(s);
        }
    }

    // java.lang.String#<init>(byte[], int, int, Charset) : arg0 -> this
    static class PositiveStringInitBytesCharset extends CoverageStrings {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            String s = new String(b, 0, b.length, StandardCharsets.UTF_8);
            strSink(s);
        }
    }

    // java.text.ChoiceFormat#<init>(String) : arg0 -> this (read back via toPattern)
    static class PositiveChoiceFormatPattern extends CoverageStrings {
        @Override public void entrypoint() {
            String p = ssrc();
            ChoiceFormat cf = new ChoiceFormat(p);
            strSink(cf.toPattern());
        }
    }

    // java.text set.+(String) : arg0 -> this (DecimalFormat#setPositivePrefix,
    // read back via getPositivePrefix)
    static class PositiveDecimalFormatSetPrefix extends CoverageStrings {
        @Override public void entrypoint() {
            String p = ssrc();
            DecimalFormat df = new DecimalFormat();
            df.setPositivePrefix(p);
            strSink(df.getPositivePrefix());
        }
    }

    // Negative: a clean local value must not be reported.
    static class NegativeCleanStringFormat extends CoverageStrings {
        @Override public void entrypoint() {
            Object[] a = new Object[]{"safe"};
            String s = String.format(Locale.ROOT, "%s", a);
            strSink(s);
        }
    }
}

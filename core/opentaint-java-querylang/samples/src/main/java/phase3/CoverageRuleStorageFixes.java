package phase3;

import base.RuleSample;
import base.RuleSet;

// Behavioural coverage for nine bugs fixed by removing the generic <rule-storage>
// carrier slot from the Java taint-model config. Each Positive proves the flow the
// fix restored/kept working; each paired Negative proves the two properties that
// used to collide through the shared slot are still kept apart.
@RuleSet("phase3/CoverageRuleStorageFixes.yaml")
public abstract class CoverageRuleStorageFixes implements RuleSample {
    public String ssrc() { return "tainted"; }
    public byte[] bsrc() { return new byte[]{1}; }
    public void strSink(String s) {}
    public void bytesSink(byte[] b) {}
    public void objSink(Object o) {}

    // 1. java.nio.ByteBuffer#wrap(byte[]) element carrier: before the fix, the
    // element taint on the wrapped array was dropped by the whole-copy re-root.
    static class PositiveByteBufferWrapArray extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(b);
            bytesSink(buf.array());
        }
    }

    // 2. java.text.MessageFormat#format(String, Object[]) element carrier: the
    // whole-copy re-rooted the array element onto a scalar result, losing it.
    static class PositiveMessageFormatArrayElement extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            String s = ssrc();
            String out = java.text.MessageFormat.format("{0}", new Object[]{ s });
            strSink(out);
        }
    }

    // 3. javax.naming.NameClassPair: name/className/fullName used to share one slot.
    static class PositiveNameClassPairGetName extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = new javax.naming.NameClassPair("a", "b");
            p.setName(ssrc());
            strSink(p.getName());
        }
    }

    static class NegativeNameClassPairGetClassName extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = new javax.naming.NameClassPair("a", "b");
            p.setName(ssrc());
            strSink(p.getClassName());
        }
    }

    // 4. javax.naming.Reference: the factory getters used to read the className slot.
    static class PositiveReferenceGetFactoryClassName extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            javax.naming.Reference r =
                new javax.naming.Reference("clean.Class", ssrc(), "http://example/");
            strSink(r.getFactoryClassName());
        }
    }

    static class NegativeReferenceGetClassName extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            javax.naming.Reference r =
                new javax.naming.Reference("clean.Class", ssrc(), "http://example/");
            strSink(r.getClassName());
        }
    }

    // 5. javax.naming.ldap.BasicControl: getID used to leak the encoded value.
    static class PositiveBasicControlGetEncodedValue extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            javax.naming.ldap.BasicControl c = new javax.naming.ldap.BasicControl("1.2", false, b);
            bytesSink(c.getEncodedValue());
        }
    }

    // FAILS as of this writing (see .superpowers/sdd/e2e-fixes-report.md): the
    // specific field-sensitive bug (a bogus encodedValue->oid String#bytes bridge)
    // was fixed, but BasicControl#<init> still copies arg(2) (encodedValue) onto
    // the whole "this" object (0587c523d6, kept deliberately for ctrlSink(c)-style
    // callers), and AnyAccessorEnabled lets that whole-object mark leak through
    // getID() even though getID()'s own config is field-sensitive-only. Expected:
    // no finding. Actual: a finding is reported.
    static class NegativeBasicControlGetID extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            javax.naming.ldap.BasicControl c = new javax.naming.ldap.BasicControl("1.2", false, b);
            strSink(c.getID());
        }
    }

    // 6. javax.naming.ldap.SortControl#<init>(String, boolean): this constructor's
    // model was deleted and restored; without it the object carries no taint.
    static class PositiveSortControlStringCtor extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            try {
                javax.naming.ldap.SortControl c = new javax.naming.ldap.SortControl(ssrc(), true);
                objSink(c);
            } catch (java.io.IOException e) {
            }
        }
    }

    // 7. javax.script.ScriptContext#setAttribute: the model stored arg(0) (the
    // attribute name) instead of arg(1) (its value), so the value never propagated.
    static class PositiveScriptContextAttributeValue extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            javax.script.SimpleScriptContext ctx = new javax.script.SimpleScriptContext();
            ctx.setAttribute("k", ssrc(), javax.script.ScriptContext.ENGINE_SCOPE);
            objSink(ctx.getAttribute("k"));
        }
    }

    // 8. java.text.DateFormatSymbols: a wildcard matcher used to route all six
    // array setters into the single weekdays slot.
    static class PositiveDateFormatSymbolsMonths extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DateFormatSymbols dfs = new java.text.DateFormatSymbols();
            dfs.setMonths(new String[]{ ssrc() });
            strSink(dfs.getMonths()[0]);
        }
    }

    static class NegativeDateFormatSymbolsWeekdays extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DateFormatSymbols dfs = new java.text.DateFormatSymbols();
            dfs.setMonths(new String[]{ ssrc() });
            strSink(dfs.getWeekdays()[0]);
        }
    }

    // Probes whether the generic {set.+}/{get.+} whole-object channel on
    // DateFormatSymbols is still live. getLocalPatternChars returns a scalar
    // String, so unlike the array getters it can observe a base-level mark.
    static class NegativeDateFormatSymbolsLocalPatternChars extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DateFormatSymbols dfs = new java.text.DateFormatSymbols();
            dfs.setMonths(new String[]{ ssrc() });
            strSink(dfs.getLocalPatternChars());
        }
    }

    // Companion positive case: proves the localPatternChars slot itself still
    // carries taint end to end now that the generic whole-object channel above
    // is closed.
    static class PositiveDateFormatSymbolsLocalPatternChars extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DateFormatSymbols dfs = new java.text.DateFormatSymbols();
            dfs.setLocalPatternChars(ssrc());
            strSink(dfs.getLocalPatternChars());
        }
    }

    // 9. java.text.DecimalFormatSymbols: four String setters were funnelled into
    // one slot.
    static class PositiveDecimalFormatSymbolsNaN extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DecimalFormatSymbols dfs = new java.text.DecimalFormatSymbols();
            dfs.setNaN(ssrc());
            strSink(dfs.getNaN());
        }
    }

    // FAILS as of this writing (see .superpowers/sdd/e2e-fixes-report.md): the
    // per-property setters are now field-sensitive (9a9141d5c), but that commit's
    // own message says the generic `set.+` whole-object taintCopyOnly twin on
    // DecimalFormatSymbols ("the bare whole-object taintCopyOnly twins are left
    // untouched") is deliberately kept, and AnyAccessorEnabled lets it leak
    // through any getter. Expected: no finding. Actual: a finding is reported.
    static class NegativeDecimalFormatSymbolsCurrencySymbol extends CoverageRuleStorageFixes {
        @Override public void entrypoint() {
            java.text.DecimalFormatSymbols dfs = new java.text.DecimalFormatSymbols();
            dfs.setNaN(ssrc());
            strSink(dfs.getCurrencySymbol());
        }
    }
}

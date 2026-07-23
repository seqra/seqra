package phase3;

import base.RuleSample;
import base.RuleSet;

// Behavioural coverage for taint isolation between per-property vfield slots on beans
// this branch split off a shared/whole-object slot, but that never got a Positive/Negative
// pair proving the split actually holds at runtime. Every Negative sink below reads a
// SCALAR getter (String / boxed primitive / single Object) on purpose: a taint mark on an
// object's whole-object base does not flow into an array-element read, so an array-getter
// sink can pass for the wrong reason (see NegativeDateFormatSymbolsWeekdays in
// CoverageRuleStorageFixes.java, which stayed green while the underlying leak was live).
@RuleSet("phase3/CoverageBeanIsolation.yaml")
public abstract class CoverageBeanIsolation implements RuleSample {
    public String ssrc() { return "tainted"; }
    public void strSink(String s) {}
    public void objSink(Object o) {}

    // 1. javax.naming.ldap.SortKey: attributeID vs matchingRuleID.
    static class PositiveSortKeyAttributeId extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.ldap.SortKey k = new javax.naming.ldap.SortKey(ssrc(), true, "clean");
            strSink(k.getAttributeID());
        }
    }

    // FAILS as of this writing: javax.naming.ldap.SortKey#<init>(String, boolean, String)'s
    // config entry copies BOTH arg(0) (attributeId) and arg(2) (matchingRuleId) onto the
    // field-sensitive slots AND onto the whole "this" object in the same entry, and both
    // SortKey#getAttributeID and SortKey#getMatchingRuleID have their own explicit
    // `from: this to: result` copy line (not merely an AnyAccessorEnabled artifact) --
    // so either property leaks into the other getter unconditionally. Expected: no
    // finding. Actual: a finding is reported.
    static class NegativeSortKeyMatchingRuleIdNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.ldap.SortKey k = new javax.naming.ldap.SortKey(ssrc(), true, "clean");
            strSink(k.getMatchingRuleID());
        }
    }

    // 2. javax.naming.ldap.ExtendedRequest is SKIPPED: it is an interface (getID scalar
    // String vs getEncodedValue byte[]), and the only public concrete JDK implementation,
    // javax.naming.ldap.StartTlsRequest, is immutable -- its no-arg constructor hardcodes
    // a fixed OID for getID() and getEncodedValue() always returns null, so there is no
    // way to inject taint into either property without fabricating a non-JDK impl.

    // 3. javax.naming.ldap.Rdn: type vs value, both directions.
    static class PositiveRdnGetType extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            try {
                javax.naming.ldap.Rdn r = new javax.naming.ldap.Rdn(ssrc(), "cleanValue");
                strSink(r.getType());
            } catch (javax.naming.InvalidNameException e) {
            }
        }
    }

    // FAILS as of this writing: javax.naming.ldap.Rdn#<init>(String, Object)'s config
    // entries only copy `arg(*) -> this` (whole object, no field split at all) -- there is
    // no field-sensitive write of arg(0)/arg(1) into .Rdn#type/.Rdn#value for this
    // constructor overload, so the whole-object mark set by the tainted type argument
    // leaks into getValue() (which does read the field-sensitive .Rdn#value slot, but
    // AnyAccessorEnabled also lets the whole-object mark satisfy that read). Expected: no
    // finding. Actual: a finding is reported.
    static class NegativeRdnValueNoLeakFromType extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            try {
                javax.naming.ldap.Rdn r = new javax.naming.ldap.Rdn(ssrc(), "cleanValue");
                objSink(r.getValue());
            } catch (javax.naming.InvalidNameException e) {
            }
        }
    }

    static class PositiveRdnGetValue extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            try {
                javax.naming.ldap.Rdn r = new javax.naming.ldap.Rdn("cleanType", ssrc());
                objSink(r.getValue());
            } catch (javax.naming.InvalidNameException e) {
            }
        }
    }

    // Same root cause as NegativeRdnValueNoLeakFromType, mirrored: the constructor's
    // whole-object mark (set here via arg(1), the value) leaks into getType() even though
    // type and value are meant to be independent slots.
    static class NegativeRdnTypeNoLeakFromValue extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            try {
                javax.naming.ldap.Rdn r = new javax.naming.ldap.Rdn("cleanType", ssrc());
                strSink(r.getType());
            } catch (javax.naming.InvalidNameException e) {
            }
        }
    }

    // 4. javax.script.SimpleScriptContext: attribute vs bindings. getBindings(int) returns
    // a Bindings object (not scalar), so per the task's own soundness rule we cannot use it
    // as a Negative sink. Instead: setAttribute("k", ssrc(), ENGINE_SCOPE) must not leak
    // into a DIFFERENT attribute name's getAttribute("other") read -- a sound scalar
    // negative that pins the attribute slot is not a whole-object channel.
    static class PositiveScriptContextAttribute extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.script.SimpleScriptContext ctx = new javax.script.SimpleScriptContext();
            ctx.setAttribute("k", ssrc(), javax.script.ScriptContext.ENGINE_SCOPE);
            objSink(ctx.getAttribute("k"));
        }
    }

    // FAILS as of this writing: javax.script.ScriptContext#setAttribute(String, Object,
    // int)'s config entry copies arg(1) (the value) into a single, name-insensitive
    // .ScriptContext#attribute#java.lang.Object vfield -- there is no per-attribute-name
    // discrimination (the String key at arg(0) is not part of the vfield identity, the
    // same way java.util.Map's MapValue slot conflates all keys). getAttribute(String)
    // reads that same undifferentiated slot regardless of the name it is called with, so
    // a value stored under "k" is observable under "other" too. Expected: no finding.
    // Actual: a finding is reported.
    static class NegativeScriptContextDifferentAttributeNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.script.SimpleScriptContext ctx = new javax.script.SimpleScriptContext();
            ctx.setAttribute("k", ssrc(), javax.script.ScriptContext.ENGINE_SCOPE);
            objSink(ctx.getAttribute("other"));
        }
    }

    // 5. java.text.ChoiceFormat: pattern (toPattern, scalar) vs limits (getLimits,
    // double[] -- not a scalar sink). ChoiceFormat's only other scalar-ish output is
    // format(double), which computes a formatted string from the *limits* table, not from
    // the pattern text -- it is not a read of a sibling property and would not be a sound
    // "does setting pattern leak elsewhere" probe. There is no clean scalar non-leak target
    // on this class, so it is covered Positive-only.
    static class PositiveChoiceFormatPattern extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.ChoiceFormat cf = new java.text.ChoiceFormat("0#zero|1#one");
            cf.applyPattern(ssrc());
            strSink(cf.toPattern());
        }
    }

    // 6. java.text.MessageFormat: pattern (toPattern, scalar) vs locale (getLocale, scalar
    // object via objSink).
    static class PositiveMessageFormatPattern extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.MessageFormat mf = new java.text.MessageFormat(ssrc());
            strSink(mf.toPattern());
        }
    }

    // FAILS as of this writing: java.text.MessageFormat#<init>(String) has a
    // `taintCopyOnly: true` config entry that copies arg(0) -> this (whole object) in
    // addition to the field-sensitive entry writing .MessageFormat#pattern#String -- the
    // whole-object twin was kept (same pattern as the BasicControl/DecimalFormatSymbols
    // whole-object twins documented in CoverageRuleStorageFixes.java) and lets the pattern
    // taint leak into getLocale() via AnyAccessorEnabled. Expected: no finding. Actual: a
    // finding is reported.
    static class NegativeMessageFormatLocaleNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.MessageFormat mf = new java.text.MessageFormat(ssrc());
            objSink(mf.getLocale());
        }
    }

    // 7. java.text.DecimalFormat: pattern (toPattern, scalar) vs symbols
    // (getDecimalFormatSymbols, scalar object via objSink).
    static class PositiveDecimalFormatPattern extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.DecimalFormat df = new java.text.DecimalFormat();
            df.applyPattern(ssrc());
            strSink(df.toPattern());
        }
    }

    // FAILS as of this writing: java.text.DecimalFormat#applyPattern(String) has a
    // `taintCopyOnly: true` config entry that copies arg(0) -> this (whole object) in
    // addition to the field-sensitive entries writing .DecimalFormat#pattern#String (and,
    // deliberately, .DecimalFormat#symbols#DecimalFormatSymbols#internationalCurrencySymbol
    // for locale-affecting pattern chars) -- the whole-object twin lets the pattern taint
    // leak into getDecimalFormatSymbols() via AnyAccessorEnabled. Expected: no finding.
    // Actual: a finding is reported.
    static class NegativeDecimalFormatSymbolsNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.DecimalFormat df = new java.text.DecimalFormat();
            df.applyPattern(ssrc());
            objSink(df.getDecimalFormatSymbols());
        }
    }

    // 8. javax.naming.directory.SearchResult: name (getName, inherited scalar String) vs
    // object (getObject, scalar Object via objSink).
    //
    // FAILS as of this writing (as a Positive -- the property never propagates at all):
    // the only config entry matching the exact SearchResult(String, Object, Attributes)
    // 3-arg constructor is a generic `params: index:0 type: String` rule that writes
    // arg(0) into `.javax.naming.directory.SearchResult#name#java.lang.String`. But
    // getName() is not overridden on SearchResult -- it resolves to the inherited
    // NameClassPair#getName(), whose config reads from the differently-keyed
    // `.javax.naming.NameClassPair#name#java.lang.Object` slot (see the sibling 4-/5-arg
    // constructor overloads, which correctly re-key arg(0) into that exact
    // NameClassPair-owned slot). The 3-arg constructor's write and getName()'s read target
    // two different vfields on the same object, so the write is orphaned. Expected: a
    // finding. Actual: no finding is reported -- the property does not propagate.
    static class PositiveSearchResultGetName extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.directory.SearchResult sr = new javax.naming.directory.SearchResult(
                ssrc(), new Object(), new javax.naming.directory.BasicAttributes());
            strSink(sr.getName());
        }
    }

    static class NegativeSearchResultObjectNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.directory.SearchResult sr = new javax.naming.directory.SearchResult(
                ssrc(), new Object(), new javax.naming.directory.BasicAttributes());
            objSink(sr.getObject());
        }
    }

    // 9. javax.naming.Binding: object (getObject, scalar Object via objSink) vs name
    // (getName, inherited scalar String).
    //
    // FAILS as of this writing (as a Positive): there is no passThrough config entry at
    // all for javax.naming.Binding#<init>(String, Object) (confirmed by grep across
    // model/java/config/stdlib/*.yaml) -- only Binding#setObject(Object) is modeled. The
    // constructor argument never reaches the object field, so getObject() observes no
    // taint even though Binding#setObject/#getObject are themselves correctly
    // field-sensitive. Expected: a finding. Actual: no finding is reported -- the
    // constructor path does not propagate.
    static class PositiveBindingGetObject extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.Binding b = new javax.naming.Binding("cleanName", ssrc());
            objSink(b.getObject());
        }
    }

    static class NegativeBindingNameNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            javax.naming.Binding b = new javax.naming.Binding("cleanName", ssrc());
            strSink(b.getName());
        }
    }
}

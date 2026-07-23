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

    // FIXED: javax.naming.ldap.SortKey#<init>(String, boolean, String)'s config entry used
    // to copy BOTH arg(0) (attributeId) and arg(2) (matchingRuleId) onto the field-sensitive
    // slots AND onto the whole "this" object in the same entry, and both
    // SortKey#getAttributeID and SortKey#getMatchingRuleID carried their own explicit
    // `from: this to: result` copy line -- so either property leaked into the other getter
    // unconditionally. The whole-object arms were removed from both the ctors and the
    // getters, leaving only the field-sensitive slots.
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

    // FIXED: javax.naming.ldap.Rdn#<init>(String, Object)'s config entries used to only
    // copy `arg(*) -> this` (whole object, no field split at all) -- there was no
    // field-sensitive write of arg(0)/arg(1) into .Rdn#type/.Rdn#value for this constructor
    // overload, so the whole-object mark set by the tainted type argument leaked into
    // getValue() (which does read the field-sensitive .Rdn#value slot, but AnyAccessorEnabled
    // also let the whole-object mark satisfy that read). The ctor now writes arg(0)/arg(1)
    // field-sensitively instead, and getType() (previously unmodelled entirely) now reads
    // .Rdn#type#String.
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

    // ACCEPTED LIMITATION (not a model bug -- do not "fix" by attempting a key-sensitive
    // attribute slot): javax.script.ScriptContext#setAttribute(String, Object, int) writes
    // into a single .ScriptContext#attribute#java.lang.Object vfield shared by every
    // attribute name. Attribute keys are runtime strings the analyzer cannot statically
    // distinguish, so setAttribute("k", tainted, scope) followed by getAttribute("other")
    // is observed as tainted even though "k" and "other" are different attributes. This is
    // the same accepted over-approximation as java.util.Map's MapValue slot, which
    // conflates all keys of a map for the same reason (see the design doc's routing of
    // keyed bags to a single HOLDER slot). It is SOUND (a real cross-key flow is never
    // dropped) but imprecise (this is a false positive for genuinely distinct keys).
    // javax.naming.ldap.ExtendedRequest has the same key-insensitivity shape but is
    // skipped above for an unrelated reason (no injectable concrete impl); no other case
    // in this file fails solely because of key-insensitivity.

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

    // FIXED: java.text.MessageFormat#<init>(String) (and its (String, Locale) and
    // #applyPattern(String) siblings) used to carry `arg(0) -> this` (whole object) twin
    // entries -- some `taintCopyOnly: true` -- beside the field-sensitive entry writing
    // .MessageFormat#pattern#String -- the whole-object twins let the pattern taint leak
    // into getLocale() via AnyAccessorEnabled. All whole-object arms were removed from the
    // MessageFormat ctors/applyPattern, leaving only the field-sensitive #pattern#/#locale#
    // writes.
    static class NegativeMessageFormatLocaleNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.MessageFormat mf = new java.text.MessageFormat(ssrc());
            objSink(mf.getLocale());
        }
    }

    // FN check for the fix above: MessageFormat#format() must still carry the pattern
    // taint into its output -- a tainted pattern reaching a formatted string is a real
    // injection flow, and removing the whole-object copy must not also remove this.
    static class PositiveMessageFormatFormatCarriesPattern extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.MessageFormat mf = new java.text.MessageFormat("clean {0}");
            mf.applyPattern(ssrc());
            strSink(mf.format(new Object[]{"x"}));
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

    // FIXED: java.text.DecimalFormat#applyPattern(String) (and its <init>(String) and
    // <init>(String, DecimalFormatSymbols) siblings) used to carry `arg(0) -> this` (whole
    // object) twin entries -- some `taintCopyOnly: true` -- beside the field-sensitive
    // entries writing .DecimalFormat#pattern#String (and, deliberately,
    // .DecimalFormat#symbols#DecimalFormatSymbols#internationalCurrencySymbol for
    // locale-affecting pattern chars) -- the whole-object twins let the pattern taint leak
    // into getDecimalFormatSymbols() via AnyAccessorEnabled. All whole-object arms were
    // removed, leaving only the field-sensitive #pattern#/#symbols# writes.
    static class NegativeDecimalFormatSymbolsNoLeak extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.DecimalFormat df = new java.text.DecimalFormat();
            df.applyPattern(ssrc());
            objSink(df.getDecimalFormatSymbols());
        }
    }

    // FN check for the fix above: DecimalFormat#format() must still carry the pattern
    // taint into its output -- a tainted pattern reaching a formatted string is a real
    // injection flow, and removing the whole-object copy must not also remove this.
    static class PositiveDecimalFormatFormatCarriesPattern extends CoverageBeanIsolation {
        @Override public void entrypoint() {
            java.text.DecimalFormat df = new java.text.DecimalFormat();
            df.applyPattern(ssrc());
            strSink(df.format(1L));
        }
    }

    // 8. javax.naming.directory.SearchResult: name (getName, inherited scalar String) vs
    // object (getObject, scalar Object via objSink).
    //
    // FIXED (was a Positive miss -- the property never propagated at all): the only config
    // entry that used to match the exact SearchResult(String, Object, Attributes) 3-arg
    // constructor was a generic `params: index:0 type: String` rule that wrote arg(0) into
    // `.javax.naming.directory.SearchResult#name#java.lang.String`. But getName() is not
    // overridden on SearchResult -- it resolves to the inherited NameClassPair#getName(),
    // whose config reads from the differently-keyed
    // `.javax.naming.NameClassPair#name#java.lang.Object` slot (see the sibling 4-/5-arg
    // constructor overloads, which correctly re-key arg(0) into that exact NameClassPair-
    // owned slot). The imprecise index-based matchers were replaced with exact per-
    // constructor entries writing name/obj/attrs into the slots their readers actually use.
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
    // FIXED (was a Positive miss): there used to be no passThrough config entry at all for
    // javax.naming.Binding#<init>(String, Object) (confirmed by grep across
    // model/java/config/stdlib/*.yaml) -- only Binding#setObject(Object) was modeled. The
    // constructor argument never reached the object field, so getObject() observed no
    // taint even though Binding#setObject/#getObject are themselves correctly
    // field-sensitive. All four real Binding constructor overloads now write name/className
    // /obj field-sensitively into the NameClassPair#name / NameClassPair#className /
    // Binding#object slots their readers already use.
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

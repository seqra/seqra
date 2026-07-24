package phase3;

import base.RuleSample;
import base.RuleSet;

// Verifies the mechanism the conductor response-source stars rely on: a STARRED
// source marks every field of an object, and a field-sensitive EXTERNAL getter
// (modeled as this.<slot> -> result) must then propagate that mark to a sink.
// javax.naming.NameClassPair#getName reads the .name# slot (a real builtin
// field-sensitive getter). ncpSrc() returns a NameClassPair whose #name# is a
// constant (clean) -- the taint comes only from the source rule marking $P.
@RuleSet("phase3/CoverageStarSourceGetter.yaml")
public abstract class CoverageStarSourceGetter implements RuleSample {
    public javax.naming.NameClassPair ncpSrc() {
        return new javax.naming.NameClassPair("n", "c");
    }

    public javax.naming.NameClassPair ncpSrcPlain() {
        return new javax.naming.NameClassPair("n", "c");
    }

    public void strSink(String s) {}

    // $*P marks every field of P (incl .name#); getName() reads .name#.
    // If a starred source reaches a field-sensitive getter, this reports.
    static class PositiveStarSourceReachesFieldGetter extends CoverageStarSourceGetter {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = ncpSrc();
            strSink(p.getName());
        }
    }

    // Non-starred source marks only P's base value; getName() reads the .name#
    // field, so a base-only mark must NOT reach it -- the control proving the
    // star (not just any source) is what carries taint into the field getter.
    static class NegativeBaseSourceMissesFieldGetter extends CoverageStarSourceGetter {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = ncpSrcPlain();
            strSink(p.getName());
        }
    }
}

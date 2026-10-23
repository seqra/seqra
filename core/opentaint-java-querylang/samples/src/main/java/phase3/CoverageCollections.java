package phase3;

import base.RuleSample;
import base.RuleSet;

import java.util.List;
import java.util.Set;

// Phase 3 core coverage: immutable-factory element passthroughs.
// Each Positive flows taint from a source, through List.of / Set.of, into the
// collection element, then out through an element read to a sink. A Positive
// turning red means the factory passthrough dropped the element taint.
@RuleSet("phase3/CoverageCollections.yaml")
public abstract class CoverageCollections implements RuleSample {
    public String ssrc() { return "tainted"; }
    public void strSink(String s) {}

    // java.util.List#of(Object) : arg0 -> result.Element
    static class PositiveListOf extends CoverageCollections {
        @Override public void entrypoint() {
            String t = ssrc();
            List<String> l = List.of(t);
            strSink(l.get(0));
        }
    }

    // java.util.Set#of(Object) : arg0 -> result.Element
    static class PositiveSetOf extends CoverageCollections {
        @Override public void entrypoint() {
            String t = ssrc();
            Set<String> s = Set.of(t);
            for (String v : s) {
                strSink(v);
            }
        }
    }

    // Negative: a clean local element must not be reported.
    static class NegativeCleanListOf extends CoverageCollections {
        @Override public void entrypoint() {
            String t = "safe";
            List<String> l = List.of(t);
            strSink(l.get(0));
        }
    }
}

package phase3;

import base.RuleSample;
import base.RuleSet;

// javax.naming.directory (java.naming JDK module) passthrough coverage. Each
// Positive flows taint from a source, through a SearchControls config passthrough,
// and back out to a sink. A Positive turning red means the config change dropped a
// real flow.
@RuleSet("phase3/CoverageNamingDirectory.yaml")
public abstract class CoverageNamingDirectory implements RuleSample {
    public String[] asrc() { return new String[]{"tainted"}; }
    public void arrSink(String[] s) {}
    public String ssrc() { return "tainted"; }
    public void strSink(String s) {}

    // SearchControls#setReturningAttributes(String[]) : arg0 -> this.returningAttributes,
    // read back via getReturningAttributes() : this.returningAttributes -> result.
    static class PositiveSearchControlsSetter extends CoverageNamingDirectory {
        @Override public void entrypoint() {
            String[] a = asrc();
            javax.naming.directory.SearchControls sc = new javax.naming.directory.SearchControls();
            sc.setReturningAttributes(a);
            arrSink(sc.getReturningAttributes());
        }
    }

    // SearchControls#<init>(int,long,int,String[],boolean,boolean) : arg3 -> this.returningAttributes.
    static class PositiveSearchControlsCtor extends CoverageNamingDirectory {
        @Override public void entrypoint() {
            javax.naming.directory.SearchControls sc =
                new javax.naming.directory.SearchControls(0, 0L, 0, asrc(), false, false);
            arrSink(sc.getReturningAttributes());
        }
    }

    // Negative: a clean local array must not be reported.
    static class NegativeCleanSearchControls extends CoverageNamingDirectory {
        @Override public void entrypoint() {
            String[] a = new String[]{"safe"};
            javax.naming.directory.SearchControls sc = new javax.naming.directory.SearchControls();
            sc.setReturningAttributes(a);
            arrSink(sc.getReturningAttributes());
        }
    }

    // NameClassPair: setName must reach getName and must NOT reach getClassName.
    static class PositiveNamePropertyRoundTrip extends CoverageNamingDirectory {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = new javax.naming.NameClassPair("a", "b");
            p.setName(ssrc());
            strSink(p.getName());
        }
    }

    static class NegativeNameDoesNotLeakToClassName extends CoverageNamingDirectory {
        @Override public void entrypoint() {
            javax.naming.NameClassPair p = new javax.naming.NameClassPair("a", "b");
            p.setName(ssrc());
            strSink(p.getClassName());
        }
    }
}

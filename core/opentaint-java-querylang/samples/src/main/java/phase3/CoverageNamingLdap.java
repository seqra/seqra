package phase3;

import base.RuleSample;
import base.RuleSet;

// javax.naming.ldap (java.naming JDK module) passthrough coverage. The Control-family
// ctors copy the tainted arg -> this (whole-object). We sink the constructed control
// object directly (ctrlSink), which observes that whole-object taint -- no read-back
// getter is needed (getEncodedValue is not modeled and its clone-based body does not
// propagate the field in this harness).
// ExtendedRequest#createExtendedResponse is UNTESTABLE (ExtendedRequest is an interface;
// its concrete impl StartTlsRequest has an inert reflective createExtendedResponse body).
@RuleSet("phase3/CoverageNamingLdap.yaml")
public abstract class CoverageNamingLdap implements RuleSample {
    public String[] asrc() { return new String[]{"tainted"}; }
    public byte[] bsrc() { return new byte[]{1}; }
    public void ctrlSink(Object c) {}

    // SortControl#<init>(String[], boolean) : arg0 -> this.
    static class PositiveSortControl extends CoverageNamingLdap {
        @Override public void entrypoint() {
            String[] a = asrc();
            try {
                javax.naming.ldap.SortControl c = new javax.naming.ldap.SortControl(a, true);
                ctrlSink(c);
            } catch (java.io.IOException e) {
            }
        }
    }

    // SortResponseControl#<init>(String, boolean, byte[]) : arg2 -> this.
    static class PositiveSortResponseControl extends CoverageNamingLdap {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            try {
                javax.naming.ldap.SortResponseControl c =
                    new javax.naming.ldap.SortResponseControl("1.2.840.113556.1.4.474", false, b);
                ctrlSink(c);
            } catch (java.io.IOException e) {
            }
        }
    }

    // BasicControl#<init>(String, boolean, byte[]) : arg2 -> this.
    static class PositiveBasicControl extends CoverageNamingLdap {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            javax.naming.ldap.BasicControl c =
                new javax.naming.ldap.BasicControl("1.2", false, b);
            ctrlSink(c);
        }
    }

    // PagedResultsResponseControl#<init>(String, boolean, byte[]) : arg2 -> this.
    static class PositivePagedResultsResponseControl extends CoverageNamingLdap {
        @Override public void entrypoint() {
            byte[] b = bsrc();
            try {
                javax.naming.ldap.PagedResultsResponseControl c =
                    new javax.naming.ldap.PagedResultsResponseControl("1.2.840.113556.1.4.319", false, b);
                ctrlSink(c);
            } catch (java.io.IOException e) {
            }
        }
    }

    // Negative: a clean local byte[] must not be reported.
    static class NegativeCleanBasicControl extends CoverageNamingLdap {
        @Override public void entrypoint() {
            byte[] b = new byte[]{0};
            javax.naming.ldap.BasicControl c =
                new javax.naming.ldap.BasicControl("1.2", false, b);
            ctrlSink(c);
        }
    }
}

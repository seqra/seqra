package phase3;

import base.RuleSample;
import base.RuleSet;

// javax.sql.rowset (java.sql.rowset JDK module) passthrough coverage. JoinRowSet is
// an interface, but the config passthrough is keyed on the interface, so calling
// through the interface type (obtained from RowSetProvider) matches it directly:
// addRowSet copies arg0 -> this, and getRowSets copies this -> result.
@RuleSet("phase3/CoverageSql.yaml")
public abstract class CoverageSql implements RuleSample {
    public javax.sql.RowSet rsrc() { return null; }
    public void objSink(Object o) {}

    // JoinRowSet#addRowSet(RowSet, String) : arg0 -> this, read back via getRowSets().
    static class PositiveJoinRowSetAddRowSet extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(r, "col");
                objSink(j.getRowSets());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    // Negative: a clean local RowSet must not be reported.
    static class NegativeCleanJoinRowSet extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = null;
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(r, "col");
                objSink(j.getRowSets());
            } catch (java.sql.SQLException e) {
            }
        }
    }
}

package phase3;

import base.RuleSample;
import base.RuleSet;

// JoinRowSet stores each added row set at rowSets[*]. getRowSets exposes those
// elements through the returned Collection; the sink reads an actual element.
@RuleSet("phase3/CoverageSql.yaml")
public abstract class CoverageSql implements RuleSample {
    public javax.sql.RowSet rsrc() { return null; }
    public void objSink(Object o) {}

    // Read back the scalar RowSet added with a named match column.
    static class PositiveJoinRowSetAddRowSet extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(r, "col");
                objSink(j.getRowSets().iterator().next());
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
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class PositiveJoinRowSetIndex extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(r, 1);
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class NegativeCleanJoinRowSetIndex extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = null;
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(r, 1);
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class PositiveJoinRowSetJoinable extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet((javax.sql.rowset.Joinable) r);
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class NegativeCleanJoinRowSetJoinable extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = null;
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet((javax.sql.rowset.Joinable) r);
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class PositiveJoinRowSetArrayNames extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(new javax.sql.RowSet[]{r}, new String[]{"col"});
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class NegativeCleanJoinRowSetArrayNames extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = null;
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(new javax.sql.RowSet[]{r}, new String[]{"col"});
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class PositiveJoinRowSetArrayIndexes extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = rsrc();
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(new javax.sql.RowSet[]{r}, new int[]{1});
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }

    static class NegativeCleanJoinRowSetArrayIndexes extends CoverageSql {
        @Override public void entrypoint() {
            javax.sql.RowSet r = null;
            try {
                javax.sql.rowset.JoinRowSet j =
                    javax.sql.rowset.RowSetProvider.newFactory().createJoinRowSet();
                j.addRowSet(new javax.sql.RowSet[]{r}, new int[]{1});
                objSink(j.getRowSets().iterator().next());
            } catch (java.sql.SQLException e) {
            }
        }
    }
}

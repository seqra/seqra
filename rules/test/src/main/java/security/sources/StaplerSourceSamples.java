package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Stapler/Jenkins sources.
 */
public class StaplerSourceSamples {

    private DataSource dataSource;

    public StaplerSourceSamples() {
        this.dataSource = null;
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void staplerRequestGetParameter(StaplerRequest req) throws Exception {
        String param = req.getParameter("name");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + param + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void staplerRequestBindJSON(StaplerRequest req) throws Exception {
        Object obj = req.bindJSON(Object.class, req.getSubmittedForm());
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + obj + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void queryParameterAnnotation(@QueryParameter String name) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    @JavaScriptMethod
    public String javaScriptMethod(String input) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + input + "'");
        }
        return "";
    }

    private String value;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    @DataBoundSetter
    public void setValue(String value) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + value + "'");
        } catch (Exception e) {
            // ignore
        }
    }

    /** Descriptor callback: configure */
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public boolean configure(StaplerRequest req, JSONObject json) throws Exception {
        String val = req.getParameter("key");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + val + "'");
        }
        return true;
    }

    /** @DataBoundConstructor — tested in inner class */
    public static class Config {
        private DataSource dataSource;

        @DataBoundConstructor
        public Config(String name) {
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
            } catch (Exception e) {
                // ignore
            }
        }
    }
}

package security.sources;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for java.lang.System environment sources (getProperty, getProperties).
 */
public class SystemPropertySourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void systemGetProperty() throws Exception {
        String val = System.getProperty("user.input");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + val + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void systemGetPropertyWithDefault() throws Exception {
        String val = System.getProperty("user.input", "default");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + val + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void systemGetProperties() throws Exception {
        Properties props = System.getProperties();
        String val = props.getProperty("user.input");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + val + "'");
        }
    }
}

package security.sources;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import hudson.FilePath;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for hudson.FilePath file sources.
 */
public class HudsonFilePathSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filePathReadToString(FilePath fp) throws Exception {
        String content = fp.readToString();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filePathRead(FilePath fp) throws Exception {
        InputStream is = fp.read();
        byte[] bytes = is.readAllBytes();
        String content = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filePathReadFromOffset(FilePath fp) throws Exception {
        InputStream is = fp.readFromOffset(0);
        byte[] bytes = is.readAllBytes();
        String content = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filePathStaticOpenInputStream() throws Exception {
        InputStream is = FilePath.openInputStream(new File("/tmp/data"), new java.nio.file.OpenOption[0]);
        byte[] bytes = is.readAllBytes();
        String content = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filePathStaticNewInputStream() throws Exception {
        InputStream is = FilePath.newInputStreamDenyingSymlinkAsNeeded(new File("/tmp/data"), "/tmp", new java.nio.file.OpenOption[0]);
        byte[] bytes = is.readAllBytes();
        String content = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }
}

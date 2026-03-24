package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemStream;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for FileItem and FileItemStream methods.
 */
public class FileUploadSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void fileItemGetString(FileItem item) throws Exception {
        String content = item.getString();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + content + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void fileItemStreamGetName(FileItemStream stream) throws Exception {
        String name = stream.getName();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
    }
}

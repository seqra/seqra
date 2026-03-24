package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for FTPClient methods.
 */
public class FtpSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void ftpClientListNames(FTPClient ftp) throws Exception {
        String[] names = ftp.listNames();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + names[0] + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void ftpClientListFiles(FTPClient ftp) throws Exception {
        FTPFile[] files = ftp.listFiles();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + files[0].getName() + "'");
        }
    }
}

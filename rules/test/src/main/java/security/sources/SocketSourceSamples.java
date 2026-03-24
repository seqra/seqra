package security.sources;

import java.io.InputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for java.net.Socket and java.net.http.WebSocket.
 */
public class SocketSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void socketGetInputStream(Socket socket) throws Exception {
        InputStream is = socket.getInputStream();
        byte[] bytes = is.readAllBytes();
        String str = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }
}

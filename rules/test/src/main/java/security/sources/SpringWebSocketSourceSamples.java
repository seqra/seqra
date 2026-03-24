package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring WebSocket handlers.
 */
public class SpringWebSocketSourceSamples extends AbstractWebSocketHandler {

    private DataSource dataSource;

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + payload + "'");
        }
    }

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + payload + "'");
        }
    }
}

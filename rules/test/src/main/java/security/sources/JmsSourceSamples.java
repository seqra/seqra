package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.jms.JMSConsumer;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.QueueRequestor;
import javax.jms.TopicRequestor;
import javax.sql.DataSource;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for JMS consumer methods.
 */
public class JmsSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void jmsConsumerReceive(JMSConsumer consumer) throws Exception {
        Message msg = consumer.receive();
        String body = msg.getBody(String.class);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + body + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void messageConsumerReceive(MessageConsumer consumer) throws Exception {
        Message msg = consumer.receive();
        String body = msg.getBody(String.class);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + body + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void queueRequestorRequest(QueueRequestor requestor, Message request) throws Exception {
        Message response = requestor.request(request);
        String body = response.getBody(String.class);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + body + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void topicRequestorRequest(TopicRequestor requestor, Message request) throws Exception {
        Message response = requestor.request(request);
        String body = response.getBody(String.class);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + body + "'");
        }
    }
}

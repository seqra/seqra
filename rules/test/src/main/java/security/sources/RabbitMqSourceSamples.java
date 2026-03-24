package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.StringRpcServer;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for RabbitMQ methods.
 */
public class RabbitMqSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void commandGetContentBody(Command cmd) throws Exception {
        byte[] body = cmd.getContentBody();
        String str = new String(body);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void rpcClientStringCall(RpcClient rpc) throws Exception {
        String result = rpc.stringCall("request");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + result + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws Exception {
        String str = new String(body);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void frameGetPayload(Frame frame) throws Exception {
        byte[] payload = frame.getPayload();
        String str = new String(payload);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void frameHandlerReadFrame(FrameHandler fh) throws Exception {
        Frame frame = fh.readFrame();
        String str = new String(frame.getPayload());
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    /** RpcServer callback - handleCall with byte[] param */
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public byte[] handleCall(byte[] requestBody, AMQP.BasicProperties replyProperties) {
        String str = new String(requestBody);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        } catch (Exception e) {
            // ignore
        }
        return new byte[0];
    }

    /** StringRpcServer callback */
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public String handleStringCall(String requestBody, AMQP.BasicProperties replyProperties) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + requestBody + "'");
        } catch (Exception e) {
            // ignore
        }
        return "";
    }
}

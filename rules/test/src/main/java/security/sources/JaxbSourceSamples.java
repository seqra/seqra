package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.xml.bind.attachment.AttachmentUnmarshaller;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for JAXB AttachmentUnmarshaller methods.
 */
public class JaxbSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void attachmentUnmarshallerByteArray(AttachmentUnmarshaller unmarshaller) throws Exception {
        byte[] data = unmarshaller.getAttachmentAsByteArray("cid:123");
        String str = new String(data);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }
}

package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.xmlpull.v1.XmlPullParser;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for XmlPullParser.
 */
public class XmlPullSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void xmlPullParserGetText(XmlPullParser parser) throws Exception {
        String text = parser.getText();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + text + "'");
        }
    }
}

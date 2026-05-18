package test;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Minimal repros for taint-propagation gaps in built-in approximations.
 *
 * Each test pipes a tainted request parameter through a SINGLE library
 * helper that has no built-in passThrough model. Without the propagator
 * the analyzer kills the dataflow fact at the helper call and the rule
 * cannot reach its sink; once the passThrough rule is added to
 * {@code core/opentaint-config/config/config/stdlib.yaml}, each sample
 * flips from {@code falseNegative} to {@code success}.
 *
 * Pair each repro with a corresponding entry in stdlib.yaml:
 *   org.apache.commons.lang3.StringUtils#defaultIfBlank  -> arg(0)->result, arg(1)->result
 *   org.apache.commons.io.IOUtils#toString               -> arg(0)->result
 *   org.apache.commons.codec.binary.Base64#encodeBase64String -> arg(0)->result
 */
public class AnalyzerPropagatorRepros {

    /**
     * SQL injection where the tainted parameter is normalized via Apache Commons
     * Lang {@code StringUtils.defaultIfBlank(...)} before reaching the JDBC sink.
     */
    @WebServlet("/repro/stringutils-defaultIfBlank")
    public static class StringUtilsDefaultIfBlankServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String name = request.getParameter("name");
            String normalized = StringUtils.defaultIfBlank(name, "guest").toString();
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:h2:mem:test");
                 java.sql.Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM users WHERE name = '" + normalized + "'");
            } catch (java.sql.SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * SQL injection where the tainted input is round-tripped through Apache
     * Commons IO {@code IOUtils.toString(InputStream, Charset)} before reaching
     * the JDBC sink.
     */
    @WebServlet("/repro/ioutils-toString")
    public static class IOUtilsToStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            java.io.InputStream in = request.getInputStream();
            String body = IOUtils.toString(in, java.nio.charset.StandardCharsets.UTF_8);
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:h2:mem:test");
                 java.sql.Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE body = '" + body + "'");
            } catch (java.sql.SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * SQL injection where the tainted parameter is Base64-encoded via Apache
     * Commons Codec {@code Base64.encodeBase64String(byte[])} before reaching
     * the JDBC sink.
     */
    // Note: a minimal Files.writeString repro lives in
    // PathTraversalNioSinksSamples$UnsafeWriteStringServlet. It exhibits a
    // real analyzer FN: with the path-traversal-in-servlet-app rule,
    // Files.write fires but Files.writeString (and Files.readString) do not,
    // even when path-traversal-sinks.yaml is reduced to just the three Files
    // patterns. The synthetic equivalents in
    // core/opentaint-java-querylang/src/test (AnalyzerBugsTest +
    // analyzerbugs.FilesWriteStringBug / FilesWriteStringJoinBug) all pass,
    // so the bug only manifests against an `opentaint compile`-built project
    // model — the trigger is elsewhere in the pipeline (not the pattern
    // matcher in isolation).

    @WebServlet("/repro/base64-encode")
    public static class Base64EncodeServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String raw = request.getParameter("token");
            String encoded = Base64.encodeBase64String(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:h2:mem:test");
                 java.sql.Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM tokens WHERE t = '" + encoded + "'");
            } catch (java.sql.SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}

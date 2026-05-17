package security.barriers;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;

/**
 * Negative-only test bank for CodeQL-aligned barriers (sanitizers,
 * pattern-not, and pattern-not-inside) we have added to the OpenTaint
 * built-in rules. Each sample exercises a single barrier so a regression
 * shows up as exactly one new false positive.
 *
 * Inventory of CodeQL classes mapped here:
 *   PathSanitizer.qll          – path-traversal barriers
 *   CommandArguments.qll       – command-injection safe argument barriers
 *   RequestForgery.qll         – ssrf / unvalidated-redirect host barriers
 *   LdapInjection.qll          – ldap-injection encoder barriers
 *   LogInjection.qll           – log-injection CRLF barriers
 *   XSS.qll                    – xss encoder barriers
 */
public class BarrierTests {

    private static javax.sql.DataSource dataSource;

    // ── path-traversal ────────────────────────────────────────────────────

    /** PathSanitizer.PathNormalizeSanitizer — Path.normalize(). */
    @WebServlet("/barrier/path-normalize")
    public static class SafePathNormalizeServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path normalized = Paths.get("/var/data/" + fileName).normalize();
            Files.readAllBytes(normalized);
        }
    }

    /** PathSanitizer.PathNormalizeSanitizer — File.getCanonicalFile(). */
    @WebServlet("/barrier/path-canonicalFile")
    public static class SafeCanonicalFileServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File canonical = new File("/var/data/" + fileName).getCanonicalFile();
            try (java.io.InputStream is = new java.io.FileInputStream(canonical)) {
                is.read();
            }
        }
    }

    /** PathSanitizer.PathNormalizeSanitizer — File.getCanonicalPath(). */
    @WebServlet("/barrier/path-canonicalPath")
    public static class SafeCanonicalPathServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            String canonical = new File("/var/data/" + fileName).getCanonicalPath();
            Files.readAllBytes(Paths.get(canonical));
        }
    }

    /** PathSanitizer.PathNormalizeSanitizer — FilenameUtils.normalize. */
    @WebServlet("/barrier/path-filenameutils-normalize")
    public static class SafeFilenameUtilsNormalizeServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            String normalized = org.apache.commons.io.FilenameUtils.normalize(fileName);
            if (normalized == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            Files.readAllBytes(Paths.get("/var/data/", normalized));
        }
    }

    /** PathSanitizer.PathNormalizeSanitizer — FilenameUtils.normalizeNoEndSeparator. */
    @WebServlet("/barrier/path-filenameutils-normalize-noend")
    public static class SafeFilenameUtilsNormalizeNoEndSeparatorServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            String normalized = org.apache.commons.io.FilenameUtils.normalizeNoEndSeparator(fileName);
            if (normalized == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            Files.readAllBytes(Paths.get("/var/data/", normalized));
        }
    }

    /** PathSanitizer — pixee Filenames.toSimpleFileName. */
    @WebServlet("/barrier/path-pixee")
    public static class SafePixeeFilenameServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            String simple = io.github.pixee.security.Filenames.toSimpleFileName(fileName);
            Files.readAllBytes(Paths.get("/var/data/", simple));
        }
    }

    // ── command-injection ─────────────────────────────────────────────────

    /** CommandLineQuery — pixee SafeCommand wrappers. */
    @WebServlet("/barrier/cmd-pixee-runcommand")
    public static class SafePixeeRunCommandServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String cmd = request.getParameter("cmd");
            io.github.pixee.security.SystemCommand.runCommand(Runtime.getRuntime(), new String[] {"/bin/sh", "-c", cmd});
        }
    }

    // ── ssrf ───────────────────────────────────────────────────────────────

    /** RequestForgery — java.net.URLEncoder.encode(String). */
    @WebServlet("/barrier/ssrf-urlencoder-1arg")
    public static class SafeUrlEncoder1ArgServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String key = request.getParameter("key");
            @SuppressWarnings("deprecation")
            String encoded = java.net.URLEncoder.encode(key);
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
                org.apache.http.client.methods.HttpGet httpget =
                        new org.apache.http.client.methods.HttpGet("https://example.com/getId?key=" + encoded);
                httpClient.execute(httpget);
            }
        }
    }

    /** RequestForgery — java.net.URLEncoder.encode(String, String). */
    @WebServlet("/barrier/ssrf-urlencoder-2arg")
    public static class SafeUrlEncoder2ArgServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String key = request.getParameter("key");
            String encoded = java.net.URLEncoder.encode(key, "UTF-8");
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
                org.apache.http.client.methods.HttpGet httpget =
                        new org.apache.http.client.methods.HttpGet("https://example.com/getId?key=" + encoded);
                httpClient.execute(httpget);
            }
        }
    }

    /** RequestForgery — Guava UrlEscapers.urlPathSegmentEscaper. */
    @WebServlet("/barrier/ssrf-guava-pathsegment")
    public static class SafeGuavaPathSegmentEscaperServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String key = request.getParameter("key");
            String encoded = com.google.common.net.UrlEscapers.urlPathSegmentEscaper().escape(key);
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
                org.apache.http.client.methods.HttpGet httpget =
                        new org.apache.http.client.methods.HttpGet("https://example.com/getId?key=" + encoded);
                httpClient.execute(httpget);
            }
        }
    }

    /** RequestForgery — Guava UrlEscapers.urlFormParameterEscaper. */
    @WebServlet("/barrier/ssrf-guava-formparam")
    public static class SafeGuavaFormParameterEscaperServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String key = request.getParameter("key");
            String encoded = com.google.common.net.UrlEscapers.urlFormParameterEscaper().escape(key);
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
                org.apache.http.client.methods.HttpGet httpget =
                        new org.apache.http.client.methods.HttpGet("https://example.com/getId?key=" + encoded);
                httpClient.execute(httpget);
            }
        }
    }

    /** RequestForgery — Guava UrlEscapers.urlFragmentEscaper. */
    @WebServlet("/barrier/ssrf-guava-fragment")
    public static class SafeGuavaFragmentEscaperServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String key = request.getParameter("key");
            String encoded = com.google.common.net.UrlEscapers.urlFragmentEscaper().escape(key);
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
                org.apache.http.client.methods.HttpGet httpget =
                        new org.apache.http.client.methods.HttpGet("https://example.com/getId?key=" + encoded);
                httpClient.execute(httpget);
            }
        }
    }

    // ── ssrf (via the main java-ssrf-sink) ───────────────────────────────

    /** RequestForgery — URLEncoder.encode(String) before passing to URL ctor. */
    @WebServlet("/barrier/ssrf-main-urlencoder")
    public static class SafeMainUrlEncoderServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "ssrf")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            String encoded = java.net.URLEncoder.encode(target, "UTF-8");
            java.net.URL url = new java.net.URL("https://api.example.com/get?id=" + encoded);
            url.openConnection().connect();
        }
    }

    /** RequestForgery — Guava urlFormParameterEscaper before URL ctor. */
    @WebServlet("/barrier/ssrf-main-guava-form")
    public static class SafeMainGuavaFormServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "ssrf")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            String encoded = com.google.common.net.UrlEscapers.urlFormParameterEscaper().escape(target);
            java.net.URL url = new java.net.URL("https://api.example.com/get?id=" + encoded);
            url.openConnection().connect();
        }
    }

    // ── ldap-injection ─────────────────────────────────────────────────────

    /** LdapInjection — Spring LdapEncoder.filterEncode. */
    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection")
    public void safeLdapFilterEncode(HttpServletRequest request) throws Exception {
        String username = request.getParameter("username");
        String encoded = org.springframework.ldap.support.LdapEncoder.filterEncode(username);
        String filter = "(uid=" + encoded + ")";
        javax.naming.directory.SearchControls ctls = new javax.naming.directory.SearchControls();
        javax.naming.directory.DirContext ctx = null;
        if (ctx != null) ctx.search("dc=example,dc=com", filter, ctls);
    }

    /** LdapInjection — Spring LdapEncoder.nameEncode. */
    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection")
    public void safeLdapNameEncode(HttpServletRequest request) throws Exception {
        String dn = request.getParameter("dn");
        String encoded = org.springframework.ldap.support.LdapEncoder.nameEncode(dn);
        javax.naming.directory.SearchControls ctls = new javax.naming.directory.SearchControls();
        javax.naming.directory.DirContext ctx = null;
        if (ctx != null) ctx.search(encoded, "(objectClass=*)", ctls);
    }

    // ── log-injection ──────────────────────────────────────────────────────

    /** LogInjection — Apache Commons Text StringEscapeUtils.escapeJava. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogEscapeJava(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = org.apache.commons.text.StringEscapeUtils.escapeJava(input);
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LogInjection — Apache Commons Lang3 StringEscapeUtils.escapeJava. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogEscapeJavaLang3(HttpServletRequest request) {
        String input = request.getParameter("input");
        @SuppressWarnings("deprecation")
        String safe = org.apache.commons.lang3.StringEscapeUtils.escapeJava(input);
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LogInjection — OWASP Encode.forJavaScript. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogEncodeForJavaScript(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = org.owasp.encoder.Encode.forJavaScript(input);
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    // ANALYZER LIMITATION: LineBreaksLogInjectionSanitizer (String.replace[All]
    // with CR/LF target) is a real CodeQL barrier but cannot be encoded as a
    // pattern-sanitizer in OpenTaint today — the matcher does not honour
    // typed-receiver instance-method patterns with literal-string arguments.
    // Restore these negative samples once the matcher supports that shape.

    // ── xss ────────────────────────────────────────────────────────────────

    /** XSS — Spring HtmlUtils.htmlEscape(String). */
    @WebServlet("/barrier/xss-htmlescape-1arg")
    public static class SafeHtmlEscape1ArgServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscape(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>Hello, " + safe + "!</h1>");
        }
    }

    /** XSS — OWASP Encode.forHtml(String). */
    @WebServlet("/barrier/xss-owasp-encode-forhtml")
    public static class SafeOwaspEncodeForHtmlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forHtml(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>Hello, " + safe + "!</h1>");
        }
    }

    /** XSS — Apache Commons Text escapeHtml4. */
    @WebServlet("/barrier/xss-commons-text-escapeHtml4")
    public static class SafeApacheEscapeHtml4Servlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>Hello, " + safe + "!</h1>");
        }
    }
}

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

    /** CodeQL barrierModel java.io.File.getName() — basename strips directory traversal. */
    @WebServlet("/barrier/path-file-getName")
    public static class SafeFileGetNameServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            String basename = new File(fileName).getName();
            Files.readAllBytes(Paths.get("/var/data/", basename));
        }
    }

    /** ESAPI Validator.getValidFileName — throws on invalid filename. */
    @WebServlet("/barrier/path-esapi-fileName")
    public static class SafeEsapiFileNameServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            try {
                String safe = org.owasp.esapi.ESAPI.validator().getValidFileName(
                        "filename", fileName, java.util.Arrays.asList(".txt", ".log"), false);
                Files.readAllBytes(Paths.get("/var/data/", safe));
            } catch (org.owasp.esapi.errors.ValidationException e) {
                throw new ServletException(e);
            }
        }
    }

    /** ESAPI Validator.getValidDirectoryPath — sanitised path under a parent. */
    @WebServlet("/barrier/path-esapi-directoryPath")
    public static class SafeEsapiDirectoryPathServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dir = request.getParameter("dir");
            try {
                String safe = org.owasp.esapi.ESAPI.validator().getValidDirectoryPath(
                        "dir", dir, new File("/var/data"), false);
                Files.readAllBytes(Paths.get(safe, "file.txt"));
            } catch (org.owasp.esapi.errors.ValidationException e) {
                throw new ServletException(e);
            }
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

    // ── unsafe-deserialization ────────────────────────────────────────────

    /** UnsafeDeserialization — pixee ValidatingObjectInputStreams.from. */
    @WebServlet("/barrier/deser-pixee-validating")
    public static class SafePixeeValidatingOisServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try (java.io.InputStream raw = request.getInputStream();
                 java.io.ObjectInputStream ois = io.github.pixee.security.ValidatingObjectInputStreams.from(raw)) {
                ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new ServletException(e);
            }
        }
    }

    /** UnsafeDeserialization — Apache Commons IO ValidatingObjectInputStream wrap. */
    @WebServlet("/barrier/deser-validating-ois")
    public static class SafeValidatingOisServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try (java.io.InputStream in = request.getInputStream();
                 org.apache.commons.io.serialization.ValidatingObjectInputStream ois =
                         new org.apache.commons.io.serialization.ValidatingObjectInputStream(in)) {
                ois.accept(String.class, Integer.class);
                Object result = ois.readObject();
                response.getWriter().write(String.valueOf(result));
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }


    // ── unsafe-reflection ─────────────────────────────────────────────────

    /** UnsafeReflection — pixee Reflection.loadAndVerify allow-list. */
    @WebServlet("/barrier/refl-pixee-loadAndVerify")
    public static class SafePixeeLoadAndVerifyServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "unsafe-reflection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String className = request.getParameter("class");
            try {
                Class<?> cls = io.github.pixee.security.Reflection.loadAndVerify(className);
                cls.getName();
            } catch (ClassNotFoundException e) {
                throw new ServletException(e);
            }
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

    /** RequestForgery — pixee Urls.create checks protocol + host policy. */
    @WebServlet("/barrier/ssrf-main-pixee-urls")
    public static class SafeMainPixeeUrlsServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "ssrf")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            java.net.URL url = io.github.pixee.security.Urls.create(
                    target,
                    io.github.pixee.security.Urls.HTTP_PROTOCOLS,
                    io.github.pixee.security.HostValidator.ALLOW_ALL);
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

    /** LineBreaksLogInjectionSanitizer — replaceAll("[\r\n]", _) assigned to var. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogStripCrLfBracket(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = input.replaceAll("[\\r\\n]", "_");
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LogInjection — pixee Newlines.stripAll. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogPixeeNewlines(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = io.github.pixee.security.Newlines.stripAll(input);
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LineBreaksLogInjectionSanitizer — replaceAll("\\R", _) assigned to var. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogStripCrLfAnyLineBreak(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = input.replaceAll("\\R", "_");
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LineBreaksLogInjectionSanitizer — replace("\n", _) assigned to var. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogReplaceNewline(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = input.replace("\n", "_");
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

    /** LineBreaksLogInjectionSanitizer — replace("\r", _) assigned to var. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogReplaceCarriageReturn(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = input.replace("\r", "_");
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
        logger.info("Got input: {}", safe);
    }

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

    /** XSS — Apache Commons Text escapeHtml3. */
    @WebServlet("/barrier/xss-commons-text-escapeHtml3")
    public static class SafeApacheEscapeHtml3Servlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml3(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — OWASP Encode.forHtmlContent. */
    @WebServlet("/barrier/xss-owasp-forHtmlContent")
    public static class SafeOwaspForHtmlContentServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forHtmlContent(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — OWASP Encode.forHtmlAttribute. */
    @WebServlet("/barrier/xss-owasp-forHtmlAttribute")
    public static class SafeOwaspForHtmlAttributeServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forHtmlAttribute(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<input value=\"" + safe + "\">");
        }
    }

    /** XSS — Spring HtmlUtils.htmlEscape (2-arg with encoding). */
    @WebServlet("/barrier/xss-htmlescape-2arg")
    public static class SafeHtmlEscape2ArgServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscape(name, "UTF-8");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>Hello, " + safe + "!</h1>");
        }
    }

    /** XSS — Spring HtmlUtils.htmlEscapeDecimal. */
    @WebServlet("/barrier/xss-htmlescape-decimal")
    public static class SafeHtmlEscapeDecimalServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscapeDecimal(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — Spring HtmlUtils.htmlEscapeHex. */
    @WebServlet("/barrier/xss-htmlescape-hex")
    public static class SafeHtmlEscapeHexServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscapeHex(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — OWASP Encode.forCDATA. */
    @WebServlet("/barrier/xss-owasp-forCDATA")
    public static class SafeOwaspForCDATAServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forCDATA(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<![CDATA[" + safe + "]]>");
        }
    }

    /** XSS — OWASP Encode.forJavaScript. */
    @WebServlet("/barrier/xss-owasp-forJavaScript")
    public static class SafeOwaspForJavaScriptServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forJavaScript(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<script>var n = '" + safe + "';</script>");
        }
    }

    /** XSS — OWASP Encode.forJavaScriptAttribute. */
    @WebServlet("/barrier/xss-owasp-forJsAttr")
    public static class SafeOwaspForJsAttrServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forJavaScriptAttribute(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<a href=\"javascript:foo('" + safe + "');\">go</a>");
        }
    }

    /** XSS — OWASP Encode.forCssString. */
    @WebServlet("/barrier/xss-owasp-forCssString")
    public static class SafeOwaspForCssStringServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forCssString(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<style>.x { content: '" + safe + "'; }</style>");
        }
    }

    /** XSS — Apache Commons Text escapeXml10. */
    @WebServlet("/barrier/xss-commons-escapeXml10")
    public static class SafeCommonsEscapeXml10Servlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeXml10(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — Apache Commons Text escapeEcmaScript. */
    @WebServlet("/barrier/xss-commons-escapeEcmaScript")
    public static class SafeCommonsEscapeEcmaScriptServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<script>var n = \"" + safe + "\";</script>");
        }
    }

    /** XSS — OWASP ESAPI encodeForJavaScript. */
    @WebServlet("/barrier/xss-esapi-forJavaScript")
    public static class SafeEsapiForJsServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForJavaScript(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<script>var n = '" + safe + "';</script>");
        }
    }

    /** XSS — OWASP ESAPI encodeForHTMLAttribute. */
    @WebServlet("/barrier/xss-esapi-forHtmlAttribute")
    public static class SafeEsapiForHtmlAttrServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForHTMLAttribute(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<input value=\"" + safe + "\">");
        }
    }

    /** XSS — OWASP ESAPI encodeForCSS. */
    @WebServlet("/barrier/xss-esapi-forCss")
    public static class SafeEsapiForCssServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForCSS(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<style>.x { content: '" + safe + "'; }</style>");
        }
    }

    /** XSS — OWASP ESAPI Validator.getValidSafeHTML returns AntiSamy-cleaned HTML. */
    @WebServlet("/barrier/xss-esapi-safeHtml")
    public static class SafeEsapiSafeHtmlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String html = request.getParameter("html");
            try {
                String safe = org.owasp.esapi.ESAPI.validator()
                        .getValidSafeHTML("comment", html, 2000, false);
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().println("<div>" + safe + "</div>");
            } catch (org.owasp.esapi.errors.ValidationException e) {
                throw new ServletException(e);
            }
        }
    }

    /** XSS — Jenkins hudson.Util.escape HTML-escapes the value. */
    @WebServlet("/barrier/xss-hudson-escape")
    public static class SafeHudsonEscapeServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = hudson.Util.escape(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    /** XSS — pixee HtmlEncoder.encode HTML-escapes the value. */
    @WebServlet("/barrier/xss-pixee-htmlEncoder")
    public static class SafePixeeHtmlEncoderServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = io.github.pixee.security.HtmlEncoder.encode(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    // ── unvalidated-redirect ──────────────────────────────────────────────

    /** UrlRedirect — URLEncoder.encode before sendRedirect. */
    @WebServlet("/barrier/redirect-urlencoder")
    public static class SafeRedirectUrlEncoderServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            String encoded = java.net.URLEncoder.encode(target, "UTF-8");
            response.sendRedirect("/safe?next=" + encoded);
        }
    }

    /** UrlRedirect — Guava urlPathSegmentEscaper before sendRedirect. */
    @WebServlet("/barrier/redirect-guava-pathseg")
    public static class SafeRedirectGuavaServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            String encoded = com.google.common.net.UrlEscapers.urlPathSegmentEscaper().escape(target);
            response.sendRedirect("/safe/" + encoded);
        }
    }

    /** UrlRedirect — ESAPI Validator.getValidRedirectLocation. */
    @WebServlet("/barrier/redirect-esapi-location")
    public static class SafeRedirectEsapiLocationServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String target = request.getParameter("target");
            try {
                String safe = org.owasp.esapi.ESAPI.validator()
                        .getValidRedirectLocation("redirect", target, false);
                response.sendRedirect(safe);
            } catch (org.owasp.esapi.errors.ValidationException e) {
                throw new ServletException(e);
            }
        }
    }

    // ── smtp-crlf-injection ───────────────────────────────────────────────

    /** SmtpInjection — pixee Newlines.stripAll strips CR/LF before setSubject. */
    @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "smtp-crlf-injection")
    public void safeSmtpPixeeNewlines(HttpServletRequest request) throws Exception {
        String subject = request.getParameter("subject");
        String safe = io.github.pixee.security.Newlines.stripAll(subject);
        java.util.Properties props = new java.util.Properties();
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props);
        javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
        msg.setSubject(safe);
    }

    /** SmtpInjection — Apache Commons Text escapeJava strips CR/LF before setSubject. */
    @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "smtp-crlf-injection")
    public void safeSmtpEscapeJava(HttpServletRequest request) throws Exception {
        String subject = request.getParameter("subject");
        String safe = org.apache.commons.text.StringEscapeUtils.escapeJava(subject);
        java.util.Properties props = new java.util.Properties();
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);
        javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
        msg.setSubject(safe);
    }

    /** SmtpInjection — replaceAll("[\r\n]", _) strips CR/LF before setHeader. */
    @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "smtp-crlf-injection")
    public void safeSmtpReplaceAllBracket(HttpServletRequest request) throws Exception {
        String headerValue = request.getParameter("header");
        String safe = headerValue.replaceAll("[\\r\\n]", "_");
        java.util.Properties props = new java.util.Properties();
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);
        javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
        msg.setHeader("X-Custom", safe);
    }

    // ── http-response-splitting (CRLF) ────────────────────────────────────

    /** ResponseSplitting — Apache Commons Text escapeJava neutralises CR/LF. */
    @WebServlet("/barrier/crlf-escapeJava")
    public static class SafeCrlfEscapeJavaServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userInput = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeJava(userInput);
            response.setHeader("X-User", safe);
        }
    }

    /** ResponseSplitting — replaceAll("[\r\n]+", _) assigned to var. */
    @WebServlet("/barrier/crlf-replaceAll-bracket")
    public static class SafeCrlfReplaceAllBracketServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userInput = request.getParameter("name");
            String safe = userInput.replaceAll("[\\r\\n]+", "_");
            response.setHeader("X-User", safe);
        }
    }

    /** ResponseSplitting — URLEncoder.encode percent-encodes CR/LF (%0D, %0A). */
    @WebServlet("/barrier/crlf-urlencoder")
    public static class SafeCrlfUrlEncoderServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userInput = request.getParameter("name");
            String safe = java.net.URLEncoder.encode(userInput, "UTF-8");
            response.setHeader("X-User", safe);
        }
    }

    /** ResponseSplitting — Guava UrlEscapers.urlPathSegmentEscaper percent-encodes CR/LF. */
    @WebServlet("/barrier/crlf-guava-urlescaper")
    public static class SafeCrlfGuavaUrlEscaperServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userInput = request.getParameter("name");
            String safe = com.google.common.net.UrlEscapers.urlFormParameterEscaper().escape(userInput);
            response.setHeader("X-User", safe);
        }
    }

    /** ResponseSplitting — pixee Newlines.stripAll. */
    @WebServlet("/barrier/crlf-pixee-newlines")
    public static class SafeCrlfPixeeNewlinesServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userInput = request.getParameter("name");
            String safe = io.github.pixee.security.Newlines.stripAll(userInput);
            response.setHeader("X-User", safe);
        }
    }

    // ── xss-in-spring-app ─────────────────────────────────────────────────

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/barrier/xss-spring")
    public static class SpringXssBarrierController {

        /** XSS-in-spring — HtmlUtils.htmlEscape. */
        @org.springframework.web.bind.annotation.GetMapping("/htmlescape")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeSpringHtmlEscape(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.springframework.web.util.HtmlUtils.htmlEscape(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>Hello, " + safe + "!</h1>");
        }

        /** XSS-in-spring — OWASP Encode.forHtml. */
        @org.springframework.web.bind.annotation.GetMapping("/owaspforhtml")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeSpringOwaspForHtml(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.owasp.encoder.Encode.forHtml(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }

        /** XSS-in-spring — Apache Commons Text escapeHtml4. */
        @org.springframework.web.bind.annotation.GetMapping("/commonstext")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeSpringEscapeHtml4(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<h1>" + safe + "</h1>");
        }
    }

    // ── xpath ──────────────────────────────────────────────────────────────

    /** XPath — OWASP Encode.forXml neutralises XML metacharacters. */
    @WebServlet("/barrier/xpath-owasp-forXml")
    public static class SafeXpathOwaspForXmlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String userInput = request.getParameter("user");
            String encoded = org.owasp.encoder.Encode.forXml(userInput);
            try {
                javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                xpath.evaluate("//user[@name='" + encoded + "']", "<root/>");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /** XPath — Apache Commons Text escapeXml10. */
    @WebServlet("/barrier/xpath-commons-escapeXml10")
    public static class SafeXpathCommonsEscapeXml10Servlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String userInput = request.getParameter("user");
            String encoded = org.apache.commons.text.StringEscapeUtils.escapeXml10(userInput);
            try {
                javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                xpath.evaluate("//user[@name='" + encoded + "']", "<root/>");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // ── ldap (extra encoder variants) ─────────────────────────────────────

    /** LdapInjection — OWASP ESAPI encodeForLDAP. */
    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection")
    public void safeLdapEsapiEncodeForLdap(HttpServletRequest request) throws Exception {
        String username = request.getParameter("username");
        String encoded = org.owasp.esapi.ESAPI.encoder().encodeForLDAP(username);
        String filter = "(uid=" + encoded + ")";
        javax.naming.directory.SearchControls ctls = new javax.naming.directory.SearchControls();
        javax.naming.directory.DirContext ctx = null;
        if (ctx != null) ctx.search("dc=example,dc=com", filter, ctls);
    }

    /** LdapInjection — OWASP ESAPI encodeForDN. */
    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection")
    public void safeLdapEsapiEncodeForDn(HttpServletRequest request) throws Exception {
        String dn = request.getParameter("dn");
        String encoded = org.owasp.esapi.ESAPI.encoder().encodeForDN(dn);
        javax.naming.directory.SearchControls ctls = new javax.naming.directory.SearchControls();
        javax.naming.directory.DirContext ctx = null;
        if (ctx != null) ctx.search(encoded, "(objectClass=*)", ctls);
    }

    // ── template-injection (SSTI) ──────────────────────────────────────────

    /** SSTI — OWASP Encode.forHtml prevents template metacharacter injection. */
    @WebServlet("/barrier/ssti-owasp-forHtml")
    public static class SafeSstiOwaspForHtmlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String template = request.getParameter("template");
            String safe = org.owasp.encoder.Encode.forHtml(template);
            try {
                org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
                StringWriter writer = new StringWriter();
                org.apache.velocity.app.Velocity.evaluate(ctx, writer, "tag", safe);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /** SSTI — Spring HtmlUtils.htmlEscape. */
    @WebServlet("/barrier/ssti-htmlescape")
    public static class SafeSstiHtmlEscapeServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String template = request.getParameter("template");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscape(template);
            try {
                org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
                StringWriter writer = new StringWriter();
                org.apache.velocity.app.Velocity.evaluate(ctx, writer, "tag", safe);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // ── response-injection ────────────────────────────────────────────────
    // These samples mirror the XSS encoder barriers but without any safe
    // content type, so they hit the lower-severity response-injection sink.

    /** response-injection — Apache Commons Text escapeEcmaScript. */
    @WebServlet("/barrier/respinj-escapeEcmaScript")
    public static class SafeRespInjEscapeEcmaScriptServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — Apache Commons Lang3 escapeEcmaScript. */
    @WebServlet("/barrier/respinj-lang3-escapeEcmaScript")
    public static class SafeRespInjLang3EscapeEcmaScriptServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.lang3.StringEscapeUtils.escapeEcmaScript(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP ESAPI encodeForURL. */
    @WebServlet("/barrier/respinj-esapi-url")
    public static class SafeRespInjEsapiUrlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            try {
                String safe = org.owasp.esapi.ESAPI.encoder().encodeForURL(name);
                response.getWriter().println(safe);
            } catch (org.owasp.esapi.errors.EncodingException e) {
                throw new ServletException(e);
            }
        }
    }

    /** response-injection — OWASP ESAPI encodeForCSS. */
    @WebServlet("/barrier/respinj-esapi-css")
    public static class SafeRespInjEsapiCssServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForCSS(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP ESAPI encodeForJavaScript. */
    @WebServlet("/barrier/respinj-esapi-js")
    public static class SafeRespInjEsapiJsServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForJavaScript(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP ESAPI encodeForXML. */
    @WebServlet("/barrier/respinj-esapi-xml")
    public static class SafeRespInjEsapiXmlServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForXML(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP ESAPI encodeForXMLAttribute. */
    @WebServlet("/barrier/respinj-esapi-xmlattr")
    public static class SafeRespInjEsapiXmlAttrServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForXMLAttribute(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP ESAPI encodeForHTMLAttribute. */
    @WebServlet("/barrier/respinj-esapi-htmlattr")
    public static class SafeRespInjEsapiHtmlAttrServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForHTMLAttribute(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP Encode.forJavaScriptAttribute. */
    @WebServlet("/barrier/respinj-owasp-jsAttr")
    public static class SafeRespInjOwaspJsAttrServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forJavaScriptAttribute(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — OWASP Encode.forCssString. */
    @WebServlet("/barrier/respinj-owasp-cssString")
    public static class SafeRespInjOwaspCssStringServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.owasp.encoder.Encode.forCssString(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — Spring HtmlUtils.htmlEscapeDecimal. */
    @WebServlet("/barrier/respinj-spring-decimal")
    public static class SafeRespInjSpringDecimalServlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.springframework.web.util.HtmlUtils.htmlEscapeDecimal(name);
            response.getWriter().println(safe);
        }
    }

    /** response-injection — Apache Commons Text escapeXml11. */
    @WebServlet("/barrier/respinj-escapeXml11")
    public static class SafeRespInjEscapeXml11Servlet extends HttpServlet {
        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String name = request.getParameter("name");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeXml11(name);
            response.getWriter().println(safe);
        }
    }

    // ── spring-response-injection ─────────────────────────────────────────

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/barrier/respinj-spring")
    public static class SpringRespInjBarrierController {

        /** spring-response-injection — Apache Commons Text escapeEcmaScript. */
        @org.springframework.web.bind.annotation.GetMapping("/escapeEcmaScript")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespEscapeEcmaScript(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.apache.commons.text.StringEscapeUtils.escapeEcmaScript(name);
            response.getWriter().println(safe);
        }

        /** spring-response-injection — OWASP ESAPI encodeForURL. */
        @org.springframework.web.bind.annotation.GetMapping("/esapiUrl")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespEsapiUrl(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            try {
                String safe = org.owasp.esapi.ESAPI.encoder().encodeForURL(name);
                response.getWriter().println(safe);
            } catch (org.owasp.esapi.errors.EncodingException e) {
                throw new IOException(e);
            }
        }

        /** spring-response-injection — OWASP ESAPI encodeForJavaScript. */
        @org.springframework.web.bind.annotation.GetMapping("/esapiJs")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespEsapiJs(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.owasp.esapi.ESAPI.encoder().encodeForJavaScript(name);
            response.getWriter().println(safe);
        }

        /** spring-response-injection — OWASP Encode.forJavaScript. */
        @org.springframework.web.bind.annotation.GetMapping("/owaspJs")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespOwaspJs(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.owasp.encoder.Encode.forJavaScript(name);
            response.getWriter().println(safe);
        }

        /** spring-response-injection — Apache Commons Text escapeXml10. */
        @org.springframework.web.bind.annotation.GetMapping("/escapeXml10")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespEscapeXml10(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.apache.commons.text.StringEscapeUtils.escapeXml10(name);
            response.getWriter().println(safe);
        }

        /** spring-response-injection — Spring HtmlUtils.htmlEscapeHex. */
        @org.springframework.web.bind.annotation.GetMapping("/htmlEscapeHex")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void safeSpringRespHtmlEscapeHex(
                @org.springframework.web.bind.annotation.RequestParam("name") String name,
                HttpServletResponse response) throws IOException {
            String safe = org.springframework.web.util.HtmlUtils.htmlEscapeHex(name);
            response.getWriter().println(safe);
        }
    }
}

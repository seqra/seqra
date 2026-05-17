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

    /** LineBreaksLogInjectionSanitizer — replaceAll("[\r\n]", _) assigned to var. */
    @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection")
    public void safeLogStripCrLfBracket(HttpServletRequest request) {
        String input = request.getParameter("input");
        String safe = input.replaceAll("[\\r\\n]", "_");
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

    // ── smtp-crlf-injection ───────────────────────────────────────────────

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
}

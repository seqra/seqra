package security.sources;

import java.io.BufferedReader;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.sql.DataSource;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for HttpServletRequest, Cookie, Part, and ServletRequest methods.
 */
public class ServletRequestSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getQueryString(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String qs = request.getQueryString();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + qs + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getRequestURI(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String uri = request.getRequestURI();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getPathInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = request.getPathInfo();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + path + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getServletPath(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String sp = request.getServletPath();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + sp + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getRemoteUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String user = request.getRemoteUser();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + user + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_cookieName(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Cookie cookie = request.getCookies()[0];
        String name = cookie.getName();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partHeader(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        String ct = part.getContentType();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + ct + "'");
        }
    }

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_servletRequestReader(HttpServletRequest request, HttpServletResponse response) throws Exception {
        BufferedReader reader = ((ServletRequest) request).getReader();
        String line = reader.readLine();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + line + "'");
        }
    }

    // ── HttpServletRequest.getHeaderNames ────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getHeaderNames(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Enumeration<String> headers = request.getHeaderNames();
        String headerName = headers.nextElement();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + headerName + "'");
        }
    }

    // ── HttpServletRequest.getParameterNames ─────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getParameterNames(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Enumeration<String> params = request.getParameterNames();
        String paramName = params.nextElement();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + paramName + "'");
        }
    }

    // ── HttpServletRequest.getParameterValues ────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getParameterValues(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String[] values = request.getParameterValues("key");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + values[0] + "'");
        }
    }

    // ── HttpServletRequest.getRequestURL ─────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_getRequestURL(HttpServletRequest request, HttpServletResponse response) throws Exception {
        StringBuffer url = request.getRequestURL();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + url + "'");
        }
    }

    // ── Cookie.getComment ────────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_cookieComment(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Cookie cookie = request.getCookies()[0];
        String comment = cookie.getComment();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + comment + "'");
        }
    }

    // ── Part.getHeader ───────────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partGetHeader(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        String header = part.getHeader("Content-Disposition");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + header + "'");
        }
    }

    // ── Part.getHeaderNames ──────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partGetHeaderNames(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        Collection<String> headerNames = part.getHeaderNames();
        String name = headerNames.iterator().next();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
    }

    // ── Part.getHeaders ──────────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partGetHeaders(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        Collection<String> headers = part.getHeaders("Content-Disposition");
        String header = headers.iterator().next();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + header + "'");
        }
    }

    // ── Part.getInputStream ──────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partGetInputStream(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        InputStream is = part.getInputStream();
        String data = new String(is.readAllBytes());
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + data + "'");
        }
    }

    // ── Part.getName ─────────────────────────────────────────────────────

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    protected void doGet_partGetName(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Part part = request.getPart("file");
        String name = part.getName();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
    }
}

package security.sensitivedataexposure;

import org.seqra.sast.test.util.PositiveRuleSample;
import org.seqra.sast.test.util.NegativeRuleSample;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.ServerSocket;

/**
 * Samples for sensitive-data-exposure rules.
 */
public class SensitiveDataExposureSamples {

    // cookie-issecure-false

    @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "cookie-issecure-false")
    public Cookie insecureSessionCookie() {
        // VULNERABLE: create a cookie without setting Secure, allowing cleartext transport
        Cookie session = new Cookie("SESSIONID", "sensitive-session-id");
        return session;
    }

    @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "cookie-issecure-false")
    public Cookie secureSessionCookie() {
        Cookie session = new Cookie("SESSIONID", "sensitive-session-id");
        // SAFE: explicitly mark cookie as Secure (and typically HttpOnly, but rule focuses on Secure)
        session.setSecure(true);
        return session;
    }

    // unencrypted-socket

    @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "unencrypted-socket")
    public Socket createUnencryptedSocket(String host, int port) throws IOException {
        // VULNERABLE: plain Socket, no TLS
        return new Socket(host, port);
    }

    @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "unencrypted-socket")
    public javax.net.ssl.SSLSocket createEncryptedSocket(String host, int port) throws Exception {
        javax.net.ssl.SSLSocketFactory factory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        return (javax.net.ssl.SSLSocket) factory.createSocket(host, port);
    }

    @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "unencrypted-socket")
    public ServerSocket createUnencryptedServerSocket(int port) throws IOException {
        // VULNERABLE: plain ServerSocket, no TLS
        return new ServerSocket(port);
    }

    @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "unencrypted-socket")
    public javax.net.ssl.SSLServerSocket createEncryptedServerSocket(int port) throws Exception {
        javax.net.ssl.SSLServerSocketFactory factory = (javax.net.ssl.SSLServerSocketFactory) javax.net.ssl.SSLServerSocketFactory.getDefault();
        return (javax.net.ssl.SSLServerSocket) factory.createServerSocket(port);
    }

    // url-rewriting

    public static class UrlRewritingController {

        @org.springframework.web.bind.annotation.GetMapping("/track")
        @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "url-rewriting")
        public void track(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String product = request.getParameter("id");
            String target = "https://partner.example.com/track?product=" + product;
            // VULNERABLE: encodeRedirectURL may append ;jsessionid, exposing the session id
            String encoded = response.encodeRedirectURL(target);
            response.sendRedirect(encoded);
        }

        @org.springframework.web.bind.annotation.GetMapping("/trackSafe")
        @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "url-rewriting")
        public void trackSafe(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String product = request.getParameter("id");
            String target = "https://partner.example.com/track?product=" + product;
            // SAFE: do not call encodeRedirectURL for external HTTPS URLs
            response.sendRedirect(target);
        }
    }

    // file-disclosure-request-dispatcher (taint join rule via untrusted path)

    public static class FileDisclosureServlet extends HttpServlet {

        @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "file-disclosure-request-dispatcher")
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String path = request.getParameter("view");
            // VULNERABLE: pass user-controlled path directly to RequestDispatcher
            RequestDispatcher dispatcher = request.getRequestDispatcher(path);
            dispatcher.forward(request, response);
        }

        @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "file-disclosure-request-dispatcher")
        protected void doGetSafe(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String key = request.getParameter("view");
            String safePath;
            if ("home".equals(key)) {
                safePath = "/WEB-INF/views/home.jsp";
            } else if ("profile".equals(key)) {
                safePath = "/WEB-INF/views/profile.jsp";
            } else {
                safePath = "/WEB-INF/views/error.jsp";
            }
            // SAFE: use redirect with a controlled, server-side selected path
            response.sendRedirect(safePath);
        }
    }

    // jsp-file-disclosure (taint join rule via ModelAndView / view name)

    public static class JspFileDisclosureController {

        @org.springframework.web.bind.annotation.RequestMapping(value = "/mvc", method = org.springframework.web.bind.annotation.RequestMethod.GET)
        @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "jsp-file-disclosure")
        public org.springframework.web.servlet.ModelAndView mvcVulnerable(HttpServletRequest request, HttpServletResponse response) {
            String viewName = request.getParameter("view");
            // VULNERABLE: untrusted view name used directly
            return new org.springframework.web.servlet.ModelAndView(viewName);
        }

        @org.springframework.web.bind.annotation.RequestMapping(value = "/mvcSafe", method = org.springframework.web.bind.annotation.RequestMethod.GET)
        @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "jsp-file-disclosure")
        public org.springframework.web.servlet.ModelAndView mvcSafe(HttpServletRequest request, HttpServletResponse response) {
            String key = request.getParameter("view");
            String resolvedView;
            if ("home".equals(key)) {
                resolvedView = "home";
            } else if ("profile".equals(key)) {
                resolvedView = "profile";
            } else {
                resolvedView = "error";
            }
            // SAFE: view name is resolved via lookup, not directly controlled by user-supplied path
            return new org.springframework.web.servlet.ModelAndView(resolvedView);
        }
    }

    // stacktrace-printing-in-error-message

    @PositiveRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "stacktrace-printing-in-error-message")
    public void printStackTraceToStdout(Exception e) {
        // VULNERABLE: prints stack trace directly, potentially exposing sensitive data
        e.printStackTrace();
    }

    @NegativeRuleSample(value = "java/security/sensitive-data-exposure.yaml", id = "stacktrace-printing-in-error-message")
    public void logStackTraceSafely(Exception e, PrintWriter log) {
        // SAFE: write a generic error message and avoid exposing internal details
        log.println("An error occurred. Please contact support with the request ID.");
        // Stack trace would typically be logged to a protected log instead of stdout; omitted here.
    }
}

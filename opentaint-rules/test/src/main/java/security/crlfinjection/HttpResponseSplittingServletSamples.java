package security.crlfinjection;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for http-response-splitting-in-servlet.
 */
public class HttpResponseSplittingServletSamples {

    /**
     * Unsafe servlet that writes untrusted input directly into HTTP headers.
     */
    @WebServlet("/http-response-splitting-in-servlet/unsafe")
    public static class UnsafeHeaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String user = request.getParameter("user"); // attacker-controlled
            String next = request.getParameter("next"); // attacker-controlled

            // VULNERABLE: user-controlled value is placed directly into header
            response.setHeader("X-User", user);

            // VULNERABLE: user-controlled value concatenated into redirect URL (Location header)
            response.sendRedirect("/home?next=" + next);
        }
    }

    /**
     * Safe servlet that validates and encodes header and redirect values.
     */
    @WebServlet("/http-response-splitting-in-servlet/safe")
    public static class SafeHeaderServlet extends HttpServlet {

        @Override
// TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String user = request.getParameter("user");
            if (user == null) {
                user = "anonymous";
            }

            // Reject CR/LF characters that could break header structure
            if (user.contains("\r") || user.contains("\n")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user");
                return;
            }

            // Enforce a simple allow-list for header-safe username
            if (!user.matches("^[A-Za-z0-9_-]{1,32}$")) {
                user = "anonymous";
            }

            response.setHeader("X-User", user);

            String next = request.getParameter("next");
            if (next == null || next.isBlank()) {
                next = "/";
            }

            // Reject any CR/LF in redirect parameter as well
            if (next.contains("\r") || next.contains("\n")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid next parameter");
                return;
            }

            // Only allow local paths to avoid open redirect-style issues (extra hardening)
            if (!next.startsWith("/")) {
                next = "/";
            }

            // In this simplified example we avoid extra encoding helpers and rely
            // on already-validated values that do not contain CR/LF or dangerous characters.
            response.sendRedirect(next);
        }
    }
}

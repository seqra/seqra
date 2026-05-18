package security.crlfinjection;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

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
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
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
     * Unsafe servlet that writes untrusted input into Cookie values.
     */
    @WebServlet("/http-response-splitting-in-servlet/unsafe-cookie")
    public static class UnsafeCookieServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String value = request.getParameter("value"); // attacker-controlled
            // VULNERABLE: user-controlled value is placed directly into cookie
            javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("session", value);
            response.addCookie(cookie);
        }
    }

    /**
     * Unsafe servlet that writes untrusted input into JAX-RS Response headers.
     */
    @WebServlet("/http-response-splitting-in-servlet/unsafe-jaxrs")
    public static class UnsafeJaxRsResponseBuilderHeaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String user = request.getParameter("user"); // attacker-controlled
            // VULNERABLE: user-controlled value is placed into JAX-RS Response header via builder chain
            Response.ok().header("X-User", user).build();
        }
    }
}

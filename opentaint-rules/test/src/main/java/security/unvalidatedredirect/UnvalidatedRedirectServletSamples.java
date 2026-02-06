package security.unvalidatedredirect;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for unvalidated-redirect-in-servlet rule.
 */
public class UnvalidatedRedirectServletSamples {

    public static class UnsafeUnvalidatedRedirectServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: unvalidated user-controlled URL is used directly in redirect
            String url = request.getParameter("url");
            if (url != null && !url.isEmpty()) {
                response.sendRedirect(url);
            } else {
                response.sendRedirect("/home.jsp");
            }
        }
    }
    public static class SafeValidatedRedirectServlet extends HttpServlet {

        private static final Set<String> ALLOWED_DOMAINS = Set.of("example.com", "trusted-partner.com");

        @Override
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String url = request.getParameter("url");
            if (url == null) {
                response.sendRedirect(request.getContextPath() + "/home.jsp");
                return;
            }

            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                String scheme = uri.getScheme();

                if (host != null
                        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && ALLOWED_DOMAINS.contains(host.toLowerCase())) {
                    // SAFE: host and scheme validated against allowlist
                    response.sendRedirect(uri.toString());
                } else {
                    // Fallback to a safe internal page
                    response.sendRedirect(request.getContextPath() + "/home.jsp");
                }
            } catch (URISyntaxException e) {
                // Invalid URL; redirect to safe internal page
                response.sendRedirect(request.getContextPath() + "/home.jsp");
            }
        }
    }

    /**
     * SAFE: redirect URL is from getContextPath() which is a sanitized source
     * (not user-controlled, comes from server configuration).
     */
    public static class SafeContextPathRedirectServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // SAFE: getContextPath() returns the server-configured context path, not user input
            String url = request.getContextPath();
            response.sendRedirect(url + "/home.jsp");
        }
    }
}

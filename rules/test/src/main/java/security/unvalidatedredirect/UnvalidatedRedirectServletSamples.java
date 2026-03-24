package security.unvalidatedredirect;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

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

    // --- HttpServletResponse.addHeader("Location", ...) ---

    public static class UnsafeAddLocationHeaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL placed directly in Location header
            String url = request.getParameter("url");
            response.addHeader("Location", url);
        }
    }

    // --- JAX-RS Response.seeOther / temporaryRedirect ---

    public static class UnsafeJaxRsSeeOtherServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URI passed to Response.seeOther
            String url = request.getParameter("url");
            Response.seeOther(URI.create(url));
        }
    }

    public static class UnsafeJaxRsTemporaryRedirectServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URI passed to Response.temporaryRedirect
            String url = request.getParameter("url");
            Response.temporaryRedirect(URI.create(url));
        }
    }

    // --- java.awt.Desktop.browse ---

    public static class UnsafeDesktopBrowseServlet extends HttpServlet {

        @Override
        // TODO: Analyzer FN – taint does not propagate through URI.create(); re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URI passed to Desktop.browse
            String url = request.getParameter("url");
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception e) {
                // ignored
            }
        }
    }

    // --- Jenkins Stapler redirect methods ---

    public static class UnsafeStaplerRedirectToServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to HttpResponses.redirectTo
            String url = request.getParameter("url");
            throw HttpResponses.redirectTo(url);
        }
    }

    public static class UnsafeStaplerRedirectToWithStatusServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to HttpResponses.redirectTo with status
            String url = request.getParameter("url");
            throw HttpResponses.redirectTo(302, url);
        }
    }

    public static class UnsafeStaplerSendRedirectServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to StaplerResponse.sendRedirect
            String url = request.getParameter("url");
            ((StaplerResponse) response).sendRedirect(url);
        }
    }

    public static class UnsafeStaplerSendRedirectWithStatusServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to StaplerResponse.sendRedirect with status
            String url = request.getParameter("url");
            ((StaplerResponse) response).sendRedirect(302, url);
        }
    }

    public static class UnsafeStaplerSendRedirect2Servlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to StaplerResponse.sendRedirect2
            String url = request.getParameter("url");
            ((StaplerResponse) response).sendRedirect2(url);
        }
    }

    // --- url-forward: ServletContext.getRequestDispatcher ---

    public static class UnsafeServletContextDispatcherServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled path passed to ServletContext.getRequestDispatcher
            String path = request.getParameter("path");
            ServletContext ctx = getServletContext();
            RequestDispatcher dispatcher = ctx.getRequestDispatcher(path);
            dispatcher.forward(request, response);
        }
    }

    // --- url-forward: PortletContext.getRequestDispatcher ---

    public static class UnsafePortletContextDispatcherServlet extends HttpServlet {

        private javax.portlet.PortletContext portletContext;

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled path passed to PortletContext.getRequestDispatcher
            String path = request.getParameter("path");
            javax.portlet.PortletRequestDispatcher dispatcher = portletContext.getRequestDispatcher(path);
        }
    }

    // --- url-forward: StaplerResponse.forward ---

    public static class UnsafeStaplerForwardServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // VULNERABLE: user-controlled URL passed to StaplerResponse.forward
            String url = request.getParameter("url");
            ((StaplerResponse) response).forward(this, url, (StaplerRequest) request);
        }
    }
}

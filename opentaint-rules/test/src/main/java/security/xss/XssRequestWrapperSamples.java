package security.xss;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;


/**
 * Samples for xssrequestwrapper-is-insecure.
 */
public class XssRequestWrapperSamples {

    /**
     * Insecure request wrapper pattern based on the well-known "XSSRequestWrapper" implementation.
     *
     * This closely mirrors the structure matched by the rule:
     * a class named {@code XSSRequestWrapper} extending {@link HttpServletRequestWrapper}.
     */
    public static class XSSRequestWrapper extends HttpServletRequestWrapper {

        public XSSRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return stripXSS(value);
        }

        private String stripXSS(String value) {
            if (value == null) {
                return null;
            }
            // Intentionally naive filter similar to the blog-post implementation;
            // this is what the rule considers insecure.
            return value
                    .replaceAll("<", "")
                    .replaceAll(">", "")
                    .replaceAll("\\(", "")
                    .replaceAll("\\)", "");

        }
    }

    /**
     * Unsafe servlet that relies on the insecure XSSRequestWrapper implementation.
     */
    @WebServlet("/xssrequestwrapper-is-insecure/unsafe")
    public static class InsecureWrappedServlet extends javax.servlet.http.HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xssrequestwrapper-is-insecure")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            HttpServletRequest wrapped = new XSSRequestWrapper(request);
            String name = wrapped.getParameter("name");

            // In a real application this would typically be written into the response;
            // here we just echo it to keep the example minimal.
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("Hello, " + name);
        }
    }

    /**
     * Simple filter that demonstrates a safer pattern: do not try to implement your own
     * XSSRequestWrapper; instead, use proper output encoding when rendering.
     */
    @WebFilter("/xssrequestwrapper-is-insecure/safe")
    public static class SafeEncodingFilter extends HttpFilter {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xssrequestwrapper-is-insecure")
        protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            // No custom wrapper; downstream components are expected to perform proper encoding.
            chain.doFilter(request, response);
        }

    }
}

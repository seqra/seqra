package security.externalconfigurationcontrol;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for unsafe-reflection-in-servlet rule.
 */
public class UnsafeReflectionServletSamples {

    @WebServlet("/external-config/reflection/unsafe")
    public static class DynamicLoaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/external-configuration-control.yaml", id = "unsafe-reflection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            // User-controlled data from request parameter
            String className = request.getParameter("className");

            try {
                // UNSAFE: user input directly controls Class.forName
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                response.getWriter().println("Loaded class: " + instance.getClass().getName());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/external-config/reflection/safe")
    public static class SafeDynamicLoaderServlet extends HttpServlet {

        // Allowlist of logical names to actual classes
        private static final Map<String, Class<?>> ALLOWED_CLASSES = new HashMap<>();

        static {
            ALLOWED_CLASSES.put("basicReport", DummyReport.class);
            ALLOWED_CLASSES.put("summaryReport", DummyReport.class);
        }

        @Override
        @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "unsafe-reflection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String type = request.getParameter("reportType");

            Class<?> clazz = ALLOWED_CLASSES.get(type);
            if (clazz == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid report type");
                return;
            }

            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                // Safe: only instances of known, vetted classes are created
                response.getWriter().println("Generated report of type: " + type + " -> " + instance.getClass().getName());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * Simple dummy class used in allowlist.
     */
    public static class DummyReport {
    }
}

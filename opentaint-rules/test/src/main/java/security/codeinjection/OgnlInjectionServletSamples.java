package security.codeinjection;

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

import ognl.Ognl;

/**
 * Samples for ognl-injection-in-servlet.
 */
public class OgnlInjectionServletSamples {

    @WebServlet("/ognl-injection-in-servlet/unsafe")
    public static class UnsafeOgnlServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // Attacker controls the "expr" parameter
            String expr = request.getParameter("expr");

            // Build OGNL context from application objects
            Map<String, Object> context = new HashMap<>();
            context.put("userService", new UserService());
            context.put("system", System.class);

            try {
                // VULNERABLE: evaluating untrusted input as an OGNL expression
                Object value = Ognl.getValue(expr, context, new Object());

                response.getWriter().println(String.valueOf(value));
            } catch (Exception ex) {
                throw new ServletException("Failed to evaluate expression", ex);
            }
        }
    }

    @WebServlet("/ognl-injection-in-servlet/safe")
    public static class SafeOgnlServlet extends HttpServlet {

        private final UserService userService = new UserService();

        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)

                throws ServletException, IOException {
            String action = request.getParameter("action");

            // Safer approach: use whitelisted actions instead of evaluating OGNL expressions.
            Object result;
            if ("getProfile".equals(action)) {
                String userId = request.getParameter("userId");
                result = userService.getProfile(userId);
            } else if ("listUsers".equals(action)) {
                result = userService.listUsers();
            } else {
                throw new IllegalArgumentException("Unsupported action");
            }

            response.getWriter().println(String.valueOf(result));
        }
    }

    /** Minimal stub to support the OGNL examples. */
    public static class UserService {
        public Object getProfile(String userId) {
            return "profile-" + userId;
        }

        public Object listUsers() {
            return "users";
        }
    }
}

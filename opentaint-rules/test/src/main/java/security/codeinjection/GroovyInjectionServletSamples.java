package security.codeinjection;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import groovy.lang.GroovyShell;

/**
 * Samples for groovy-injection-in-servlet.
 */
public class GroovyInjectionServletSamples {

    @WebServlet("/groovy-injection-in-servlet/unsafe")
    public static class UnsafeGroovyServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // Attacker controls this parameter (e.g., ?script=...)
            String script = request.getParameter("script");

            GroovyShell shell = new GroovyShell();

            // VULNERABLE: directly evaluates attacker-controlled script code
            Object result = shell.evaluate(script);

            response.getWriter().println(String.valueOf(result));
        }
    }

    @WebServlet("/groovy-injection-in-servlet/safe")
    public static class SafeGroovyServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)

                throws ServletException, IOException {
            String action = request.getParameter("action");

            // Safer pattern: map user-controlled input to a fixed set of allowed operations,
            // without evaluating arbitrary Groovy code.
            String message;
            if ("ping".equals(action)) {
                message = "pong";
            } else if ("version".equals(action)) {
                message = getServletContext().getServerInfo();
            } else {
                message = "unsupported";
            }

            response.getWriter().println(message);
        }
    }
}

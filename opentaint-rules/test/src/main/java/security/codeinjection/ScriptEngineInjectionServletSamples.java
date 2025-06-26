package security.codeinjection;

import java.io.IOException;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for script-engine-injection-in-servlet.
 */
public class ScriptEngineInjectionServletSamples {

    @WebServlet("/script-engine-injection-in-servlet/unsafe")
    public static class UnsafeScriptEngineServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "script-engine-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String expr = request.getParameter("expr");

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("javascript");

            try {
                // VULNERABLE: directly evaluates attacker-controlled script code
                Object result = engine.eval(expr);
                response.getWriter().println(String.valueOf(result));
            } catch (ScriptException e) {
                throw new ServletException("Failed to evaluate script", e);
            }
        }
    }

    @WebServlet("/script-engine-injection-in-servlet/safe")
    public static class SafeScriptEngineServlet extends HttpServlet {

        private final ScriptEngine engine;
        private final CompiledScript compiled;

        public SafeScriptEngineServlet() throws ScriptException {
            ScriptEngineManager manager = new ScriptEngineManager();
            this.engine = manager.getEngineByName("javascript");

            // Trusted, static script embedded in the application
            String script = "function calculate(a, b) { return a + b; } calculate(a, b);";
            this.compiled = ((Compilable) engine).compile(script);
        }

        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "script-engine-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)

                throws ServletException, IOException {
            try {
                int a = Integer.parseInt(request.getParameter("a"));
                int b = Integer.parseInt(request.getParameter("b"));

                Bindings bindings = engine.createBindings();
                bindings.put("a", a);
                bindings.put("b", b);

                // Only the trusted script runs; user data cannot change the code
                Object result = compiled.eval(bindings);
                response.getWriter().println(String.valueOf(result));
            } catch (NumberFormatException | ScriptException ex) {
                throw new ServletException("Failed to execute safe script", ex);
            }
        }
    }
}

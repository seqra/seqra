package security.codeinjection;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for script-engine-injection-in-spring.
 */
public class ScriptEngineInjectionSpringSamples {

    @RestController
    public static class UnsafeScriptEngineController {

        @GetMapping("/script-engine-injection-in-spring/unsafe")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "script-engine-injection-in-spring-app")
        public String unsafeScriptEngine(@RequestParam("expr") String expr) throws ScriptException {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("javascript");

            // VULNERABLE: directly evaluates attacker-controlled script code
            Object result = engine.eval(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    public static class SafeScriptEngineController {

        private final ScriptEngine engine;
        private final CompiledScript compiled;

        public SafeScriptEngineController() throws ScriptException {
            ScriptEngineManager manager = new ScriptEngineManager();
            this.engine = manager.getEngineByName("javascript");

            // Trusted, static script embedded in the application
            String script = "function calculate(a, b) { return a + b; } calculate(a, b);";
            this.compiled = ((Compilable) engine).compile(script);
        }

        @GetMapping("/script-engine-injection-in-spring/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "script-engine-injection-in-spring-app")
        public String safeScriptEngine(@RequestParam("a") int a, @RequestParam("b") int b) throws ScriptException {
            Bindings bindings = engine.createBindings();
            bindings.put("a", a);
            bindings.put("b", b);

            // Only the trusted script runs; user data cannot change the code
            Object result = compiled.eval(bindings);
            return String.valueOf(result);
        }
    }
}

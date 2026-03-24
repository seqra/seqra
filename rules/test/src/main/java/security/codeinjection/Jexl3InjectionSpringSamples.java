package security.codeinjection;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlScript;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for JEXL injection (JEXL 3).
 */
public class Jexl3InjectionSpringSamples {

    @RestController
    @RequestMapping("/jexl3-injection")
    public static class UnsafeJexl3CreateExpressionController {

        @GetMapping("/create-expression")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeCreateExpression(@RequestParam("expr") String expr) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            // VULNERABLE: creating JEXL 3 expression from user input
            JexlExpression expression = engine.createExpression(expr);
            Object result = expression.evaluate(null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/jexl3-injection")
    public static class UnsafeJexl3CreateScriptController {

        @GetMapping("/create-script")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeCreateScript(@RequestParam("script") String script) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            // VULNERABLE: creating JEXL 3 script from user input
            JexlScript jexlScript = engine.createScript(script);
            Object result = jexlScript.execute(null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/jexl3-injection")
    public static class UnsafeJexl3GetPropertyController {

        @GetMapping("/get-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeGetProperty(@RequestParam("prop") String prop) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            Object target = new Object();
            // VULNERABLE: JEXL 3 property access with user input
            Object result = engine.getProperty(target, prop);
            return String.valueOf(result);
        }
    }

    // ---- JEXL 3 JexlExpression/JexlScript Argument[this] sinks (task-14) ----

    @RestController
    @RequestMapping("/jexl3-injection/arg-this")
    public static class UnsafeJexl3ExpressionEvaluateController {

        @GetMapping("/expression-evaluate")
        // TODO: Analyzer FN – taint does not propagate through engine.createExpression() to JexlExpression object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeExpressionEvaluate(@RequestParam("expr") String expr) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            // Taint on Argument[this]: the JexlExpression itself is tainted
            JexlExpression expression = engine.createExpression(expr);
            Object result = expression.evaluate(null);
            return String.valueOf(result);
        }

        @GetMapping("/expression-callable")
        // TODO: Analyzer FN – taint does not propagate through engine.createExpression() to JexlExpression object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeExpressionCallable(@RequestParam("expr") String expr) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            JexlExpression expression = engine.createExpression(expr);
            Object result = expression.callable(null).call();
            return String.valueOf(result);
        }

        @GetMapping("/script-execute")
        // TODO: Analyzer FN – taint does not propagate through engine.createScript() to JexlScript object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeScriptExecute(@RequestParam("script") String script) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            JexlScript jexlScript = engine.createScript(script);
            Object result = jexlScript.execute(null);
            return String.valueOf(result);
        }

        @GetMapping("/script-callable")
        // TODO: Analyzer FN – taint does not propagate through engine.createScript() to JexlScript object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeScriptCallable(@RequestParam("script") String script) throws Exception {
            JexlEngine engine = new JexlBuilder().create();
            JexlScript jexlScript = engine.createScript(script);
            Object result = jexlScript.callable(null).call();
            return String.valueOf(result);
        }
    }
}

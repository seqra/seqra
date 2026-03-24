package security.codeinjection;

import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.Expression;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for JEXL injection (JEXL 2).
 */
public class JexlInjectionSpringSamples {

    @RestController
    @RequestMapping("/jexl-injection")
    public static class UnsafeJexl2CreateExpressionController {

        @GetMapping("/jexl2/create-expression")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeCreateExpression(@RequestParam("expr") String expr) throws Exception {
            JexlEngine engine = new JexlEngine();
            // VULNERABLE: creating JEXL expression from user input
            Expression expression = engine.createExpression(expr);
            Object result = expression.evaluate(null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/jexl-injection")
    public static class UnsafeJexl2GetPropertyController {

        @GetMapping("/jexl2/get-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeGetProperty(@RequestParam("prop") String prop) throws Exception {
            JexlEngine engine = new JexlEngine();
            Object target = new Object();
            // VULNERABLE: JEXL property access with user input
            Object result = engine.getProperty(target, prop);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/jexl-injection")
    public static class UnsafeJexl2SetPropertyController {

        @GetMapping("/jexl2/set-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeSetProperty(@RequestParam("prop") String prop) throws Exception {
            JexlEngine engine = new JexlEngine();
            Object target = new Object();
            // VULNERABLE: JEXL property write with user input
            engine.setProperty(target, prop, "value");
            return "set";
        }
    }

    // ---- JEXL 2 Expression Argument[this] sinks (task-14) ----

    @RestController
    @RequestMapping("/jexl-injection/arg-this")
    public static class UnsafeJexl2ExpressionEvaluateController {

        @GetMapping("/expression-evaluate")
        // TODO: Analyzer FN – taint does not propagate through engine.createExpression() to Expression object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String unsafeExpressionEvaluate(@RequestParam("expr") String expr) throws Exception {
            JexlEngine engine = new JexlEngine();
            // Taint on Argument[this]: the Expression itself is tainted
            Expression expression = engine.createExpression(expr);
            Object result = expression.evaluate(null);
            return String.valueOf(result);
        }

        // Note: JEXL 2 Expression interface does not have callable() method (only JEXL 3 JexlExpression does)
    }

    @RestController
    @RequestMapping("/jexl-injection")
    public static class SafeJexlController {

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-spring-app")
        public String safeJexl(@RequestParam(value = "action", required = false) String action) {
            // Safer: no JEXL evaluation on user input
            if ("compute".equals(action)) {
                JexlEngine engine = new JexlEngine();
                Expression expr = engine.createExpression("1 + 1");
                return String.valueOf(expr.evaluate(null));
            }
            return "unknown action";
        }
    }
}

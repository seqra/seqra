package security.codeinjection;

import org.mvel2.MVEL;
import org.mvel2.MVELRuntime;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.jsr223.MvelCompiledScript;
import org.mvel2.jsr223.MvelScriptEngine;
import org.mvel2.templates.TemplateRuntime;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring MVC samples for MVEL injection.
 */
public class MvelInjectionSpringSamples {

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelEvalController {

        @GetMapping("/eval")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeEval(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating user-controlled MVEL expression
            Object result = MVEL.eval(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelEvalToBooleanController {

        @GetMapping("/eval-to-boolean")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeEvalToBoolean(@RequestParam("expr") String expr) {
            Map<String, Object> vars = new HashMap<>();
            // VULNERABLE: evaluating user-controlled MVEL expression
            boolean result = MVEL.evalToBoolean(expr, vars);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelEvalToStringController {

        @GetMapping("/eval-to-string")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeEvalToString(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating user-controlled MVEL expression
            return MVEL.evalToString(expr);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelExecuteExpressionController {

        @GetMapping("/execute-expression")
        // TODO: Analyzer FN – taint does not propagate through MVEL.compileExpression() to compiled expression;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeExecuteExpression(@RequestParam("expr") String expr) {
            // VULNERABLE: compiling and executing user-controlled MVEL expression
            Object compiled = MVEL.compileExpression(expr);
            Object result = MVEL.executeExpression(compiled);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelTemplateController {

        @GetMapping("/template")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeTemplate(@RequestParam("template") String template) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("name", "World");
            // VULNERABLE: evaluating user-controlled MVEL template
            Object result = TemplateRuntime.eval(template, vars);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelScriptEngineEvalController {

        @GetMapping("/script-engine-eval")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeScriptEngineEval(@RequestParam("expr") String expr) throws Exception {
            // VULNERABLE: evaluating user-controlled MVEL expression via JSR-223
            MvelScriptEngine engine = new MvelScriptEngine();
            Object result = engine.eval(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelExecuteAllExpressionController {

        @GetMapping("/execute-all-expression")
        // TODO: Analyzer FN – taint does not propagate through MVEL.compileExpression() to compiled expression;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeExecuteAllExpression(@RequestParam("expr") String expr) {
            // VULNERABLE: compiling and executing user-controlled MVEL expressions
            Serializable compiled = MVEL.compileExpression(expr);
            Object[] results = MVEL.executeAllExpression(new Serializable[]{compiled}, new Object(), null);
            return String.valueOf(results);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelExecuteSetExpressionController {

        @GetMapping("/execute-set-expression")
        // TODO: Analyzer FN – taint does not propagate through MVEL.compileExpression() to compiled expression;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeExecuteSetExpression(@RequestParam("expr") String expr) {
            // VULNERABLE: compiling and executing user-controlled MVEL set expression
            Serializable compiled = MVEL.compileSetExpression(expr);
            MVEL.executeSetExpression(compiled, new Object(), "value");
            return "done";
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelRuntimeExecuteController {

        @GetMapping("/runtime-execute")
        // TODO: Analyzer FN – taint does not propagate through MVEL.compileExpression() to CompiledExpression;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeRuntimeExecute(@RequestParam("expr") String expr) {
            // VULNERABLE: compiling and executing user-controlled MVEL expression via MVELRuntime
            CompiledExpression compiled = (CompiledExpression) MVEL.compileExpression(expr);
            Object result = MVELRuntime.execute(false, compiled, new Object(), null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelScriptEngineEvaluateController {

        @GetMapping("/script-engine-evaluate")
        // TODO: Analyzer FN – taint does not propagate through MvelScriptEngine.compiledScript() to Serializable;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeScriptEngineEvaluate(@RequestParam("expr") String expr) throws Exception {
            // VULNERABLE: compiling and evaluating user-controlled MVEL expression
            MvelScriptEngine engine = new MvelScriptEngine();
            Serializable compiled = engine.compiledScript(expr);
            Object result = engine.evaluate(compiled, null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class UnsafeMvelCompiledScriptEvalController {

        @GetMapping("/compiled-script-eval")
        // TODO: Analyzer FN – taint does not propagate through MvelScriptEngine.compile() to MvelCompiledScript;
        // re-enable when taint propagation summaries for MVEL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String unsafeCompiledScriptEval(@RequestParam("expr") String expr) throws Exception {
            // VULNERABLE: compiling and evaluating user-controlled MVEL expression
            MvelScriptEngine engine = new MvelScriptEngine();
            MvelCompiledScript compiled = (MvelCompiledScript) engine.compile(expr);
            Object result = compiled.eval((javax.script.ScriptContext) null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/mvel-injection")
    public static class SafeMvelController {

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-spring-app")
        public String safeMvel(@RequestParam("name") String name) {
            // SAFE: expression is static, user input only as data
            Map<String, Object> vars = new HashMap<>();
            vars.put("name", name);
            Object result = MVEL.eval("'Hello, ' + name", vars);
            return String.valueOf(result);
        }
    }
}

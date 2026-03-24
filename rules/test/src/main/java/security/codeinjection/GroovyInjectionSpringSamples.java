package security.codeinjection;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.util.Eval;

/**
 * Spring MVC samples for groovy-injection-in-spring.
 */
public class GroovyInjectionSpringSamples {

    @RestController
    public static class UnsafeGroovyController {

        @GetMapping("/groovy-injection-in-spring/unsafe")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection")
        public String unsafeGroovy(@RequestParam("script") String script) {
            GroovyShell shell = new GroovyShell();

            // VULNERABLE: directly evaluates attacker-controlled script code
            Object result = shell.evaluate(script);

            return String.valueOf(result);
        }
    }

    // ── GroovyClassLoader.parseClass ────────────────────────────────────

    @RestController
    @RequestMapping("/groovy-injection-in-spring")
    public static class UnsafeGroovyParseClassController {

        @GetMapping("/parse-class")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeParseClass(@RequestParam("code") String code) throws Exception {
            GroovyClassLoader loader = new GroovyClassLoader();
            // VULNERABLE: parsing attacker-controlled code into a class
            Class<?> clazz = loader.parseClass(code);
            return clazz.getName();
        }
    }

    // ── Eval.me ─────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/groovy-injection-in-spring")
    public static class UnsafeEvalMeController {

        @GetMapping("/eval-me")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeEvalMe(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating attacker-controlled Groovy expression
            Object result = Eval.me(expr);
            return String.valueOf(result);
        }
    }

    // ── Eval.x ──────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/groovy-injection-in-spring")
    public static class UnsafeEvalXController {

        @GetMapping("/eval-x")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeEvalX(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating attacker-controlled Groovy expression with binding
            Object result = Eval.x("data", expr);
            return String.valueOf(result);
        }
    }

    // ── Eval.xy ─────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/groovy-injection-in-spring")
    public static class UnsafeEvalXyController {

        @GetMapping("/eval-xy")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeEvalXy(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating attacker-controlled Groovy expression with two bindings
            Object result = Eval.xy("a", "b", expr);
            return String.valueOf(result);
        }
    }

    // ── Eval.xyz ────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/groovy-injection-in-spring")
    public static class UnsafeEvalXyzController {

        @GetMapping("/eval-xyz")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeEvalXyz(@RequestParam("expr") String expr) {
            // VULNERABLE: evaluating attacker-controlled Groovy expression with three bindings
            Object result = Eval.xyz("a", "b", "c", expr);
            return String.valueOf(result);
        }
    }

    // ── CompilationUnit.compile (Argument[this]) ────────────────────────

    // TODO: Analyzer FN – taint does not propagate through CompilationUnit.addSource to compile();
    // the sink is on Argument[this] but taint flows through addSource, not the compile call itself.
    // Re-enable when taint propagation summaries for CompilationUnit are added.
    // @RestController
    // @RequestMapping("/groovy-injection-in-spring")
    // public static class UnsafeCompilationUnitController {
    //
    //     @GetMapping("/compilation-unit")
    //     @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
    //     public String unsafeCompile(@RequestParam("code") String code) {
    //         org.codehaus.groovy.control.CompilationUnit cu =
    //                 new org.codehaus.groovy.control.CompilationUnit();
    //         cu.addSource("UserScript", code);
    //         cu.compile();
    //         return "compiled";
    //     }
    // }

    @RestController
    public static class SafeGroovyController {

        @GetMapping("/groovy-injection-in-spring/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection")
        public String safeGroovy(@RequestParam(value = "action", required = false) String action) {
            // Safer pattern: map user-controlled input to a fixed set of allowed operations,
            // without evaluating arbitrary Groovy code.
            if ("ping".equals(action)) {
                return "pong";
            }
            if ("version".equals(action)) {
                return "1.0";
            }
            return "unsupported";
        }
    }
}

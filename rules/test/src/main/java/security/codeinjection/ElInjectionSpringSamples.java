package security.codeinjection;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.validation.ConstraintValidatorContext;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for EL injection sinks (ExpressionFactory.createMethodExpression
 * and buildConstraintViolationWithTemplate).
 */
public class ElInjectionSpringSamples {

    // ── ExpressionFactory.createMethodExpression ────────────────────────

    @RestController
    @RequestMapping("/el-injection-in-spring")
    public static class UnsafeCreateMethodExpressionController {

        @GetMapping("/method-expression")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "spring-el-injection")
        public String unsafeMethodExpression(@RequestParam("expr") String expr,
                                             ELContext elContext) {
            ExpressionFactory factory = ExpressionFactory.newInstance();
            // VULNERABLE: user-controlled EL expression evaluated via createMethodExpression
            MethodExpression me = factory.createMethodExpression(elContext, expr, String.class, new Class<?>[]{});
            Object result = me.invoke(elContext, null);
            return String.valueOf(result);
        }
    }

    // ── buildConstraintViolationWithTemplate ─────────────────────────────

    @RestController
    @RequestMapping("/el-injection-in-spring")
    public static class UnsafeBuildConstraintViolationController {

        @GetMapping("/constraint-violation")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "spring-el-injection")
        public String unsafeConstraintViolation(@RequestParam("template") String template,
                                                ConstraintValidatorContext context) {
            // VULNERABLE: user-controlled template evaluated as EL expression
            context.buildConstraintViolationWithTemplate(template).addConstraintViolation();
            return "validated";
        }
    }
}

package security.codeinjection;

import java.util.HashMap;
import java.util.Map;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ognl.Ognl;

/**
 * Spring MVC samples for ognl-injection-in-spring.
 */
public class OgnlInjectionSpringSamples {

    @RestController
    public static class UnsafeOgnlController {

        @GetMapping("/ognl-injection-in-spring/unsafe")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeOgnl(@RequestParam("expr") String expr) throws Exception {
            // Build OGNL context from application objects
            Map<String, Object> context = new HashMap<>();
            context.put("userService", new UserService());
            context.put("system", System.class);

            // VULNERABLE: evaluating untrusted input as an OGNL expression
            Object value = Ognl.getValue(expr, context, new Object());

            return String.valueOf(value);
        }
    }

    @RestController
    public static class SafeOgnlController {

        private final UserService userService = new UserService();

        @GetMapping("/ognl-injection-in-spring/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String safeOgnl(@RequestParam(value = "action", required = false) String action,
                               @RequestParam(value = "userId", required = false) String userId) {
            // Safer approach: use whitelisted actions instead of evaluating OGNL expressions.
            if ("getProfile".equals(action) && userId != null) {
                return userService.getProfile(userId);
            }
            if ("listUsers".equals(action)) {
                return userService.listUsers();
            }
            throw new IllegalArgumentException("Unsupported action");
        }
    }

    /** Minimal stub to support the OGNL examples. */
    public static class UserService {
        public String getProfile(String userId) {
            return "profile-" + userId;
        }

        public String listUsers() {
            return "users";
        }
    }
}

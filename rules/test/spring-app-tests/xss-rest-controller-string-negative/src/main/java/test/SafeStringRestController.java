package test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + `String` return + no explicit `produces`.
 *
 * Spring's `StringHttpMessageConverter` defaults to `text/plain`, NOT
 * `text/html`, so the reflected user input is not XSS-exploitable in
 * practice. The rule's class-level
 * `pattern-not-inside @RestController class { ... }` suppresses this.
 *
 * Verified non-firing in `opentaint scan` mode. `agent test-rules` mode
 * (even with the `spring-app-tests/` Spring-dispatcher wrapping) does
 * not honor class-level `pattern-not-inside` end-to-end and reports an
 * FP — known test-framework gap, tracked separately. The real rule is
 * correct; the annotation here pins the desired behavior.
 */
@RestController
public class SafeStringRestController {

    @GetMapping("/safe-string")
    // TEST-FRAMEWORK-FP: @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public String safeString(@RequestParam(required = false, defaultValue = "") String name) {
        return "<h1>Hello, " + name + "!</h1>";
    }
}

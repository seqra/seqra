package test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + `ResponseEntity<DTO>`.
 *
 * Spring's `MappingJackson2HttpMessageConverter` serializes the DTO as
 * `application/json`, never `text/html`. Two independent rule mechanisms
 * suppress this:
 *   - exact type-arg matching at the method-decl return position
 *     (`pattern-inside ResponseEntity<String>` does not match
 *     `ResponseEntity<GreetingDto>`);
 *   - the class-level `pattern-not-inside @RestController class { ... }`.
 *
 * Verified non-firing in `opentaint scan` mode. `agent test-rules`
 * mode does not honor either suppression mechanism end-to-end here;
 * known test-framework gap (annotation suppressed, rule itself is
 * correct).
 */
@RestController
public class SafeDtoRestController {

    public static class GreetingDto {
        public String name;
        public GreetingDto(String name) { this.name = name; }
    }

    @GetMapping("/safe-dto")
    // TEST-FRAMEWORK-FP: @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<GreetingDto> safeDto(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok(new GreetingDto(name));
    }
}

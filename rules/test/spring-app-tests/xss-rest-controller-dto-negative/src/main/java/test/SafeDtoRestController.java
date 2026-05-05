package test;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + `ResponseEntity<DTO>`.
 *
 * Spring's `MappingJackson2HttpMessageConverter` serializes the DTO as
 * `application/json`, never `text/html`. Suppression mechanism:
 *   - exact type-arg matching at the method-decl return position
 *     (`pattern-inside ResponseEntity<String>` does not match
 *     `ResponseEntity<GreetingDto>`);
 *   - the raw `pattern-inside ResponseEntity` alternative would
 *     over-match this shape, but a sibling `pattern-not-inside
 *     ResponseEntity<$T>` subtracts every parameterized declaration
 *     from the raw branch, so `ResponseEntity<GreetingDto>` does not
 *     fall through into the raw matcher either.
 */
@RestController
public class SafeDtoRestController {

    public static class GreetingDto {
        public String name;
        public GreetingDto(String name) { this.name = name; }
    }

    @GetMapping("/safe-dto")
    @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<GreetingDto> safeDto(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok(new GreetingDto(name));
    }
}

package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + `ResponseEntity<?>` (wildcard) returning a String body
 * — DEFAULT-DANGEROUS.
 *
 * The wildcard's `?` is invisible to Spring's content negotiation; the
 * runtime body type (String) drives converter selection. With browser
 * Accept the response is served as `text/html;charset=UTF-8`. Empirically
 * validated in `/tmp/spring-content-type-probe/RESULTS.md`.
 */
@RestController
public class UnsafeResponseEntityWildcardController {

    @GetMapping("/unsafe-responseentity-wildcard")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<?> unsafeResponseEntityWildcard(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok("<h1>hi " + name + "</h1>");
    }
}

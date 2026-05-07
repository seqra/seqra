package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + raw `ResponseEntity` (no type argument) returning a
 * String body — DEFAULT-DANGEROUS.
 *
 * Spring's runtime-type negotiation sees the String body and routes it
 * through `StringHttpMessageConverter`, which serves `text/html;charset=UTF-8`
 * under browser Accept. Empirically validated in
 * `/tmp/spring-content-type-probe/RESULTS.md`.
 *
 * The raw-type return must be matched by Branch 1d's
 * `pattern-inside ResponseEntity` + `pattern-not-inside ResponseEntity<$T>`
 * (Semgrep `TypeAwarePatternTest.A24` discrimination), placed at top level
 * under `pattern-sinks:` rather than nested in Branch 1.
 */
@RestController
@SuppressWarnings({"rawtypes", "unchecked"})
public class UnsafeResponseEntityRawController {

    @GetMapping("/unsafe-responseentity-raw")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity unsafeResponseEntityRaw(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok("<h1>hi " + name + "</h1>");
    }
}

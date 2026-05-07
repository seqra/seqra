package test;

import java.nio.charset.StandardCharsets;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + `ResponseEntity<Resource>` — DEFAULT-DANGEROUS.
 *
 * Same converter mechanics as bare `Resource`: `ResourceHttpMessageConverter`
 * advertises the wildcard media type, browser Accept ranks `text/html` first, Spring writes
 * the bytes labeled `text/html`. Empirically validated in
 * `/tmp/spring-content-type-probe/RESULTS.md`.
 */
@RestController
public class UnsafeResponseEntityResourceController {

    @GetMapping("/unsafe-responseentity-resource")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<Resource> unsafeResponseEntityResource(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok(new ByteArrayResource(("<h1>hi " + name + "</h1>").getBytes(StandardCharsets.UTF_8)));
    }
}

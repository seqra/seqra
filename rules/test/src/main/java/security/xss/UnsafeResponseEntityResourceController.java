package security.xss;

import java.nio.charset.StandardCharsets;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// @RestController + ResponseEntity<Resource>. Same wildcard-converter mechanics as
// bare Resource — browser Accept ranks text/html first, bytes labeled text/html.
@RestController
public class UnsafeResponseEntityResourceController {

    @GetMapping("/unsafe-responseentity-resource")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<Resource> unsafeResponseEntityResource(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok(new ByteArrayResource(("<h1>hi " + name + "</h1>").getBytes(StandardCharsets.UTF_8)));
    }
}

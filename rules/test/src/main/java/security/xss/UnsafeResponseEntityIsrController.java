package security.xss;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnsafeResponseEntityIsrController {

    @GetMapping("/unsafe-responseentity-isr")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<InputStreamResource> unsafeResponseEntityIsr(@RequestParam(required = false, defaultValue = "") String name) {
        byte[] bytes = ("<h1>hi " + name + "</h1>").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }
}

package security.xss;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings({"rawtypes", "unchecked"})
public class UnsafeResponseEntityRawController {

    @GetMapping("/unsafe-responseentity-raw")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity unsafeResponseEntityRaw(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok("<h1>hi " + name + "</h1>");
    }
}

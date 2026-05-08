package security.xss;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// @RestController + ResponseEntity<DTO> — Jackson converter serializes as application/json.
// Suppressed by exact type-arg matching at the method-decl return position.
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

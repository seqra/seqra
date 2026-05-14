package security.xss;

import java.nio.charset.StandardCharsets;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnsafeBareResourceController {

    @GetMapping("/unsafe-bare-resource")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public Resource unsafeBareResource(@RequestParam(required = false, defaultValue = "") String name) {
        return new ByteArrayResource(("<h1>hi " + name + "</h1>").getBytes(StandardCharsets.UTF_8));
    }
}

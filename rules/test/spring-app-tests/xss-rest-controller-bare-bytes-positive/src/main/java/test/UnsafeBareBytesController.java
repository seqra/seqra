package test;

import java.nio.charset.StandardCharsets;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + bare `byte[]` return — DEFAULT-DANGEROUS.
 *
 * `ByteArrayHttpMessageConverter` advertises the wildcard media type, so a browser's
 * Accept header (which ranks text/html ahead of the wildcard) lifts the response to
 * `Content-Type: text/html`. Tainted bytes get labeled as HTML and
 * the inline `<script>` executes. Empirically validated in
 * `/tmp/spring-content-type-probe/RESULTS.md`.
 */
@RestController
public class UnsafeBareBytesController {

    @GetMapping("/unsafe-bare-bytes")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public byte[] unsafeBareBytes(@RequestParam(required = false, defaultValue = "") String name) {
        return ("<h1>hi " + name + "</h1>").getBytes(StandardCharsets.UTF_8);
    }
}

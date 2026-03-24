package security.ssrf;

import java.io.InputStream;
import java.net.URL;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Extra SSRF sink samples for patterns not yet covered by other test files.
 */
public class SsrfExtraSinksSamples {

    // ── new URL(userInput).openStream() ─────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-extra")
    public static class UnsafeUrlOpenStreamController {

        @GetMapping("/open-stream")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> unsafeOpenStream(@RequestParam("url") String url) throws Exception {
            // VULNERABLE: taint on $UNTRUSTED in new URL constructor, then openStream
            InputStream is = new URL(url).openStream();
            String content = new String(is.readAllBytes());
            is.close();
            return ResponseEntity.ok(content);
        }
    }

    // ── new URL(userInput).getContent() ─────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-extra")
    public static class UnsafeUrlGetContentController {

        @GetMapping("/get-content")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> unsafeGetContent(@RequestParam("url") String url) throws Exception {
            // VULNERABLE: taint on $UNTRUSTED in new URL constructor, then getContent
            Object content = new URL(url).getContent();
            return ResponseEntity.ok(String.valueOf(content));
        }
    }

    // ── java.net.http.HttpClient.send(request, ...) ─────────────────────

    // TODO: Analyzer FN – taint does not propagate through HttpRequest.newBuilder chain;
    // re-enable when taint propagation summaries for HttpRequest.Builder are added.
    // @RestController
    // @RequestMapping("/ssrf-extra")
    // public static class UnsafeHttpClientSendController {
    //
    //     @GetMapping("/http-client-send")
    //     @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
    //     public ResponseEntity<String> unsafeSend(@RequestParam("url") String url) throws Exception {
    //         java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
    //                 .uri(java.net.URI.create(url))
    //                 .GET()
    //                 .build();
    //         java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    //         java.net.http.HttpResponse<String> resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    //         return ResponseEntity.ok(resp.body());
    //     }
    // }

    // ── Eclipse Jetty HttpClient.GET ────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-extra")
    public static class UnsafeJettyGetController {

        @GetMapping("/jetty-get")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> unsafeJettyGet(@RequestParam("url") String url) throws Exception {
            org.eclipse.jetty.client.HttpClient httpClient = new org.eclipse.jetty.client.HttpClient();
            // VULNERABLE: user-controlled URL passed directly to Jetty GET
            org.eclipse.jetty.client.api.ContentResponse response = httpClient.GET(url);
            return ResponseEntity.ok(response.getContentAsString());
        }
    }
}

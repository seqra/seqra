package security.ssrf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Samples for SSRF-related rules: servlet SSRF, Spring SSRF, and servlet parameter pollution.
 */
public class SsrfSamples {

    // ssrf-in-servlet-app

    @WebServlet("/ssrf/unsafe-proxy")
    public static class UnsafeProxyServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            // User controls full target URL
            String targetUrl = request.getParameter("url");
            if (targetUrl == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'url' parameter");
                return;
            }

            // VULNERABLE: directly using unvalidated user input as target URL
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            response.setStatus(conn.getResponseCode());
            try (InputStream in = conn.getInputStream();
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }

    @WebServlet("/ssrf/safe-proxy")
    public static class SafeProxyServlet extends HttpServlet {

        private static final Set<String> ALLOWED_HOSTS = Set.of(
                "api.example.com",
                "services.partner.com"
        );

        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String targetUrl = request.getParameter("url");
            if (targetUrl == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'url' parameter");
                return;
            }

            URI uri;
            try {
                uri = new URI(targetUrl);
            } catch (URISyntaxException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid URL");
                return;
            }

            String scheme = uri.getScheme();
            if (scheme == null ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported scheme");
                return;
            }

            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Host not allowed");
                return;
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Internal addresses are not allowed");
                return;
            }

            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            response.setStatus(status);

            InputStream in = (status >= 200 && status < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (in != null) {
                try (in; OutputStream out = response.getOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    // ssrf-in-spring-app

    @RestController
    @RequestMapping("/ssrf/proxy")
    public static class SsrfSpringController {

        private final RestTemplate restTemplate = new RestTemplate();

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> unsafeProxy(@RequestParam("url") String targetUrl) {
            if (targetUrl == null || targetUrl.isBlank()) {
                return ResponseEntity.badRequest().body("Missing 'url' parameter");
            }

            // VULNERABLE: directly using unvalidated user input as target URL
            String body = restTemplate.getForObject(targetUrl, String.class);
            return ResponseEntity.ok(body);
        }

        private static final Set<String> ALLOWED_SPRING_HOSTS = Set.of(
                "api.example.com",
                "services.partner.com"
        );

        @GetMapping("/safe")
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> safeProxy(@RequestParam("url") String targetUrl) {
            if (targetUrl == null || targetUrl.isBlank()) {
                return ResponseEntity.badRequest().body("Missing 'url' parameter");
            }

            URI uri;
            try {
                uri = new URI(targetUrl);
            } catch (URISyntaxException e) {
                return ResponseEntity.badRequest().body("Invalid URL");
            }

            String scheme = uri.getScheme();
            if (scheme == null ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return ResponseEntity.badRequest().body("Unsupported scheme");
            }

            String host = uri.getHost();
            if (host == null || !ALLOWED_SPRING_HOSTS.contains(host.toLowerCase())) {
                return ResponseEntity.status(403).body("Host not allowed");
            }

            try {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
                    return ResponseEntity.status(403).body("Internal addresses are not allowed");
                }
            } catch (UnknownHostException e) {
                return ResponseEntity.badRequest().body("Unable to resolve host");
            }

            String body = restTemplate.getForObject(uri, String.class);
            return ResponseEntity.ok(body);
        }
    }

    // java-servlet-parameter-pollution

    @WebServlet("/ssrf/parameter-pollution/unsafe")
    public static class UnsafeParameterPollutionServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String key = request.getParameter("key"); // untrusted

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // VULNERABLE: directly concatenate untrusted value into URL query string
                String url = "https://example.com/getId?key=" + key;
                HttpGet httpget = new HttpGet(url);
                try (CloseableHttpResponse clientResponse = httpClient.execute(httpget)) {
                    byte[] data = clientResponse.getEntity().getContent().readAllBytes();
                    response.getOutputStream().write(data);
                }
            }
        }
    }

    @WebServlet("/ssrf/parameter-pollution/safe")
    public static class SafeParameterPollutionServlet extends HttpServlet {

        private static final Map<String, String> LOOKUP_TABLE = new HashMap<>();

        static {
            LOOKUP_TABLE.put("key1", "value1");
            LOOKUP_TABLE.put("key2", "value2");
        }

        @Override
        @NegativeRuleSample(value = "java/security/ssrf.yaml", id = "java-servlet-parameter-pollution")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String key = request.getParameter("key");
            String value = LOOKUP_TABLE.getOrDefault(key, "value1");

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // SAFE: user-supplied key is resolved via lookup table; only mapped values are used
                String url = "https://example.com/getId?key=" + value;
                HttpGet httpget = new HttpGet(url);
                try (CloseableHttpResponse clientResponse = httpClient.execute(httpget)) {
                    byte[] data = clientResponse.getEntity().getContent().readAllBytes();
                    response.getOutputStream().write(data);
                }
            }
        }
    }
}

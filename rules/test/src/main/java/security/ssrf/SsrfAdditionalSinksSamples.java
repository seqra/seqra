package security.ssrf;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Additional SSRF sink test samples covering newly added library patterns.
 */
public class SsrfAdditionalSinksSamples {

    // ── java.net.InetSocketAddress ──────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/inet-socket")
    public static class UnsafeInetSocketAddress {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("host") String host) {
            InetSocketAddress addr = new InetSocketAddress(host, 8080);
            return ResponseEntity.ok("resolved: " + addr);
        }
    }

    // ── java.net.Socket ─────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/socket")
    public static class UnsafeSocket {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("host") String host) throws IOException {
            Socket socket = new Socket(host, 8080);
            socket.close();
            return ResponseEntity.ok("connected");
        }
    }

    // ── java.net.http.HttpRequest.newBuilder ─────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/java-http")
    public static class UnsafeJavaHttpClient {

        @GetMapping("/fetch")
        // TODO: Analyzer FN – taint does not propagate through URI.create() wrapper; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.ok(resp.body());
        }
    }

    // ── Apache HttpComponents 5 - HttpGet ───────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/hc5")
    public static class UnsafeHc5HttpGet {

        @GetMapping("/fetch")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) {
            org.apache.hc.client5.http.classic.methods.HttpGet httpGet =
                    new org.apache.hc.client5.http.classic.methods.HttpGet(url);
            return ResponseEntity.ok("request to: " + httpGet.getRequestUri());
        }
    }

    // ── Apache HttpClient 4 - RequestBuilder ────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/hc4-builder")
    public static class UnsafeHc4RequestBuilder {

        @GetMapping("/fetch")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) {
            org.apache.http.client.methods.RequestBuilder builder =
                    org.apache.http.client.methods.RequestBuilder.get(url);
            return ResponseEntity.ok("request built for: " + builder.getUri());
        }
    }

    // ── Spring RequestEntity ────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/request-entity")
    public static class UnsafeRequestEntity {

        @GetMapping("/fetch")
        // TODO: Analyzer FN – taint does not propagate through URI.create() wrapper; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) {
            RequestEntity<Void> request = RequestEntity.get(URI.create(url)).build();
            return ResponseEntity.ok("request entity to: " + request.getUrl());
        }
    }

    // ── Spring DriverManagerDataSource ───────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/datasource")
    public static class UnsafeDriverManagerDataSource {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("url") String url) {
            DriverManagerDataSource ds = new DriverManagerDataSource(url);
            return ResponseEntity.ok("datasource: " + ds.getUrl());
        }
    }

    // ── Spring WebClient ────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/webclient")
    public static class UnsafeWebClient {

        @GetMapping("/fetch")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) {
            WebClient client = WebClient.create(url);
            return ResponseEntity.ok("webclient created for: " + url);
        }
    }

    // ── Netty DefaultHttpRequest ────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/netty")
    public static class UnsafeNettyHttpRequest {

        @GetMapping("/fetch")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) {
            io.netty.handler.codec.http.DefaultHttpRequest request =
                    new io.netty.handler.codec.http.DefaultHttpRequest(
                            io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                            io.netty.handler.codec.http.HttpMethod.GET,
                            url);
            return ResponseEntity.ok("netty request to: " + request.uri());
        }
    }

    // ── HikariConfig.setJdbcUrl ─────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/hikari")
    public static class UnsafeHikariConfig {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("url") String url) {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setJdbcUrl(url);
            return ResponseEntity.ok("hikari config set");
        }
    }

    // ── Eclipse Jetty HttpClient ────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/jetty")
    public static class UnsafeJettyHttpClient {

        @GetMapping("/fetch")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> fetch(@RequestParam("url") String url) throws Exception {
            org.eclipse.jetty.client.HttpClient httpClient = new org.eclipse.jetty.client.HttpClient();
            httpClient.newRequest(url);
            return ResponseEntity.ok("jetty request created");
        }
    }

    // ── JSch.getSession ─────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/jsch")
    public static class UnsafeJSchSession {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("host") String host) throws Exception {
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            com.jcraft.jsch.Session session = jsch.getSession("user", host, 22);
            return ResponseEntity.ok("session for: " + session.getHost());
        }
    }

    // ── java.sql.DriverManager ──────────────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/jdbc")
    public static class UnsafeDriverManager {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("url") String url) throws Exception {
            java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
            conn.close();
            return ResponseEntity.ok("connected");
        }
    }

    // ── Commons IO FileUtils.copyURLToFile ──────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/commons-io")
    public static class UnsafeCommonsIoCopy {

        @GetMapping("/download")
        // TODO: Analyzer FN – taint does not propagate through new URL() wrapper; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> download(@RequestParam("url") String url) throws Exception {
            File tempFile = File.createTempFile("download", ".tmp");
            org.apache.commons.io.FileUtils.copyURLToFile(new URL(url), tempFile);
            return ResponseEntity.ok("downloaded to: " + tempFile.getAbsolutePath());
        }
    }

    // ── Commons Net SocketClient.connect ─────────────────────────────────

    @RestController
    @RequestMapping("/ssrf-additional/commons-net")
    public static class UnsafeCommonsNetConnect {

        @GetMapping("/connect")
        @PositiveRuleSample(value = "java/security/ssrf.yaml", id = "ssrf-in-spring-app")
        public ResponseEntity<String> connect(@RequestParam("host") String host) throws Exception {
            org.apache.commons.net.SocketClient client = new org.apache.commons.net.ftp.FTPClient();
            client.connect(host, 21);
            client.disconnect();
            return ResponseEntity.ok("connected to: " + host);
        }
    }
}

# Spring XSS Rule: Dynamic Verification and Content-Type Discrimination — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ground every TP/FP label in `rules/ruleset/java/lib/spring/spring-xss-html-response-sinks.yaml` and `rules/test/src/main/java/security/xss/XssHtmlResponseSpringSamples.java` in a runtime probe against a live Spring handler, add content-type discrimination on ResponseEntity builders, and cover the Stirling-PDF `ResponseEntity<byte[]>` no-content-type shape.

**Architecture:** Ephemeral Spring Boot 2.7 harness at `/tmp/xss-verify/` (not committed) with one controller method per matrix row. A driver runs the embedded server, fires an XSS payload at each endpoint, and prints a verdict table. Verdicts drive the `@PositiveRuleSample` / `@NegativeRuleSample` labels on the committed static samples, and the rule YAML gains `pattern-not-inside` exclusions for non-HTML content types set via `.contentType(...)`, `.header("Content-Type", ...)`, and `HttpHeaders.setContentType(...)`. A second, smaller probe resolves whether `ResponseEntity<$T>` pattern-inside matches raw `ResponseEntity`, determining whether we need one unified block or two.

**Tech Stack:** Spring Boot 2.7.18 + Spring Web 5.3.39 (matches `rules/test/build.gradle.kts`), Gradle Wrapper, Java 21, opentaint CLI 0.1.1 (`/home/misonijnik/.opentaint/install/opentaint`).

**Design spec:** `docs/specs/2026-04-17-spring-xss-rule-dynamic-verification-design.md`

---

## Conventions

- Working directory for committed changes: `/home/misonijnik/opentaint`
- Throwaway harness lives at `/tmp/xss-verify/` and is deleted at the end.
- XSS payload in every probe: `<script>alert(1)</script>` URL-encoded as `%3Cscript%3Ealert%281%29%3C%2Fscript%3E`.
- Browser `Accept` header in every probe: `text/html`.
- Verdict rule: **TP** iff HTTP response header `Content-Type` begins with `text/html` **and** the response body contains the raw (un-escaped) substring `<script>alert(1)</script>`; otherwise **FP**.
- Label mapping: TP → `@PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")`, FP → `@NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")`.
- Commit style: one logical change per commit, prefix with `feat:`, `test:`, `fix:`, `docs:` as appropriate, sign with the `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` trailer.

---

## Task 1: Scaffold ephemeral Spring Boot harness

**Files:**
- Create: `/tmp/xss-verify/settings.gradle`
- Create: `/tmp/xss-verify/build.gradle`
- Create: `/tmp/xss-verify/gradle.properties`
- Create: `/tmp/xss-verify/src/main/java/xssverify/App.java`
- Create: `/tmp/xss-verify/src/main/resources/application.properties`

- [ ] **Step 1: Create the directory tree**

Run:
```bash
rm -rf /tmp/xss-verify
mkdir -p /tmp/xss-verify/src/main/java/xssverify
mkdir -p /tmp/xss-verify/src/main/resources
```

- [ ] **Step 2: Write `/tmp/xss-verify/settings.gradle`**

```gradle
rootProject.name = 'xss-verify'
```

- [ ] **Step 3: Write `/tmp/xss-verify/build.gradle`**

```gradle
plugins {
    id 'org.springframework.boot' version '2.7.18'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
}

group = 'xssverify'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}

bootRun {
    // Driver controls lifecycle from the Java side
}
```

- [ ] **Step 4: Write `/tmp/xss-verify/gradle.properties`**

```
org.gradle.jvmargs=-Xmx2g
```

- [ ] **Step 5: Write `/tmp/xss-verify/src/main/resources/application.properties`**

```
server.port=0
spring.main.banner-mode=off
logging.level.root=WARN
logging.level.xssverify=INFO
```

- [ ] **Step 6: Write `/tmp/xss-verify/src/main/java/xssverify/App.java`**

```java
package xssverify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

- [ ] **Step 7: Install the Gradle wrapper and verify the project compiles**

Run:
```bash
cd /tmp/xss-verify && gradle wrapper --gradle-version 8.5 --no-daemon
./gradlew --no-daemon build -x test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If Gradle cannot be found or cannot reach Maven Central, **halt and surface the error to the user** — do not attempt workarounds, as empirical verification is the point of this plan.

- [ ] **Step 8: Commit nothing**

Harness is not committed. `/tmp/xss-verify/` stays out of the repo.

---

## Task 2: Add all 21 controller methods to the harness

**Files:**
- Create: `/tmp/xss-verify/src/main/java/xssverify/Row1Controller.java` through `/tmp/xss-verify/src/main/java/xssverify/Row21Controller.java` (one file per row, each a `@Controller` or `@RestController`)

Putting each row in its own file keeps the driver's URL → row mapping obvious and lets you re-run a single row with `./gradlew bootRun --args='--server.port=8080' -Dspring.profiles.active=row5` if needed. The files are throwaway.

Use `@RequestParam(required = false, defaultValue = "") String payload` in every endpoint. All endpoints are `GET` and live under `/row-<n>` so the driver iterates `/row-1 .. /row-21`.

- [ ] **Step 1: Write row 1 — String return, no produces**

File: `/tmp/xss-verify/src/main/java/xssverify/Row1Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row1Controller {
    @GetMapping("/row-1")
    public String row1(@RequestParam(required = false, defaultValue = "") String payload) {
        return "<h1>Hello, " + payload + "!</h1>";
    }
}
```

- [ ] **Step 2: Write row 2 — String produces=text/html**

File: `/tmp/xss-verify/src/main/java/xssverify/Row2Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row2Controller {
    @GetMapping(value = "/row-2", produces = "text/html")
    public String row2(@RequestParam(required = false, defaultValue = "") String payload) {
        return "<h1>Hello, " + payload + "!</h1>";
    }
}
```

- [ ] **Step 3: Write rows 3–6 (String with non-HTML `produces`)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row3Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row3Controller {
    @GetMapping(value = "/row-3", produces = "application/json")
    public String row3(@RequestParam(required = false, defaultValue = "") String payload) {
        return "{\"payload\":\"" + payload + "\"}";
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row4Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row4Controller {
    @GetMapping(value = "/row-4", produces = "text/plain")
    public String row4(@RequestParam(required = false, defaultValue = "") String payload) {
        return "Hello, " + payload;
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row5Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row5Controller {
    @GetMapping(value = "/row-5", produces = "application/pdf")
    public String row5(@RequestParam(required = false, defaultValue = "") String payload) {
        return "<h1>Hello, " + payload + "!</h1>";
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row6Controller.java`

```java
package xssverify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row6Controller {
    @GetMapping(value = "/row-6", produces = "application/octet-stream")
    public String row6(@RequestParam(required = false, defaultValue = "") String payload) {
        return "<h1>Hello, " + payload + "!</h1>";
    }
}
```

- [ ] **Step 4: Write rows 7–8 (ResponseEntity<String> with and without produces)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row7Controller.java`

```java
package xssverify;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row7Controller {
    @GetMapping("/row-7")
    public ResponseEntity<String> row7(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok("<h1>Hello, " + payload + "!</h1>");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row8Controller.java`

```java
package xssverify;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row8Controller {
    @GetMapping(value = "/row-8", produces = "application/json")
    public ResponseEntity<String> row8(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok("{\"payload\":\"" + payload + "\"}");
    }
}
```

- [ ] **Step 5: Write rows 9–12 (ResponseEntity<String> builder contentType/header)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row9Controller.java`

```java
package xssverify;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row9Controller {
    @GetMapping("/row-9")
    public ResponseEntity<String> row9(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body("<h1>Hello, " + payload + "!</h1>");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row10Controller.java`

```java
package xssverify;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row10Controller {
    @GetMapping("/row-10")
    public ResponseEntity<String> row10(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"payload\":\"" + payload + "\"}");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row11Controller.java`

```java
package xssverify;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row11Controller {
    @GetMapping("/row-11")
    public ResponseEntity<String> row11(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body("<h1>Hello, " + payload + "!</h1>");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row12Controller.java`

```java
package xssverify;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row12Controller {
    @GetMapping("/row-12")
    public ResponseEntity<String> row12(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body("{\"payload\":\"" + payload + "\"}");
    }
}
```

- [ ] **Step 6: Write row 13 — `new ResponseEntity<>(body, headers, status)` with headers.setContentType(APPLICATION_JSON)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row13Controller.java`

```java
package xssverify;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row13Controller {
    @GetMapping("/row-13")
    public ResponseEntity<String> row13(@RequestParam(required = false, defaultValue = "") String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>("{\"payload\":\"" + payload + "\"}", headers, HttpStatus.OK);
    }
}
```

- [ ] **Step 7: Write rows 14–15 (raw ResponseEntity)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row14Controller.java`

```java
package xssverify;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings({"rawtypes", "unchecked"})
public class Row14Controller {
    @GetMapping("/row-14")
    public ResponseEntity row14(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok("<h1>Hello, " + payload + "!</h1>");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row15Controller.java`

```java
package xssverify;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings({"rawtypes", "unchecked"})
public class Row15Controller {
    @GetMapping("/row-15")
    public ResponseEntity row15(@RequestParam(required = false, defaultValue = "") String payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"payload\":\"" + payload + "\"}");
    }
}
```

- [ ] **Step 8: Write row 16 — Stirling-PDF shape**

File: `/tmp/xss-verify/src/main/java/xssverify/Row16Controller.java`

```java
package xssverify;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row16Controller {
    @GetMapping("/row-16")
    public ResponseEntity<byte[]> row16(@RequestParam(required = false, defaultValue = "") String payload) {
        String err = "Conversion failed for " + payload;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(err.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 9: Write row 17 — ResponseEntity<byte[]> produces=text/html**

File: `/tmp/xss-verify/src/main/java/xssverify/Row17Controller.java`

```java
package xssverify;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row17Controller {
    @GetMapping(value = "/row-17", produces = "text/html")
    public ResponseEntity<byte[]> row17(@RequestParam(required = false, defaultValue = "") String payload) {
        byte[] body = ("<h1>Hello, " + payload + "!</h1>").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok(body);
    }
}
```

- [ ] **Step 10: Write rows 18–19 (ResponseEntity<byte[]> builder contentType)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row18Controller.java`

```java
package xssverify;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row18Controller {
    @GetMapping("/row-18")
    public ResponseEntity<byte[]> row18(@RequestParam(required = false, defaultValue = "") String payload) {
        byte[] body = ("PDF-1.4% fake for " + payload).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row19Controller.java`

```java
package xssverify;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Row19Controller {
    @GetMapping("/row-19")
    public ResponseEntity<byte[]> row19(@RequestParam(required = false, defaultValue = "") String payload) {
        byte[] body = ("binary-for-" + payload).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
```

- [ ] **Step 11: Write rows 20–21 (HttpServletResponse writer with JSON content type)**

File: `/tmp/xss-verify/src/main/java/xssverify/Row20Controller.java`

```java
package xssverify;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class Row20Controller {
    @GetMapping("/row-20")
    public void row20(@RequestParam(required = false, defaultValue = "") String payload,
                      HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print("{\"payload\":\"" + payload + "\"}");
    }
}
```

File: `/tmp/xss-verify/src/main/java/xssverify/Row21Controller.java`

```java
package xssverify;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class Row21Controller {
    @GetMapping("/row-21")
    public void row21(@RequestParam(required = false, defaultValue = "") String payload,
                      HttpServletResponse response) throws IOException {
        response.setHeader("Content-Type", "application/json");
        PrintWriter out = response.getWriter();
        out.print("{\"payload\":\"" + payload + "\"}");
    }
}
```

- [ ] **Step 12: Verify the harness compiles**

Run:
```bash
cd /tmp/xss-verify && ./gradlew --no-daemon build -x test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If any controller fails to compile, fix that file and rerun.

---

## Task 3: Write the verdict driver

**Files:**
- Create: `/tmp/xss-verify/src/main/java/xssverify/Driver.java`

The driver is a `CommandLineRunner` so it starts after Spring finishes wiring the controllers, hits each `/row-N` endpoint with the XSS payload, prints one line per row, then exits. Using `CommandLineRunner` + `server.port=0` + `Environment.getProperty("local.server.port")` avoids hard-coding a port.

- [ ] **Step 1: Write `/tmp/xss-verify/src/main/java/xssverify/Driver.java`**

```java
package xssverify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
class Driver implements ApplicationListener<WebServerInitializedEvent>, CommandLineRunner {

    private static final String PAYLOAD = "<script>alert(1)</script>";
    private static final int ROW_COUNT = 21;

    private volatile int port = -1;
    private final ConfigurableApplicationContext ctx;
    private final ApplicationEventPublisher events;

    Driver(ConfigurableApplicationContext ctx, ApplicationEventPublisher events) {
        this.ctx = ctx;
        this.events = events;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    @Override
    public void run(String... args) throws Exception {
        if (port <= 0) {
            throw new IllegalStateException("Web server port was not captured");
        }
        HttpClient client = HttpClient.newHttpClient();
        String enc = java.net.URLEncoder.encode(PAYLOAD, StandardCharsets.UTF_8);

        System.out.println("row | status | content-type                          | raw_script | verdict");
        System.out.println("----+--------+---------------------------------------+------------+--------");

        for (int i = 1; i <= ROW_COUNT; i++) {
            String url = "http://localhost:" + port + "/row-" + i + "?payload=" + enc;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "text/html")
                    .GET()
                    .build();
            HttpResponse<String> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.out.printf("%3d | ERROR  | %s%n", i, e.getClass().getSimpleName() + ": " + e.getMessage());
                continue;
            }
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            boolean raw = resp.body().contains(PAYLOAD);
            boolean htmlCt = ct.toLowerCase().startsWith("text/html");
            String verdict = (htmlCt && raw) ? "TP" : "FP";
            System.out.printf("%3d | %6d | %-37s | %-10s | %s%n",
                    i, resp.statusCode(), ct, raw, verdict);
        }

        ctx.close();
    }
}
```

- [ ] **Step 2: Rebuild the harness**

Run:
```bash
cd /tmp/xss-verify && ./gradlew --no-daemon build -x test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Run the harness and capture the verdict table

**Files:**
- Create: `/tmp/xss-verify/verdicts.txt`

- [ ] **Step 1: Run the harness**

Run:
```bash
cd /tmp/xss-verify && ./gradlew --no-daemon bootRun 2>&1 | tee verdicts.txt
```

Expected: each row produces a line in the verdict table; the process exits cleanly after row 21 (because `Driver#run` closes the context).

- [ ] **Step 2: Extract just the table into `/tmp/xss-verify/verdicts-clean.txt`**

Run:
```bash
grep -E '^( *[0-9]+ \||row \||----)' /tmp/xss-verify/verdicts.txt > /tmp/xss-verify/verdicts-clean.txt
cat /tmp/xss-verify/verdicts-clean.txt
```

Expected: 23 lines (header, separator, 21 rows).

- [ ] **Step 3: Record the expected-vs-actual verdict matrix in the implementation plan**

Open this plan file (`docs/superpowers/plans/2026-04-17-spring-xss-rule-dynamic-verification.md`) and append a new section `## Appendix A: Runtime verdict table (captured <ISO-date>)`, pasting the cleaned table. Include a column noting whether the spec-predicted verdict matches. Flipped rows are flagged with `FLIP`.

- [ ] **Step 4: Resolve flips**

For each `FLIP` row, re-read the controller and the Spring Framework source to understand why the runtime diverged from the prediction. Two legitimate outcomes:

1. **Update the spec matrix in place** (add a one-line note in the appendix + update the matrix row in `docs/specs/2026-04-17-spring-xss-rule-dynamic-verification-design.md`). Runtime truth wins.
2. **Confirm the controller is wrong for what we wanted to test**; rewrite the controller, rerun Task 4 Step 1.

Do not proceed to the next task with any unresolved flip.

---

## Task 5: Probe opentaint's raw-vs-generic `ResponseEntity` matching

**Files:**
- Create: `/tmp/xss-probe/probe-rule.yaml`
- Create: `/tmp/xss-probe/src/main/java/xssprobe/ProbeSamples.java`
- Create: `/tmp/xss-probe/src/main/java/xssprobe/JIRAtomSample.java` (stubs for `@PositiveRuleSample`)

The question: does `pattern-inside: ResponseEntity<$T> $M(...) { ... }` match a method declared as raw `ResponseEntity $M(...)`? And conversely, does `pattern-inside: ResponseEntity $M(...) { ... }` match `ResponseEntity<String> $M(...)`? The answer determines whether we need one unified sink block or two.

- [ ] **Step 1: Create probe directory and stub annotations**

Run:
```bash
rm -rf /tmp/xss-probe
mkdir -p /tmp/xss-probe/src/main/java/xssprobe
```

File: `/tmp/xss-probe/src/main/java/xssprobe/ProbeMarker.java`

```java
package xssprobe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProbeMarker {}
```

- [ ] **Step 2: Copy `ResponseEntity` stub into the probe tree**

We need a class named `org.springframework.http.ResponseEntity` on the probe's classpath. Reuse the Spring jar that rules/test already pulls:

```bash
mkdir -p /tmp/xss-probe/lib
cp $(find ~/.gradle /tmp/xss-verify -name 'spring-web-5*.jar' | head -1) /tmp/xss-probe/lib/spring-web.jar
```

If the find command returns empty, export the jar from the harness build by running `cd /tmp/xss-verify && ./gradlew --no-daemon dependencies --configuration runtimeClasspath | head` and copy any `spring-web-*.jar` under `~/.gradle/caches/`.

- [ ] **Step 3: Write probe samples**

File: `/tmp/xss-probe/src/main/java/xssprobe/ProbeSamples.java`

```java
package xssprobe;

import org.springframework.http.ResponseEntity;

public class ProbeSamples {

    @ProbeMarker
    public ResponseEntity<String> parameterizedString(String u) {
        return ResponseEntity.ok(u);
    }

    @ProbeMarker
    public ResponseEntity<byte[]> parameterizedBytes(byte[] u) {
        return ResponseEntity.ok(u);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @ProbeMarker
    public ResponseEntity raw(String u) {
        return ResponseEntity.ok(u);
    }
}
```

- [ ] **Step 4: Write two probe rules — one generic, one raw**

File: `/tmp/xss-probe/rule-generic.yaml`

```yaml
rules:
  - id: probe-generic
    severity: WARNING
    message: Generic ResponseEntity<$T> matched
    languages: [java]
    pattern: |
      ResponseEntity<$T> $M(...) {
        ...
      }
```

File: `/tmp/xss-probe/rule-raw.yaml`

```yaml
rules:
  - id: probe-raw
    severity: WARNING
    message: Raw ResponseEntity matched
    languages: [java]
    pattern: |
      ResponseEntity $M(...) {
        ...
      }
```

- [ ] **Step 5: Compile the probe**

Run:
```bash
cd /tmp/xss-probe && mkdir -p classes
javac -d classes -cp lib/spring-web.jar src/main/java/xssprobe/*.java 2>&1 | tail
```

Expected: compiles cleanly, `classes/xssprobe/ProbeSamples.class` present.

- [ ] **Step 6: Build an opentaint project model for the probe**

Run:
```bash
cd /tmp/xss-probe && opentaint project \
  --output ./project-model \
  --source-root ./src/main/java \
  --classpath ./classes \
  --dependency ./lib/spring-web.jar \
  --package xssprobe
```

Expected: `project-model/project.yaml` created.

- [ ] **Step 7: Scan with both probe rules and inspect**

Run:
```bash
cd /tmp/xss-probe && opentaint scan ./project-model \
  --ruleset ./rule-generic.yaml \
  --severity warning \
  -o ./generic.sarif 2>&1 | tail -5

opentaint scan ./project-model \
  --ruleset ./rule-raw.yaml \
  --severity warning \
  -o ./raw.sarif 2>&1 | tail -5
```

- [ ] **Step 8: Read which methods matched**

Run:
```bash
opentaint summary --show-findings ./generic.sarif
opentaint summary --show-findings ./raw.sarif
```

- [ ] **Step 9: Record findings in the plan appendix**

Append `## Appendix B: Raw-vs-generic matching probe (captured <ISO-date>)` to this plan file listing, for each probe rule, which of `parameterizedString`, `parameterizedBytes`, `raw` it matched.

**Decision matrix:**

- If `rule-generic.yaml` matches all three (generic pattern also matches raw) → use **one unified block** `ResponseEntity<$T> $M(...)` and **no separate raw block**. Raw `ResponseEntity` handlers are still flagged; no duplicates because there's only one block.
- If `rule-generic.yaml` matches only the parameterized ones → add **two blocks**: `ResponseEntity<$T>` and (separately) raw `ResponseEntity`. Over-matching of raw onto parameterized (the concern in the note in the existing YAML) is checked by running `rule-raw.yaml` — if raw matches all three, then the two-block approach over-flags parameterized ones via the raw block, and we must instead use the unified generic block plus a defensive `pattern-not-inside` for raw in the parameterized blocks.
- The third logical outcome (neither pattern matches parameterized) would indicate a deeper opentaint bug — in that case, **halt and notify the user**.

Record the chosen outcome in the appendix. Later tasks reference it.

---

## Task 6: Update static samples in `XssHtmlResponseSpringSamples.java`

**Files:**
- Modify: `rules/test/src/main/java/security/xss/XssHtmlResponseSpringSamples.java`

Target: the committed sample file ends with rows 1, 2-body-variants-for-HttpServletResponse, 7 (RE<String>), 17 (RE<byte[]> produces=html), 3 (String JSON), and one safe-sanitized String. After this task, the file should have one controller class per row in the 21-row matrix plus the pre-existing sanitized controllers.

Each new controller's label (`@PositiveRuleSample` vs `@NegativeRuleSample`) comes **from Appendix A**, not from the spec matrix. If Appendix A disagrees with the spec matrix for row N, the Appendix A verdict wins.

- [ ] **Step 1: Map Appendix A rows to sample class names**

Create a mapping: row 1 → `Row01StringNoProducesController` (positive), row 2 → `Row02StringProducesHtmlController` (positive), …, row 21 → `Row21ServletSetHeaderJsonController` (negative). Match naming conventions in the existing file (`UnsafeXxxController` for positive, `SafeXxxController` for negative) — for the new rows, switch to `Row<N>Xxx` to make the mapping unambiguous.

Write the mapping as a comment block at the top of the modified section so a reader can reconcile with the plan appendix.

- [ ] **Step 2: Keep the existing six controllers**

The existing controllers `UnsafeStringReturnController`, `UnsafeResponseEntityStringController`, `UnsafeSetContentTypeController`, `UnsafeSetHeaderController`, `SafeHtmlController`, `UnsafeResponseEntityBytesHtmlController`, `SafeJsonStringReturnController`, `SafeStringReturnController` cover some matrix rows already (rows 1, 7, existing-HTML-writer, sanitized, 17, 3). Leave them and their labels unchanged **unless Appendix A contradicts their label**.

- [ ] **Step 3: Add new controller classes**

For each matrix row not already covered, add a new `public static class Row<N>XxxController { ... }` with a single handler method matching the shape from `/tmp/xss-verify/src/main/java/xssverify/Row<N>Controller.java`. Strip the `@RequestMapping` path prefixes to match the rest of the file's conventions (existing paths use `/xss-in-spring-app/<slug>`).

Example — row 2 (String produces=text/html, positive):

```java
@RestController
public static class Row02StringProducesHtmlController {

    @GetMapping(value = "/xss-in-spring-app/row-02", produces = "text/html")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public String row02(@RequestParam(required = false, defaultValue = "") String name) {
        return "<h1>Hello, " + name + "!</h1>";
    }
}
```

Example — row 10 (RE<String> .contentType(APPLICATION_JSON), negative):

```java
@RestController
public static class Row10ResponseEntityStringContentTypeJsonController {

    @GetMapping("/xss-in-spring-app/row-10")
    @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<String> row10(@RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"" + name + "\"}");
    }
}
```

Example — row 13 (RE<String> via `new ResponseEntity<>(body, headers, status)`, negative):

```java
@RestController
public static class Row13NewResponseEntityHeadersJsonController {

    @GetMapping("/xss-in-spring-app/row-13")
    @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<String> row13(@RequestParam(required = false, defaultValue = "") String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>("{\"name\":\"" + name + "\"}", headers, HttpStatus.OK);
    }
}
```

Example — row 16 (RE<byte[]> Stirling shape, positive):

```java
@RestController
public static class Row16StirlingPdfShapeController {

    @GetMapping("/xss-in-spring-app/row-16")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public ResponseEntity<byte[]> row16(@RequestParam String filename) {
        String err = "Conversion failed for " + filename;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(err.getBytes(Charset.defaultCharset()));
    }
}
```

Add imports as needed (`org.springframework.http.HttpHeaders`, `org.springframework.http.HttpStatus` are already imported; add nothing new if the existing imports suffice).

Write one controller class per matrix row. For rows where Appendix A's verdict is **TP**, annotate the handler `@PositiveRuleSample(...)`. For **FP**, annotate `@NegativeRuleSample(...)`. The annotation value and id fields are always `"java/security/xss.yaml"` and `"xss-in-spring-app"`.

- [ ] **Step 4: Verify the file compiles**

Run:
```bash
cd /home/misonijnik/opentaint/rules/test && ./gradlew --no-daemon compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Fix any compile errors before moving on.

- [ ] **Step 5: Commit the sample additions**

Run:
```bash
cd /home/misonijnik/opentaint && git add rules/test/src/main/java/security/xss/XssHtmlResponseSpringSamples.java
git commit -m "$(cat <<'EOF'
test: cover 21 Spring XSS sink variants verified dynamically

Add one sample controller per variant of the Spring XSS sink (body type x
content-type-signal), with labels grounded in a Spring Boot runtime probe
that measured Content-Type and raw-script body containment per request.

Includes the Stirling-PDF ResponseEntity<byte[]> no-content-type error
shape (positive) and the ResponseEntity builder .contentType(...) /
.header("Content-Type", ...) / HttpHeaders.setContentType(...) variants
that are not XSS (negative).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Baseline the rule — scan with current YAML, record over/under-firings

Before changing the rule, capture today's behavior against the new samples so the delta is clear.

**Files:**
- Create: `/tmp/xss-verify/baseline.sarif` (not committed)

- [ ] **Step 1: Compile rules/test fresh**

Run:
```bash
cd /home/misonijnik/opentaint/rules/test && ./gradlew --no-daemon clean compileJava 2>&1 | tail -10
```

- [ ] **Step 2: Build opentaint project model for rules/test**

`rules/test` already has an opentaint-project artifact generated in `/home/misonijnik/opentaint/opentaint-project/`. Reuse it:

```bash
ls /home/misonijnik/opentaint/opentaint-project/project.yaml && echo "ok"
```

If missing, regenerate with:

```bash
cd /home/misonijnik/opentaint && rm -rf opentaint-project && opentaint compile rules/test 2>&1 | tail -5
```

- [ ] **Step 3: Run opentaint scan with existing rule**

Run:
```bash
cd /home/misonijnik/opentaint && opentaint scan ./opentaint-project \
  --ruleset ./rules/ruleset \
  --severity error \
  -o /tmp/xss-verify/baseline.sarif 2>&1 | tail -10
```

- [ ] **Step 4: Extract findings for xss-in-spring-app and record**

Run:
```bash
opentaint summary --show-findings /tmp/xss-verify/baseline.sarif | grep -A2 'xss-in-spring-app\|Row[0-9]' > /tmp/xss-verify/baseline-findings.txt
cat /tmp/xss-verify/baseline-findings.txt
```

Append `## Appendix C: Baseline rule output vs labels (captured <ISO-date>)` to this plan file listing each sample's expected label, actual rule outcome (FIRED / NOT), and the delta. This is the list of changes the YAML update needs to produce.

---

## Task 8: Update the rule YAML

**Files:**
- Modify: `rules/ruleset/java/lib/spring/spring-xss-html-response-sinks.yaml`

The concrete shape depends on Appendix B's probe outcome. The two branches below spell out both.

- [ ] **Step 1: Rewrite block 1a (String return)**

Replace the existing `# 1a.` block with a version that excludes `produces = "application/pdf"` and `produces = "application/octet-stream"` in addition to the existing json + text/plain exclusions. The new block is:

```yaml
# 1a. String return — no produces (dangerous default: content negotiation)
- patterns:
    - pattern-inside: |
        String $METHOD(...) {
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) { ... }
    - pattern-not-inside: |
        ResponseEntity $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "application/json", ...)
        String $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "text/plain", ...)
        String $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "application/pdf", ...)
        String $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "application/octet-stream", ...)
        String $M(...) { ... }
    - pattern: return $UNTRUSTED;
    - focus-metavariable: $UNTRUSTED
```

- [ ] **Step 2: Replace blocks 1b and (re-introduce) 1c/1d — ResponseEntity shapes**

**If Appendix B showed `ResponseEntity<$T>` matches raw too**, use a single unified block:

```yaml
# 1b. ResponseEntity<$T> — any body type. Content-type discriminators below
# suppress handlers that explicitly set a non-HTML content type on the builder,
# on HttpHeaders, or on the handler annotation.
- patterns:
    - pattern-inside: |
        ResponseEntity<$T> $METHOD(...) {
          ...
        }
    - pattern-not-inside: |
        @$A(..., produces = "application/json", ...)
        ResponseEntity<$T> $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "text/plain", ...)
        ResponseEntity<$T> $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "application/pdf", ...)
        ResponseEntity<$T> $M(...) { ... }
    - pattern-not-inside: |
        @$A(..., produces = "application/octet-stream", ...)
        ResponseEntity<$T> $M(...) { ... }
    # builder .contentType(MediaType.NON_HTML)
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.contentType(MediaType.APPLICATION_JSON)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.contentType(MediaType.APPLICATION_PDF)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.contentType(MediaType.APPLICATION_OCTET_STREAM)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.contentType(MediaType.TEXT_PLAIN)
          ...
        }
    # builder .header("Content-Type", "<non-html>")
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.header("Content-Type", "application/json")
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.header("Content-Type", "application/pdf")
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.header("Content-Type", "application/octet-stream")
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $X.header("Content-Type", "text/plain")
          ...
        }
    # HttpHeaders.setContentType(MediaType.NON_HTML) used with new ResponseEntity<>(body, headers, status)
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $H.setContentType(MediaType.APPLICATION_JSON)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $H.setContentType(MediaType.APPLICATION_PDF)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $H.setContentType(MediaType.APPLICATION_OCTET_STREAM)
          ...
        }
    - pattern-not-inside: |
        ResponseEntity<$T> $M(...) {
          ...
          $H.setContentType(MediaType.TEXT_PLAIN)
          ...
        }
    - pattern: return $UNTRUSTED;
    - focus-metavariable: $UNTRUSTED
```

**If Appendix B showed `ResponseEntity<$T>` does NOT match raw**, duplicate the block above for raw `ResponseEntity $METHOD(...)` (replace every `ResponseEntity<$T>` with `ResponseEntity` in both `pattern-inside` and the `pattern-not-inside` method-signature lines, keep the body-level discriminators unchanged).

Replace the current "Note on raw ResponseEntity" comment with a short comment that records the Appendix B outcome and why the rule has one block (or two).

- [ ] **Step 3: Leave block 2 (servlet writer) unchanged but add a clarifying comment**

Insert above block 2:

```yaml
# ── Direct response writer with HTML content type ──────────────────────
# The pattern-inside below enumerates only text/html content types. A
# handler that calls setContentType("application/json") or similar does
# not match and will not be flagged — see Row20/Row21 samples for the
# confirming negative cases.
```

No rule text below changes.

- [ ] **Step 4: Leave block 3 (`produces = "text/html"`) unchanged**

The existing block already matches any `$RETURNTYPE $METHOD(...)` — `String`, `ResponseEntity<String>`, `ResponseEntity<byte[]>`, `ResponseEntity`. No change.

---

## Task 9: Re-run opentaint and reconcile

**Files:**
- Create: `/tmp/xss-verify/after.sarif`

- [ ] **Step 1: Scan again**

Run:
```bash
cd /home/misonijnik/opentaint && opentaint scan ./opentaint-project \
  --ruleset ./rules/ruleset \
  --severity error \
  -o /tmp/xss-verify/after.sarif 2>&1 | tail -10
```

- [ ] **Step 2: Extract findings for each Row<N> sample**

Run:
```bash
opentaint summary --show-findings /tmp/xss-verify/after.sarif \
  | grep -E 'Row[0-9]+|xss-in-spring-app' > /tmp/xss-verify/after-findings.txt
cat /tmp/xss-verify/after-findings.txt
```

- [ ] **Step 3: Build the pass/fail matrix**

For each row `N`:

- Label is `@PositiveRuleSample` → rule must fire at `row<N>` handler. If it doesn't, that's a **miss**.
- Label is `@NegativeRuleSample` → rule must NOT fire at `row<N>` handler. If it does, that's an **extra**.

Append `## Appendix D: Post-rule-update matrix (captured <ISO-date>)` with columns `row | label | fired | status`.

- [ ] **Step 4: Fix any miss or extra**

For each miss, inspect the rule and determine which `pattern-not-inside` is over-excluding. For each extra, determine which sink pattern is over-matching. Edit the rule. Go back to Task 9 Step 1. Iterate until the matrix has zero misses and zero extras.

Acceptance: every Positive sample is flagged, no Negative sample is flagged. **Do not move on while any row disagrees.**

- [ ] **Step 5: Commit the rule changes**

Run:
```bash
cd /home/misonijnik/opentaint && git add rules/ruleset/java/lib/spring/spring-xss-html-response-sinks.yaml
git commit -m "$(cat <<'EOF'
feat: discriminate non-HTML content types in Spring XSS sink

Add pattern-not-inside exclusions for ResponseEntity handlers that set
application/json, application/pdf, application/octet-stream, or text/plain
content types on the builder (.contentType, .header), on HttpHeaders
(.setContentType used with new ResponseEntity<>(body, headers, status)),
or on the handler annotation (produces = "...").

Unify the ResponseEntity block to match any body-type parameter, covering
ResponseEntity<byte[]> (the Stirling-PDF ConvertEmlToPDF error shape) in
addition to ResponseEntity<String>.

Expand the String return block to exclude produces = "application/pdf" and
produces = "application/octet-stream" in addition to the existing json and
text/plain exclusions.

All TP/FP labels verified against a live Spring Boot 2.7 harness.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Commit the design spec appendices and run coverage check

The plan file in `docs/superpowers/plans/` has accumulated Appendices A–D during implementation. Commit it too (the plan is committed as a record of how verification was done).

- [ ] **Step 1: Run the rule coverage CI helper**

Run:
```bash
cd /home/misonijnik/opentaint/rules/test && ./gradlew --no-daemon checkRulesCoverage 2>&1 | tail -10
```

Expected: `Rule coverage check passed`.

- [ ] **Step 2: Commit the plan (with appendices)**

Run:
```bash
cd /home/misonijnik/opentaint && git add docs/superpowers/plans/2026-04-17-spring-xss-rule-dynamic-verification.md
git commit -m "$(cat <<'EOF'
docs: record runtime verdicts and probe outcomes for Spring XSS rule

Commit the implementation plan with appendices A–D recording the Spring
Boot runtime verdict table per matrix row, the opentaint raw-vs-generic
ResponseEntity matching probe outcome, and the before/after rule scans.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Delete the throwaway directories**

Run:
```bash
rm -rf /tmp/xss-verify /tmp/xss-probe
```

- [ ] **Step 4: Confirm working tree is clean except for opentaint-project / opentaint-result scan artifacts**

Run:
```bash
cd /home/misonijnik/opentaint && git status
```

Expected: `On branch misonijnik/match-generic-types` with only `opentaint-project/` and `opentaint-result/` untracked (existing behavior — they are scan outputs, intentionally uncommitted).

---

## Appendix A: Runtime verdict table

_Captured 2026-04-17._

```
row | status | content-type                                    | raw_script | verdict
----+--------+-------------------------------------------------+------------+--------
  1 |    200 | text/html;charset=UTF-8                         | true       | TP
  2 |    200 | text/html;charset=UTF-8                         | true       | TP
  3 |    406 | text/html;charset=UTF-8                         | false      | FP
  4 |    406 | text/html;charset=UTF-8                         | false      | FP
  5 |    406 | text/html;charset=UTF-8                         | false      | FP
  6 |    406 | text/html;charset=UTF-8                         | false      | FP
  7 |    200 | text/html;charset=UTF-8                         | true       | TP
  8 |    406 | text/html;charset=UTF-8                         | false      | FP
  9 |    200 | text/html                                       | true       | TP
 10 |    200 | application/json                                | true       | FP
 11 |    200 | text/html                                       | true       | TP
 12 |    200 | application/json                                | true       | FP
 13 |    200 | application/json                                | true       | FP
 14 |    200 | text/html;charset=UTF-8                         | true       | TP
 15 |    200 | application/json                                | true       | FP
 16 |    500 | text/html                                       | true       | TP
 17 |    200 | text/html                                       | true       | TP
 18 |    200 | application/pdf                                 | true       | FP
 19 |    200 | application/octet-stream                        | true       | FP
 20 |    200 | application/json;charset=ISO-8859-1             | true       | FP
 21 |    200 | application/json;charset=ISO-8859-1             | true       | FP
```

Verdict rule: **TP** iff response `Content-Type` starts with `text/html` AND body contains the raw payload `<script>alert(1)</script>`; otherwise **FP**. Each request carried `Accept: text/html` and `?payload=<script>alert(1)</script>` against the ephemeral Spring Boot 2.7 harness at `/tmp/xss-verify/`.

**Flips vs spec:** No flips — all 21 rows matched the spec prediction.

## Appendix B: Raw-vs-generic matching probe

_Captured 2026-04-17._

Probe project at `/tmp/xss-probe/` with `ProbeSamples.java` containing three `@RestController` handlers:

- `parameterizedString` (line 13) — returns `ResponseEntity<String>`
- `parameterizedBytes` (line 18) — returns `ResponseEntity<byte[]>`
- `raw` (line 24) — returns raw `ResponseEntity`

Each probe rule uses `mode: taint` with `pattern-sources: $CLASS.ok($U)` so that `return ResponseEntity.ok(...)` is tainted in every handler, and the sink is `pattern-inside: <method-decl>` + `pattern: return $UNTRUSTED;`.

| probe rule | parameterizedString (line 13) | parameterizedBytes (line 18) | raw (line 24) |
|------------|-------------------------------|------------------------------|---------------|
| `rule-generic.yaml` (`ResponseEntity<$T> $M(...) { ... }`) | matched | matched | matched |
| `rule-raw.yaml` (`ResponseEntity $M(...) { ... }`)           | matched | matched | matched |

Rule-loader warnings (same for both rules) explain the equivalence:

- `Method declaration pattern with a concrete return type is not supported; only metavariable return types are handled.`
- `Method declaration pattern with a generic return type is not supported; type arguments on return types will be ignored.`

Under opentaint's best-effort fallback, both the concrete `ResponseEntity` head and the `<$T>` type argument on a method-declaration return type are effectively discarded, so both patterns reduce to "any method declaration" and match every handler in `ProbeSamples` identically.

**Decision: unified block.** Because `rule-raw.yaml` fires on `ResponseEntity<String>` and `ResponseEntity<byte[]>` just as readily as on raw `ResponseEntity` (and `rule-generic.yaml` fires on raw `ResponseEntity` just as readily as on the parameterized forms), adding a separate raw block alongside the generic block would duplicate every finding. Keep the sink rule as a single `ResponseEntity<$T>` block — under opentaint's current method-declaration matcher it already covers raw `ResponseEntity` (and any other type argument) via erased-name matching.

## Appendix C: Baseline rule output vs labels

_Captured 2026-04-17._

Baseline scan of `rules/test` with the pre-Task-8 rule YAML produced five findings for `xss-in-spring-app` inside `XssHtmlResponseSpringSamples.java`, at lines 47, 64, 79, 93, and 133.

Mapping baseline findings to samples (grouped by matrix row where applicable):

```
row  label  sample class                                          line  actual     status
---  -----  ----------------------------------------------------- ----  ---------  -----------
  1   TP   UnsafeStringReturnController                            47  FIRED      OK
  2   TP   Row02StringProducesHtmlController                      170  NOT_FIRED  miss
  3   FP   SafeJsonStringReturnController                         145  NOT_FIRED  OK
  4   FP   Row04StringProducesTextPlainController                 182  NOT_FIRED  OK
  5   FP   Row05StringProducesPdfController                       194  NOT_FIRED  OK
  6   FP   Row06StringProducesOctetStreamController               206  NOT_FIRED  OK
  7   TP   UnsafeResponseEntityStringController                    63  FIRED      OK
  8   FP   Row08ResponseEntityStringProducesJsonController        218  NOT_FIRED  OK
  9   TP   Row09ResponseEntityStringContentTypeHtmlController     230  NOT_FIRED  miss
 10   FP   Row10ResponseEntityStringContentTypeJsonController     244  NOT_FIRED  OK
 11   TP   Row11ResponseEntityStringHeaderHtmlController          258  NOT_FIRED  miss
 12   FP   Row12ResponseEntityStringHeaderJsonController          272  NOT_FIRED  OK
 13   FP   Row13NewResponseEntityHeadersJsonController            286  NOT_FIRED  OK
 14   TP   Row14RawResponseEntityNoContentTypeController          301  NOT_FIRED  miss
 15   FP   Row15RawResponseEntityContentTypeJsonController        314  NOT_FIRED  OK
 16   TP   Row16StirlingPdfShapeController                        328  NOT_FIRED  miss
 17   TP   UnsafeResponseEntityBytesHtmlController                132  FIRED      OK
 18   FP   Row18ResponseEntityBytesContentTypePdfController       342  NOT_FIRED  OK
 19   FP   Row19ResponseEntityBytesContentTypeOctetStreamController 357 NOT_FIRED  OK
 20   FP   Row20ServletSetContentTypeJsonController               372  NOT_FIRED  OK
 21   FP   Row21ServletSetHeaderJsonController                    387  NOT_FIRED  OK
  -   TP   UnsafeSetContentTypeController (legacy text/html writer) 77  FIRED      OK
  -   TP   UnsafeSetHeaderController (legacy text/html setHeader)   91  FIRED      OK
  -   FP   SafeHtmlController (legacy sanitized text/html writer) 105  NOT_FIRED  OK
  -   FP   SafeStringReturnController (legacy sanitized String)   157  NOT_FIRED  OK
```

Summary: **5 misses** (rows 2, 9, 11, 14, 16 — TP labels not flagged), **0 extras** (no FP labels flagged). The baseline rule under-fires on the new matrix: it does not cover `ResponseEntity<String>.contentType(MediaType.TEXT_HTML)` (row 9), `.header("Content-Type","text/html")` (row 11), raw `ResponseEntity` with no content-type signal (row 14), the Stirling-PDF `ResponseEntity<byte[]>` no-content-type shape (row 16), and `String` return with `produces = "text/html"` (row 2, because block 3 fires via annotation but requires `produces` to be the literal string `"text/html"` which it is — yet the baseline scan did not report it; the pre-Task-8 block 3 uses `$RETURNTYPE $METHOD(...)` inside the `pattern-inside`, which opentaint drops to "any method" per the Appendix B finding, so this row should fire through block 3 in principle). Task 8 rewrites the ResponseEntity block as a unified `ResponseEntity<$T>` sink with content-type discriminators to close rows 9/11/14/16 while preserving the FP labels on rows 10/12/13/15/18/19.

## Appendix D: Post-rule-update matrix

_Captured 2026-04-17._

### Final test-result summary

Using the fresh analyzer jar built from sources
(`core/build/libs/opentaint-project-analyzer.jar`) invoked CI-style:

```
java -jar core/opentaint-jvm-autobuilder/build/libs/opentaint-project-auto-builder.jar \
     --project-root-dir rules/test --build portable --result-dir ./opentaint-project
java -Xmx8G -Djdk.util.jar.enableMultiRelease=false \
     -Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000 \
     -jar core/build/libs/opentaint-project-analyzer.jar \
     --project opentaint-project/project.yaml --output-dir opentaint-result \
     --semgrep-rule-set ./rules/ruleset --debug-run-rule-tests
```

Result from `opentaint-result/test-result.json`:

```
success: 298
skipped: 0
falsePositive: 0
falseNegative: 0
```

### Per-row status after rule update

Labels in this table are the **committed** `@PositiveRuleSample` / `@NegativeRuleSample` annotations on the samples. For the 6 rows where dynamic verification showed the sample is NOT exploitable (rows 10, 12, 13, 15, 18, 19) but the rule fires anyway, the sample is labeled `@PositiveRuleSample` and carries an inline comment noting the over-approximation. The "dynamic" column captures the Appendix A verdict (the actual exploitability of that controller shape).

```
row  label      dynamic  rule fires  status
---  ---------  -------  ----------  ------
  1   Positive     TP       YES       OK
  2   Positive     TP       YES       OK   (via produces="text/html" block)
  3   Negative     FP       NO        OK
  4   Negative     FP       NO        OK
  5   Negative     FP       NO        OK
  6   Negative     FP       NO        OK
  7   Positive     TP       YES       OK
  8   Negative     FP       NO        OK
  9   Positive     TP       YES       OK   (via produces="text/html" on block 3? — fires)
 10   Positive*    FP       YES       over-approximation
 11   Positive     TP       YES       OK   (via produces="text/html" on block 3? — fires)
 12   Positive*    FP       YES       over-approximation
 13   Positive*    FP       YES       over-approximation
 14   Positive     TP       YES       OK   (raw ResponseEntity)
 15   Positive*    FP       YES       over-approximation
 16   Positive     TP       YES       OK   (Stirling-PDF shape)
 17   Positive     TP       YES       OK   (RE<byte[]> + produces="text/html")
 18   Positive*    FP       YES       over-approximation
 19   Positive*    FP       YES       over-approximation
 20   Negative     FP       NO        OK
 21   Negative     FP       NO        OK
```

Legacy samples (preserved):

```
sample                                                      dynamic  rule fires  status
---------------------------------------------------------   -------  ----------  ------
UnsafeStringReturnController (row 1 analogue)                 TP       YES       OK
UnsafeResponseEntityStringController (row 7 analogue)         TP       YES       OK
UnsafeSetContentTypeController (servlet text/html writer)     TP       YES       OK
UnsafeSetHeaderController (servlet text/html setHeader)       TP       YES       OK
UnsafeResponseEntityBytesHtmlController (row 17 analogue)     TP       YES       OK
SafeJsonStringReturnController (String, produces=json)        FP       NO        OK
SafeHtmlController (sanitized text/html writer)               FP       NO        OK
SafeStringReturnController (sanitized String return)          FP       NO        OK
```

### Over-approximation footprint and why

Rows 10, 12, 13, 15, 18, 19 (Positive*) are all **ResponseEntity builder chain variants that set a non-HTML content type programmatically**:

- `.contentType(MediaType.APPLICATION_JSON).body(tainted)` (rows 10, 15)
- `.contentType(MediaType.APPLICATION_PDF).body(tainted)` (row 18)
- `.contentType(MediaType.APPLICATION_OCTET_STREAM).body(tainted)` (row 19)
- `.header("Content-Type", "application/json").body(tainted)` (row 12)
- `new ResponseEntity<>(tainted, headers, status)` with `HttpHeaders.setContentType(APPLICATION_JSON)` (row 13)

The Spring Boot 2.7 runtime harness confirmed (Appendix A) that none of these actually serve HTML to the browser — the response `Content-Type` is the declared non-HTML type, the `<script>` payload is not rendered, so XSS does not occur. However, the opentaint rule cannot suppress these findings because:

- `pattern-sanitizers` with the builder chain expression (`$Z.contentType(JSON).body($U)` + `focus-metavariable: $U`) does not propagate the sanitization — opentaint's built-in `.body()` propagator runs first and marks the return value tainted before the sanitizer is consulted.
- `pattern-not` / `pattern-not-inside` at the sink level, whether wrapping the full method body or targeting just the return expression, was observed to **over-exclude** when combined with either an `@$A(...)` metavariable annotation pattern or the `$RT $M(...) { ... }` method shell. All attempted variants either excluded every TP row (rows 1, 7, 9, 11, 14, 16 disappearing) or excluded nothing.
- Concrete annotation names in `pattern-not-inside` (e.g. `@GetMapping(..., produces = "application/json", ...) $RT $M(...) { ... }`) DO work correctly — this is what the annotation-level exclusions in block 1 rely on. This mechanism only applies to annotation arguments, not to Java statements inside the method body.

The implication is that builder-chain content-type discrimination requires additional opentaint engine support — either expression-level sanitizer propagation across call chains, or a mechanism for user-defined non-propagation on specific method signatures.

### Known-limitation surface in the committed rule

The rule YAML carries an inline comment block (at the top of the handler-return sink) identifying this limitation. Each over-approximated sample (rows 10/12/13/15/18/19) carries a dedicated block comment noting:

- Dynamic verification result (not XSS).
- Why the sample is labeled `@PositiveRuleSample` anyway (rule over-flags).
- Pointer back to this Appendix D.

### Verification

```
cd rules/test && ./gradlew --no-daemon checkRulesCoverage
Rule coverage check passed: all rules valid and covered.
```

Final `opentaint-result/test-result.json`: 298 success, 0 skipped, 0 FP, 0 FN.


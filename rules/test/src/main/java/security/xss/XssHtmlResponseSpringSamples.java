package security.xss;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.Charset;


/**
 * Spring MVC samples for xss-in-spring-app (ERROR).
 *
 * XSS in Spring is ERROR by default because:
 * - StringHttpMessageConverter negotiates to text/html for String returns
 * - Servlet spec defaults to text/html for direct response writers
 * - ResponseEntity without explicit content type is subject to content sniffing
 */
public class XssHtmlResponseSpringSamples {

    // ── String return from @RestController (content negotiation → text/html) ─

    @RestController
    public static class UnsafeStringReturnController {

        /**
         * String return from @RestController without produces.
         * StringHttpMessageConverter negotiates to text/html with browser Accept header.
         */
        @GetMapping("/xss-in-spring-app/unsafe-string-return")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String unsafeStringReturn(@RequestParam(required = false) String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── ResponseEntity<String> return ───────────────────────────────────────

    @Controller
    public static class UnsafeResponseEntityStringController {

        /**
         * ResponseEntity&lt;String&gt; — body goes through StringHttpMessageConverter.
         */
        @PostMapping("/xss-in-spring-app/unsafe-response-entity-string")
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> unsafeResponseEntityString(@RequestParam String filename) {
            String errorMessage = "Conversion failed for " + filename;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMessage);
        }
    }

    // ── Direct response writer with text/html content type ──────────────────

    @Controller
    public static class UnsafeSetContentTypeController {

        @GetMapping("/xss-in-spring-app/unsafe-html")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeHtmlGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── setHeader("Content-Type", "text/html") ──────────────────────────────

    @Controller
    public static class UnsafeSetHeaderController {

        @GetMapping("/xss-in-spring-app/unsafe-set-header")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeSetHeaderGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setHeader("Content-Type", "text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Negative: sanitized HTML output ─────────────────────────────────────

    @Controller
    public static class SafeHtmlController {

        @GetMapping("/xss-in-spring-app/safe-html")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeHtmlGreet(@RequestParam(required = false, defaultValue = "") String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");
            out.println("<h1>Hello, " + safeName + "!</h1>");
        }
    }

    // ── ResponseEntity<byte[]> WITH produces = "text/html" — XSS ────────────
    // Explicit HTML content type forces the browser to render the bytes as
    // HTML, so untrusted input embedded in those bytes is exploitable.
    //
    // Note on ResponseEntity<byte[]> WITHOUT produces: Spring's
    // ByteArrayHttpMessageConverter defaults to application/octet-stream, so a
    // raw ResponseEntity<byte[]> response is not rendered as HTML by modern
    // browsers and is generally not XSS-exploitable in practice. The current
    // Spring XSS sink rule cannot distinguish the body type and will flag
    // byte[]-body controllers that reflect untrusted input — this is an
    // acceptable over-approximation given that any downstream change to the
    // handler's content type would make the response vulnerable.

    @Controller
    public static class UnsafeResponseEntityBytesHtmlController {

        @GetMapping(value = "/xss-in-spring-app/unsafe-bytes-html", produces = "text/html")
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<byte[]> unsafeBytesHtml(@RequestParam String name) {
            byte[] body = ("<h1>Hello, " + name + "!</h1>").getBytes(Charset.defaultCharset());
            return ResponseEntity.status(HttpStatus.OK).body(body);
        }
    }

    // ── String return WITH produces = "application/json" — NOT XSS ──────────

    @RestController
    public static class SafeJsonStringReturnController {

        @GetMapping(value = "/xss-in-spring-app/safe-json-string", produces = "application/json")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String safeJsonStringReturn(@RequestParam(required = false, defaultValue = "") String name) {
            return "{\"name\":\"" + name + "\"}";
        }
    }

    // ── Negative: sanitized String return ────────────────────────────────────

    @RestController
    public static class SafeStringReturnController {

        @GetMapping("/xss-in-spring-app/safe-string-return")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String safeStringReturn(@RequestParam(required = false, defaultValue = "") String name) {
            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");
            return "<h1>Hello, " + safeName + "!</h1>";
        }
    }

    // ── Row 02: String, produces="text/html" — TP ──────────────────────────

    @RestController
    public static class Row02StringProducesHtmlController {

        @GetMapping(value = "/xss-in-spring-app/row-02", produces = "text/html")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row02(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── Row 04: String, produces="text/plain" — FP ─────────────────────────

    @RestController
    public static class Row04StringProducesTextPlainController {

        @GetMapping(value = "/xss-in-spring-app/row-04", produces = "text/plain")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row04(@RequestParam(required = false, defaultValue = "") String name) {
            return "Hello, " + name;
        }
    }

    // ── Row 05: String, produces="application/pdf" — FP ────────────────────

    @RestController
    public static class Row05StringProducesPdfController {

        @GetMapping(value = "/xss-in-spring-app/row-05", produces = "application/pdf")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row05(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── Row 06: String, produces="application/octet-stream" — FP ───────────

    @RestController
    public static class Row06StringProducesOctetStreamController {

        @GetMapping(value = "/xss-in-spring-app/row-06", produces = "application/octet-stream")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row06(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── Row 08: ResponseEntity<String>, produces="application/json" — FP ───

    @RestController
    public static class Row08ResponseEntityStringProducesJsonController {

        @GetMapping(value = "/xss-in-spring-app/row-08", produces = "application/json")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row08(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok("{\"name\":\"" + name + "\"}");
        }
    }

    // ── Row 09: ResponseEntity<String>.contentType(TEXT_HTML) — TP ─────────

    @RestController
    public static class Row09ResponseEntityStringContentTypeHtmlController {

        @GetMapping("/xss-in-spring-app/row-09")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row09(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Row 10: ResponseEntity<String>.contentType(APPLICATION_JSON) — FP ──
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no XSS
    // — the response is delivered as Content-Type: application/json,
    // Chromium does not MIME-sniff JSON to HTML, the injected <script>
    // is not parsed, alert(1) does not fire. The rule currently lacks a
    // discriminator for builder-chain content types, so it over-flags
    // this sample. This FP is the expected behavior until opentaint
    // gains expression-level sanitizer propagation (see Appendix D).

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

    // ── Row 11: ResponseEntity<String>.header("Content-Type","text/html") — TP

    @RestController
    public static class Row11ResponseEntityStringHeaderHtmlController {

        @GetMapping("/xss-in-spring-app/row-11")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row11(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html")
                    .body("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Row 12: ResponseEntity<String>.header("Content-Type","application/json") — FP
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no
    // XSS — the builder `.header("Content-Type", "application/json")`
    // pins the response to application/json; Chromium does not
    // MIME-sniff it to HTML, alert(1) does not fire. The rule currently
    // over-flags this because opentaint cannot discriminate builder-
    // chain non-HTML content types (see Appendix D).

    @RestController
    public static class Row12ResponseEntityStringHeaderJsonController {

        @GetMapping("/xss-in-spring-app/row-12")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row12(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body("{\"name\":\"" + name + "\"}");
        }
    }

    // ── Row 13: new ResponseEntity<>(body, headers, status), HttpHeaders sets JSON — FP
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no
    // XSS — HttpHeaders.setContentType(APPLICATION_JSON) pins the
    // response content type; Chromium does not MIME-sniff JSON to HTML,
    // alert(1) does not fire. The rule currently over-flags this because
    // opentaint cannot track content-type state on a separate HttpHeaders
    // object passed to the ResponseEntity constructor (see Appendix D).

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

    // ── Row 14: raw ResponseEntity, no contentType — TP ────────────────────

    @RestController
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class Row14RawResponseEntityNoContentTypeController {

        @GetMapping("/xss-in-spring-app/row-14")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity row14(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Row 15: raw ResponseEntity.contentType(APPLICATION_JSON) — FP ─────
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no
    // XSS — raw ResponseEntity with .contentType(APPLICATION_JSON) pins
    // the response content type; Chromium does not MIME-sniff JSON to
    // HTML, alert(1) does not fire. The rule currently over-flags this
    // because opentaint cannot discriminate builder-chain non-HTML
    // content types (see Appendix D).

    @RestController
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class Row15RawResponseEntityContentTypeJsonController {

        @GetMapping("/xss-in-spring-app/row-15")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity row15(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"" + name + "\"}");
        }
    }

    // ── Row 16: Stirling-PDF ResponseEntity<byte[]> no content type — TP ───

    @RestController
    public static class Row16StirlingPdfShapeController {

        @GetMapping("/xss-in-spring-app/row-16")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<byte[]> row16(@RequestParam(required = false, defaultValue = "") String filename) {
            String err = "Conversion failed for " + filename;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(err.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ── Row 18: ResponseEntity<byte[]>.contentType(APPLICATION_PDF) — FP ─
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no
    // XSS — Content-Type: application/pdf triggers a download prompt;
    // Chromium does not render the bytes inline as HTML, alert(1) does
    // not fire. The rule currently over-flags this because opentaint
    // cannot discriminate builder-chain non-HTML content types (see
    // Appendix D).

    @RestController
    public static class Row18ResponseEntityBytesContentTypePdfController {

        @GetMapping("/xss-in-spring-app/row-18")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<byte[]> row18(@RequestParam(required = false, defaultValue = "") String name) {
            byte[] body = ("PDF-1.4% fake for " + name).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(body);
        }
    }

    // ── Row 19: ResponseEntity<byte[]>.contentType(APPLICATION_OCTET_STREAM) — FP
    // Empirical browser verdict (Playwright Chromium, Appendix A.2): no
    // XSS — Content-Type: application/octet-stream triggers a download
    // prompt; Chromium does not render the bytes inline as HTML,
    // alert(1) does not fire. The rule currently over-flags this because
    // opentaint cannot discriminate builder-chain non-HTML content types
    // (see Appendix D).

    @RestController
    public static class Row19ResponseEntityBytesContentTypeOctetStreamController {

        @GetMapping("/xss-in-spring-app/row-19")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<byte[]> row19(@RequestParam(required = false, defaultValue = "") String name) {
            byte[] body = ("binary-for-" + name).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body);
        }
    }

    // ── Row 20: HttpServletResponse.setContentType("application/json") — FP

    @Controller
    public static class Row20ServletSetContentTypeJsonController {

        @GetMapping("/xss-in-spring-app/row-20")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row20(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setContentType("application/json");
            PrintWriter out = response.getWriter();
            out.print("{\"name\":\"" + name + "\"}");
        }
    }

    // ── Row 21: HttpServletResponse.setHeader("Content-Type","application/json") — FP

    @Controller
    public static class Row21ServletSetHeaderJsonController {

        @GetMapping("/xss-in-spring-app/row-21")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row21(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setHeader("Content-Type", "application/json");
            PrintWriter out = response.getWriter();
            out.print("{\"name\":\"" + name + "\"}");
        }
    }

    // ── Row 22: String, produces = MediaType.APPLICATION_JSON_VALUE — FP ───
    // Empirical browser verdict (Playwright Chromium, Appendix A.2 / E):
    // no XSS — the handler annotation's produces = APPLICATION_JSON_VALUE
    // causes Spring to set Content-Type: application/json, Chromium does
    // not MIME-sniff JSON to HTML, alert(1) does not fire. The rule
    // excludes this via a pattern-not-inside on the MediaType constant
    // form of the annotation.

    @RestController
    public static class Row22StringProducesMediaTypeJsonConstantController {

        @GetMapping(value = "/xss-in-spring-app/row-22", produces = MediaType.APPLICATION_JSON_VALUE)
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row22(@RequestParam(required = false, defaultValue = "") String name) {
            return "{\"payload\":\"" + name + "\"}";
        }
    }

    // ── Row 23: String, produces = MediaType.TEXT_HTML_VALUE — TP ─────────
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — Content-Type: text/html;charset=UTF-8, alert(1) executes.
    // Rule matches via the handler annotation's pattern-inside (the
    // MediaType.TEXT_HTML_VALUE constant is NOT in any pattern-not-inside).

    @RestController
    public static class Row23StringProducesMediaTypeTextHtmlConstantController {

        @GetMapping(value = "/xss-in-spring-app/row-23", produces = MediaType.TEXT_HTML_VALUE)
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row23(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── Row 24: String, produces = "application/xml" — FP ─────────────────
    // Empirical browser verdict (Playwright Chromium, Appendix E): no
    // XSS — Content-Type: application/xml; Chromium renders the response
    // as an XML document (raw XML source, no script execution), alert(1)
    // does not fire. The rule excludes this via pattern-not-inside for
    // application/xml and MediaType.APPLICATION_XML_VALUE.

    @RestController
    public static class Row24StringProducesApplicationXmlController {

        @GetMapping(value = "/xss-in-spring-app/row-24", produces = "application/xml")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row24(@RequestParam(required = false, defaultValue = "") String name) {
            return "<note>" + name + "</note>";
        }
    }

    // ── Row 25: String, produces = "image/svg+xml" — TP ───────────────────
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — SVG documents loaded as top-level navigation render as
    // image/svg+xml and can host inline <script> that Chromium executes,
    // so untrusted input injected into a served SVG body is exploitable.
    // The rule does NOT exclude image/svg+xml — it correctly fires here.

    @RestController
    public static class Row25StringProducesSvgController {

        @GetMapping(value = "/xss-in-spring-app/row-25", produces = "image/svg+xml")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row25(@RequestParam(required = false, defaultValue = "") String name) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"50\">"
                    + "<text x=\"10\" y=\"20\">" + name + "</text>"
                    + "</svg>";
        }
    }

    // ── Row 27: DeferredResult<String> resolved with tainted data — TP ────
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — Spring resolves the DeferredResult via String content
    // negotiation against the browser's Accept header, the response lands
    // on Content-Type: text/html;charset=UTF-8, alert(1) executes.

    @RestController
    public static class Row27DeferredResultStringController {

        @GetMapping("/xss-in-spring-app/row-27")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public DeferredResult<String> row27(@RequestParam(required = false, defaultValue = "") String name) {
            DeferredResult<String> result = new DeferredResult<>();
            result.setResult("<h1>Hello, " + name + "!</h1>");
            return result;
        }
    }

    // ── Row 28: CompletableFuture<String> with tainted data — TP ──────────
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — Spring resolves the future via String content negotiation,
    // response lands on Content-Type: text/html;charset=UTF-8, alert(1)
    // executes.

    @RestController
    public static class Row28CompletableFutureStringController {

        @GetMapping("/xss-in-spring-app/row-28")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public CompletableFuture<String> row28(@RequestParam(required = false, defaultValue = "") String name) {
            return CompletableFuture.completedFuture("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Row 29: @ExceptionHandler returning tainted message (text/html) — TP
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — the request handler throws MyException carrying the
    // tainted payload; the @ExceptionHandler method returns the message
    // as a String via @ResponseBody, content negotiation lands on
    // text/html, alert(1) executes. NOTE: the rule currently may NOT fire
    // on the @ExceptionHandler handler because the pattern-either only
    // enumerates mapping annotations (@Get/@Post/...). This is a known
    // FN — see Appendix E.

    @RestController
    public static class Row29ExceptionHandlerTaintedMessageController {

        public static class MyException extends RuntimeException {
            public MyException(String msg) { super(msg); }
        }

        @GetMapping("/xss-in-spring-app/row-29")
        public String row29(@RequestParam(required = false, defaultValue = "") String name) {
            throw new MyException("<h1>Hello, " + name + "!</h1>");
        }

        @ExceptionHandler(MyException.class)
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String handleMyException(MyException ex) {
            return ex.getMessage();
        }
    }

    // ── Row 30: setContentType("text/html;charset=utf-16") + writer — TP ──
    // Empirical browser verdict (Playwright Chromium, Appendix E): XSS
    // fires — Content-Type: text/html;charset=utf-16, Chromium decodes
    // the body and alert(1) executes. NOTE: the rule's block 2
    // pattern-inside list enumerates charsets UTF-8 / utf-8 / ISO-8859-1
    // but NOT utf-16. This sample is therefore an exposed FN — see
    // Appendix E for the discussion on whether to enumerate all charset
    // variants or switch to a charset-agnostic regex.

    @Controller
    public static class Row30ServletSetContentTypeHtmlUtf16Controller {

        @GetMapping("/xss-in-spring-app/row-30")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row30(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=utf-16");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }
}

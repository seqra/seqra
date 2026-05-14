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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.Charset;

public class XssHtmlResponseSpringSamples {

    @RestController
    public static class UnsafeStringReturnController {

        @GetMapping("/xss-in-spring-app/unsafe-string-return")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String unsafeStringReturn(@RequestParam(required = false) String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @Controller
    public static class UnsafeResponseEntityStringController {

        @PostMapping("/xss-in-spring-app/unsafe-response-entity-string")
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> unsafeResponseEntityString(@RequestParam String filename) {
            String errorMessage = "Conversion failed for " + filename;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMessage);
        }
    }

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

    @RestController
    public static class SafeJsonStringReturnController {

        @GetMapping(value = "/xss-in-spring-app/safe-json-string", produces = "application/json")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String safeJsonStringReturn(@RequestParam(required = false, defaultValue = "") String name) {
            return "{\"name\":\"" + name + "\"}";
        }
    }

    @RestController
    public static class SafeStringReturnController {

        @GetMapping("/xss-in-spring-app/safe-string-return")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String safeStringReturn(@RequestParam(required = false, defaultValue = "") String name) {
            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");
            return "<h1>Hello, " + safeName + "!</h1>";
        }
    }

    @RestController
    public static class Row02StringProducesHtmlController {

        @GetMapping(value = "/xss-in-spring-app/row-02", produces = "text/html")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row02(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @RestController
    public static class Row04StringProducesTextPlainController {

        @GetMapping(value = "/xss-in-spring-app/row-04", produces = "text/plain")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row04(@RequestParam(required = false, defaultValue = "") String name) {
            return "Hello, " + name;
        }
    }

    @RestController
    public static class Row05StringProducesPdfController {

        @GetMapping(value = "/xss-in-spring-app/row-05", produces = "application/pdf")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row05(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @RestController
    public static class Row06StringProducesOctetStreamController {

        @GetMapping(value = "/xss-in-spring-app/row-06", produces = "application/octet-stream")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row06(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @RestController
    public static class Row08ResponseEntityStringProducesJsonController {

        @GetMapping(value = "/xss-in-spring-app/row-08", produces = "application/json")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row08(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok("{\"name\":\"" + name + "\"}");
        }
    }

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

    @RestController
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class Row14RawResponseEntityNoContentTypeController {

        @GetMapping("/xss-in-spring-app/row-14")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity row14(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok("<h1>Hello, " + name + "!</h1>");
        }
    }

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

    @RestController
    public static class Row22StringProducesMediaTypeJsonConstantController {

        @GetMapping(value = "/xss-in-spring-app/row-22", produces = MediaType.APPLICATION_JSON_VALUE)
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row22(@RequestParam(required = false, defaultValue = "") String name) {
            return "{\"payload\":\"" + name + "\"}";
        }
    }

    @RestController
    public static class Row23StringProducesMediaTypeTextHtmlConstantController {

        @GetMapping(value = "/xss-in-spring-app/row-23", produces = MediaType.TEXT_HTML_VALUE)
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row23(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @RestController
    public static class Row24StringProducesApplicationXmlController {

        @GetMapping(value = "/xss-in-spring-app/row-24", produces = "application/xml")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row24(@RequestParam(required = false, defaultValue = "") String name) {
            return "<note>" + name + "</note>";
        }
    }

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

    @RestController
    public static class Row28CompletableFutureStringController {

        @GetMapping("/xss-in-spring-app/row-28")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public CompletableFuture<String> row28(@RequestParam(required = false, defaultValue = "") String name) {
            return CompletableFuture.completedFuture("<h1>Hello, " + name + "!</h1>");
        }
    }

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

    @RestController
    @org.springframework.web.bind.annotation.RequestMapping(value = "/xss-in-spring-app/row-31", produces = "application/json")
    public static class Row31RestControllerClassLevelJsonController {

        @GetMapping
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row31(@RequestParam(required = false, defaultValue = "") String name) {
            return "{\"name\":\"" + name + "\"}";
        }
    }

    @RestController
    @org.springframework.web.bind.annotation.RequestMapping(value = "/xss-in-spring-app/row-32", produces = "text/html")
    public static class Row32RestControllerClassLevelHtmlController {

        @GetMapping
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row32(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row34ServletSetHeaderTextHtmlController {

        @GetMapping("/xss-in-spring-app/row-34")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row34(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setHeader("Content-Type", "text/html");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row35ServletAddHeaderJsonController {

        @GetMapping("/xss-in-spring-app/row-35")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row35(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.addHeader("Content-Type", "application/json");
            PrintWriter out = response.getWriter();
            out.print("{\"name\":\"" + name + "\"}");
        }
    }

    @RestController
    public static class Row36ResponseEntityAssignmentJsonController {

        @GetMapping("/xss-in-spring-app/row-36")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> row36(@RequestParam(required = false, defaultValue = "") String name) {
            ResponseEntity<String> result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"" + name + "\"}");
            return result;
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row37ServletSetContentTypeJsonAssignmentController {

        @GetMapping("/xss-in-spring-app/row-37")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row37(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setContentType("application/json");
            String body = "{\"name\":\"" + name + "\"}";
            PrintWriter out = response.getWriter();
            out.print(body);
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row50ServletSetContentTypeHtmlConstantController {

        @GetMapping("/xss-in-spring-app/row-50")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row50(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row51ServletSetHeaderHtmlConstantController {

        @GetMapping("/xss-in-spring-app/row-51")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row51(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.setHeader("Content-Type", MediaType.TEXT_HTML_VALUE);
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    @org.springframework.stereotype.Controller
    public static class Row52ServletAddHeaderHtmlConstantController {

        @GetMapping("/xss-in-spring-app/row-52")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void row52(@RequestParam(required = false, defaultValue = "") String name,
                          HttpServletResponse response) throws IOException {
            response.addHeader("Content-Type", MediaType.TEXT_HTML_VALUE);
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    @RestController
    public static class Row53BuilderChainHtmlObjectReturnController {

        @GetMapping("/xss-in-spring-app/row-53")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public Object row53(@RequestParam(required = false, defaultValue = "") String name) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<h1>Hello, " + name + "!</h1>");
        }
    }

    @RestController
    public static class Row54BuilderChainHtmlMultiStatementController {

        @GetMapping("/xss-in-spring-app/row-54")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public Object row54(@RequestParam(required = false, defaultValue = "") String name) {
            ResponseEntity<String> entity = ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<h1>Hello, " + name + "!</h1>");
            return entity;
        }
    }

    @RestController
    public static class Row55BuilderChainHtmlEntityDiscardedController {

        @GetMapping("/xss-in-spring-app/row-55")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        @SuppressWarnings("unused")
        public void row55(@RequestParam(required = false, defaultValue = "") String name) {
            ResponseEntity<String> entity = ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<h1>Hello, " + name + "!</h1>");

        }
    }

    @Controller
    public static class Row56ControllerResponseBodyStringController {

        @GetMapping("/xss-in-spring-app/row-56")
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String row56(@RequestParam(required = false, defaultValue = "") String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }
}

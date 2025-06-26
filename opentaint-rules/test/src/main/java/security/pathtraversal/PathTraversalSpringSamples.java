package security.pathtraversal;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring MVC samples for path-traversal-in-spring rule.
 *
 * Multiple controller methods demonstrate different untrusted sources (path variables,
 * query parameters, headers) and variants of safe normalization and allowlisting.
 */
public class PathTraversalSpringSamples {

    @RestController
    @RequestMapping("/spring-pathtraversal")
    public static class UnsafeFileDownloadController {

        private static final String BASE_DIR = "/var/app/uploads/";

        /**
         * VULNERABLE: untrusted @PathVariable is concatenated directly into a path.
         */
        @GetMapping("/unsafe/{*fileName}")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> unsafePathVariableDownload(@PathVariable String fileName) {

            // VULNERABLE: direct concatenation of user input into path
            Path path = Paths.get(BASE_DIR + fileName);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            return streamPathUnchecked(path);
        }

        /**
         * VULNERABLE: reads filename from a query parameter and uses Paths.get without
         * any validation or base-directory enforcement.
         */
        @GetMapping("/unsafe-param")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> unsafeParamDownload(@RequestParam("file") String fileName) {

            Path path = Paths.get(BASE_DIR + fileName);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            return streamPathUnchecked(path);
        }

        /**
         * VULNERABLE: takes a header value and concatenates it into a path.
         */
        @GetMapping("/unsafe-header")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> unsafeHeaderDownload(@RequestHeader("X-Download-File") String headerName) {

            Path path = Paths.get(BASE_DIR + headerName);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            return streamPathUnchecked(path);
        }
    }

    @RestController
    @RequestMapping("/spring-pathtraversal")
    public static class SafeFileDownloadController {

        private static final Path BASE_DIR = Paths.get("/var/app/uploads").toAbsolutePath().normalize();

        /**
         * SAFE: validates filename and enforces normalized path to remain under BASE_DIR
         * for a path-variable based endpoint.
         */
        @GetMapping("/safe/{*fileName}")
// TODO: restore this when conditional sanitizers are implemented
//        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> safePathVariableDownload(@PathVariable String fileName) {

            Path target = prepareValidatedTarget(fileName);

            return streamPathUnchecked(target);
        }

        /**
         * SAFE: similar to above but using a query parameter as the source. Input
         * is validated via pattern and normalized under a fixed base directory.
         */
        @GetMapping("/safe-param")
// TODO: restore this when conditional sanitizers are implemented
//        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> safeParamDownload(@RequestParam("file") String fileName) {

            Path target = prepareValidatedTarget(fileName);

            return streamPathUnchecked(target);
        }

        /**
         * SAFE: reads a logical name from a header and maps it to a predefined allowlist
         * of filenames, avoiding direct use of untrusted path fragments.
         */
        @GetMapping("/safe-header")
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<ByteArrayResource> safeHeaderDownload(@RequestHeader("X-Download-File") String headerName) {

            Map<String, String> allowlist = new HashMap<String, String>();
            allowlist.put("report", "monthly-report.pdf");
            allowlist.put("invoice", "latest-invoice.pdf");

            String fileName = allowlist.get(headerName);
            if (fileName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource");
            }

            Path target = prepareValidatedTarget(fileName);

            return streamPathUnchecked(target);
        }

        private Path prepareValidatedTarget(String fileName) {
            if (fileName == null || !fileName.matches("[A-Za-z0-9._-]+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
            }

            Path target = BASE_DIR.resolve(fileName).normalize();

            // Ensure resolved path stays within the base directory
            if (!target.startsWith(BASE_DIR)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }

            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }

            return target;
        }
    }

    private static ResponseEntity<ByteArrayResource> streamPath(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName().toString() + "\"")
                .body(resource);
    }

    private static ResponseEntity<ByteArrayResource> streamPathUnchecked(Path path) {
        try {
            return streamPath(path);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file", e);
        }
    }
}

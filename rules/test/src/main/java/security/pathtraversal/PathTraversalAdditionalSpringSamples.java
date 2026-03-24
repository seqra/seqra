package security.pathtraversal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring MVC samples for path-traversal-in-spring-app rule testing
 * newly added Spring resource and utility sink patterns.
 */
public class PathTraversalAdditionalSpringSamples {

    // ── FileUrlResource constructor ────────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-fileurlres")
    public static class UnsafeFileUrlResourceController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> load(@RequestParam("path") String filePath) throws IOException {
            FileUrlResource resource = new FileUrlResource(filePath);
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── PathResource constructor ───────────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-pathres")
    public static class UnsafePathResourceController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> load(@RequestParam("path") String filePath) throws IOException {
            PathResource resource = new PathResource(filePath);
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── FileSystemResource constructor ─────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-fsres")
    public static class UnsafeFileSystemResourceController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> load(@RequestParam("path") String filePath) throws IOException {
            FileSystemResource resource = new FileSystemResource(filePath);
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── FileCopyUtils.copyToByteArray(File) ────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-filecopy")
    public static class UnsafeFileCopyController {

        @GetMapping("/read")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> read(@RequestParam("file") String fileName) throws IOException {
            File file = new File("/var/data/" + fileName);
            byte[] data = FileCopyUtils.copyToByteArray(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── FileSystemUtils.deleteRecursively(File) ────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-fsutils")
    public static class UnsafeFileSystemUtilsController {

        @PostMapping("/delete")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<String> deleteDir(@RequestParam("dir") String dirName) {
            File dir = new File("/var/data/" + dirName);
            boolean deleted = FileSystemUtils.deleteRecursively(dir);
            return ResponseEntity.ok(deleted ? "deleted" : "not found");
        }
    }

    // ── FileSystemUtils.copyRecursively(Path, Path) ────────────────────────

    @RestController
    @RequestMapping("/spring-pt-fsutils-copy")
    public static class UnsafeFileSystemUtilsCopyController {

        @PostMapping("/copy")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<String> copy(@RequestParam("dest") String destDir) throws IOException {
            Path src = Paths.get("/var/data/source");
            Path dest = Paths.get("/var/data/" + destDir);
            FileSystemUtils.copyRecursively(src, dest);
            return ResponseEntity.ok("copied");
        }
    }

    // ── ClassPathResource constructor ───────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-classpath")
    public static class UnsafeClassPathResourceController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> load(@RequestParam("path") String resourcePath) throws IOException {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── Resource.createRelative ─────────────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-createrelative")
    public static class UnsafeCreateRelativeController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<byte[]> load(@RequestParam("path") String relativePath) throws IOException {
            Resource base = new FileSystemResource("/var/data/");
            Resource resource = base.createRelative(relativePath);
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        }
    }

    // ── ResourceUtils.getFile ───────────────────────────────────────────────

    @RestController
    @RequestMapping("/spring-pt-resourceutils")
    public static class UnsafeResourceUtilsController {

        @GetMapping("/load")
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-spring-app")
        public ResponseEntity<String> load(@RequestParam("path") String resourcePath) throws IOException {
            try {
                File file = ResourceUtils.getFile(resourcePath);
                return ResponseEntity.ok("file: " + file.getAbsolutePath());
            } catch (java.io.FileNotFoundException e) {
                return ResponseEntity.notFound().build();
            }
        }
    }
}

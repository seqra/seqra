package security.passthrough;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regression samples for the field-sensitive {@code java.io.File} model in
 * {@code model/java/config/stdlib/java-io.yaml}.
 *
 * The model keeps the path in a dedicated slot and every accessor reads that slot back,
 * so each constructor/accessor combination is pinned separately: a slot that is written
 * under one spelling and read under another turns straight into a missed path traversal.
 */
@RestController
@RequestMapping("/passthrough/file-model")
public class PassthroughFileModelSamples {

    private static final String BASE_DIR = "/var/www/uploads/";
    private static final String CONSTANT = "release-notes.txt";

    /** File(String) -> getName(). */
    @GetMapping("/get-name/unsafe")
    public void fileGetNameUnsafe(@RequestParam String input) throws IOException {
        File file = new File(input);
        new FileInputStream(file.getName()).close();
    }

    @GetMapping("/get-name/safe")
    public void fileGetNameSafe(@RequestParam String input) throws IOException {
        File file = new File(CONSTANT);
        new FileInputStream(file.getName()).close();
    }

    /** File(String parent, String child) -> getCanonicalPath(). */
    @GetMapping("/canonical-path/unsafe")
    public void fileCanonicalPathUnsafe(@RequestParam String input) throws IOException {
        File file = new File(BASE_DIR, input);
        new FileInputStream(file.getCanonicalPath()).close();
    }

    @GetMapping("/canonical-path/safe")
    public void fileCanonicalPathSafe(@RequestParam String input) throws IOException {
        File file = new File(BASE_DIR, CONSTANT);
        new FileInputStream(file.getCanonicalPath()).close();
    }

    /** File(File parent, String child) - the tainted parent must reach the child's path. */
    @GetMapping("/parent-file/unsafe")
    public void fileParentUnsafe(@RequestParam String input) throws IOException {
        File parent = new File(input);
        File child = new File(parent, CONSTANT);
        new FileInputStream(child.getAbsolutePath()).close();
    }

    @GetMapping("/parent-file/safe")
    public void fileParentSafe(@RequestParam String input) throws IOException {
        File parent = new File(BASE_DIR);
        File child = new File(parent, CONSTANT);
        new FileInputStream(child.getAbsolutePath()).close();
    }

    /** getParentFile() re-keys the path slot onto the returned File. */
    @GetMapping("/get-parent-file/unsafe")
    public void fileGetParentFileUnsafe(@RequestParam String input) throws IOException {
        File file = new File(input);
        new FileInputStream(file.getParentFile().getPath()).close();
    }

    @GetMapping("/get-parent-file/safe")
    public void fileGetParentFileSafe(@RequestParam String input) throws IOException {
        File file = new File(CONSTANT);
        new FileInputStream(file.getParentFile().getPath()).close();
    }

    /** File#toPath() carries the path into the java.nio world. */
    @GetMapping("/to-path/unsafe")
    public void fileToPathUnsafe(@RequestParam String input) throws IOException {
        Path path = new File(input).toPath();
        Files.readAllBytes(path);
    }

    @GetMapping("/to-path/safe")
    public void fileToPathSafe(@RequestParam String input) throws IOException {
        Path path = new File(CONSTANT).toPath();
        Files.readAllBytes(path);
    }

    /** File(URI) - the URI-shaped constructor feeds the same slot. */
    @GetMapping("/from-uri/unsafe")
    public void fileFromUriUnsafe(@RequestParam String input) throws IOException {
        File file = new File(URI.create(input));
        new FileInputStream(file.getPath()).close();
    }

    @GetMapping("/from-uri/safe")
    public void fileFromUriSafe(@RequestParam String input) throws IOException {
        File file = new File(URI.create("file:///var/www/uploads/release-notes.txt"));
        new FileInputStream(file.getPath()).close();
    }

    /** FileSystem#getPath(String, String...) - the trailing segments arrive as an array element. */
    @GetMapping("/file-system-get-path/unsafe")
    public void fileSystemGetPathUnsafe(@RequestParam String input) throws IOException {
        Path path = FileSystems.getDefault().getPath(BASE_DIR, input);
        Files.readAllBytes(path);
    }

    @GetMapping("/file-system-get-path/safe")
    public void fileSystemGetPathSafe(@RequestParam String input) throws IOException {
        Path path = FileSystems.getDefault().getPath(BASE_DIR, CONSTANT);
        Files.readAllBytes(path);
    }

    /** File#toString() is the accessor most call sites use implicitly. */
    @GetMapping("/to-string/unsafe")
    public void fileToStringUnsafe(@RequestParam String input) throws IOException {
        File file = new File(BASE_DIR, input);
        new FileInputStream(file.toString()).close();
    }

    @GetMapping("/to-string/safe")
    public void fileToStringSafe(@RequestParam String input) throws IOException {
        File file = new File(BASE_DIR, CONSTANT);
        new FileInputStream(file.toString()).close();
    }
}

package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PathTraversalTest {

    @PositiveRuleSample(value = "java/security/stirling-path-traversal.yaml", id = "stirling-path-traversal")
    @PostMapping("/upload-vulnerable")
    public String vulnerable(@RequestParam MultipartFile file) throws IOException {
        // Directly use original filename — path traversal possible
        String filename = file.getOriginalFilename();
        Path dest = Paths.get("/uploads/" + filename);
        Files.copy(file.getInputStream(), dest);
        return "uploaded";
    }

    @NegativeRuleSample(value = "java/security/stirling-path-traversal.yaml", id = "stirling-path-traversal")
    @PostMapping("/upload-safe")
    public String safe(@RequestParam MultipartFile file) throws IOException {
        // Use sanitized filename — only the base name, no path components
        String filename = new File(file.getOriginalFilename()).getName();
        Path dest = Paths.get("/uploads/").resolve(filename);
        Files.copy(file.getInputStream(), dest);
        return "uploaded";
    }
}

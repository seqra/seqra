package custom.stirling;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/convert")
public class ConvertEmlToPDF {
    private String htmlEscape(String value) {
        return value;
    }

    @PostMapping("/eml/pdf")
    public String convertEmlToPdf(@ModelAttribute EmlToPdfRequest request) {

        MultipartFile inputFile = request.getFileInput();
        String originalFilename = inputFile.getOriginalFilename();

        String sink = ResponseEntity.body(originalFilename.getBytes(StandardCharsets.UTF_8));

        return sink;
    }

    @PostMapping("/eml/pdf2")
    public String convertEmlToPdf2(@ModelAttribute EmlToPdfRequest request) {

        MultipartFile inputFile = request.getFileInput();
        String originalFilename = inputFile.getOriginalFilename();
        String escapedFilename = htmlEscape(originalFilename);

        String sink = ResponseEntity.body(originalFilename.getBytes(StandardCharsets.UTF_8));

        return sink;
    }


    @PostMapping("/eml/pdf3")
    public String convertEmlToPdf3(@ModelAttribute EmlToPdfRequest request) {

        MultipartFile inputFile = request.getFileInput();
        String originalFilename = inputFile.getOriginalFilename();
        String escapedFilename = htmlEscape(originalFilename);

        String sink = ResponseEntity.body(escapedFilename.getBytes(StandardCharsets.UTF_8));

        return sink;
    }
}

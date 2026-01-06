package custom.stirling;

import org.springframework.web.multipart.MultipartFile;

public class EmlToPdfRequest {
    private MultipartFile fileInput;

    public MultipartFile getFileInput() {
        return fileInput;
    }
}

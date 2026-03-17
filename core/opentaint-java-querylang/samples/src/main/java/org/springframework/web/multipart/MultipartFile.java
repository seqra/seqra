package org.springframework.web.multipart;

public class MultipartFile {
    private String fileName;
    public MultipartFile(String fileName) {
        this.fileName = fileName;
    }
    public String getOriginalFilename() {
        return fileName;
    }
}

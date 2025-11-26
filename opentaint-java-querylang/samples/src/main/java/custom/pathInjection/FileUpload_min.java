package custom.pathInjection;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Controller
@RequestMapping("/file")
public class FileUpload_min {
    private static final String UPLOADED_FOLDER = "/tmp/";

    @PostMapping("/upload/picture")
    @ResponseBody
    public String uploadPicture(@RequestParam("file") MultipartFile multifile) {
        String fileName = multifile.getOriginalFilename();
        String filePath = UPLOADED_FOLDER + fileName;
        deleteFile(filePath);
        return String.format("You successfully uploaded '%s'", filePath);
    }

    private void deleteFile(String filePath) {
        File delFile = new File(filePath);
        delFile.delete();
    }
}

package custom;

import base.RuleSample;
import custom.pathTraversal.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

public abstract class springPathTraversal1 implements RuleSample {
    static class PositiveUploadFile extends springPathTraversal1 {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }

}

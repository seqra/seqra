package custom;

import base.RuleSample;
import custom.pathTraversal.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

public abstract class springPathTraversalOrigin implements RuleSample {
    static class PositiveUploadFile extends springPathTraversalOrigin {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }

}

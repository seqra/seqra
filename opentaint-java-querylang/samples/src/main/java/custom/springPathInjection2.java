package custom;

import base.RuleSample;
import custom.pathInjection.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

import base.RuleSet;

@RuleSet("custom/springPathInjection2.yaml")
public abstract class springPathInjection2 implements RuleSample {
    static class PositiveUploadFile extends springPathInjection2 {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }

}

package custom;

import base.RuleSample;
import custom.pathInjection.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

import base.RuleSet;

@RuleSet("custom/springPathInjection1.yaml")
public abstract class springPathInjection1 implements RuleSample {
    static class PositiveUploadFile extends springPathInjection1 {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }

}

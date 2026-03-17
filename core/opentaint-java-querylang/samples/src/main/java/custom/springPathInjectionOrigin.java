package custom;

import base.RuleSample;
import custom.pathInjection.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

import base.RuleSet;

@RuleSet("custom/springPathInjectionOrigin.yaml")
public abstract class springPathInjectionOrigin implements RuleSample {
    static class PositiveUploadFile extends springPathInjectionOrigin {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }

}

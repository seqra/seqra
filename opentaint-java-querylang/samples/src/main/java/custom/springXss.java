package custom;

import base.RuleSample;
import custom.pathInjection.FileUpload_min;
import org.springframework.web.multipart.MultipartFile;

import base.RuleSet;

@RuleSet("custom/springXss.yaml")
public abstract class springXss implements RuleSample {
    static class PositiveCommandInject extends springXss {
        @Override
        public void entrypoint() {
            new FileUpload_min().uploadPicture(new MultipartFile(""));
        }
    }
}

package custom;

import base.RuleSample;
import base.RuleSet;
import custom.stirling.ConvertEmlToPDF;
import custom.stirling.EmlToPdfRequest;
import example.util.CustomType1;
import example.util.CustomType2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RuleSet("custom/springXssSanitizedMin.yaml")
public abstract class springXssSanitizedMin implements RuleSample {
    CustomType2 sanitize(CustomType2 data) {
        return data;
    }

    static class PositiveConvertEmlToPDF1 extends springXssSanitizedMin {
        @Override
        public void entrypoint() {
            method(new CustomType1());
        }

        private CustomType2 method(CustomType1 src) {
            return src.mkType2();
        }
    }

    static class PositiveConvertEmlToPDF2 extends springXssSanitizedMin {
        @Override
        public void entrypoint() {
            method(new CustomType1());
        }

        private CustomType2 method(CustomType1 src) {
            CustomType2 sink = src.mkType2();
            CustomType2 sanitized = sanitize(sink);
            return sink;
        }
    }

    static class NegativeConvertEmlToPDF extends springXssSanitizedMin {
        @Override
        public void entrypoint() {
            method(new CustomType1());
        }

        private CustomType2 method(CustomType1 src) {
            CustomType2 sink = src.mkType2();
            CustomType2 sanitized = sanitize(sink);
            return sanitized;
        }
    }
}

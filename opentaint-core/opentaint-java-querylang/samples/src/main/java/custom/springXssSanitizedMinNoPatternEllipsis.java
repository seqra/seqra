package custom;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;

@RuleSet("custom/springXssSanitizedMinNoPatternEllipsis.yaml")
public abstract class springXssSanitizedMinNoPatternEllipsis implements RuleSample {
    CustomType2 sanitize(CustomType2 data) {
        return data;
    }

    static class PositiveConvertEmlToPDF1 extends springXssSanitizedMinNoPatternEllipsis {
        @Override
        public void entrypoint() {
            method(new CustomType1());
        }

        private CustomType2 method(CustomType1 src) {
            return src.mkType2();
        }
    }

    static class PositiveConvertEmlToPDF2 extends springXssSanitizedMinNoPatternEllipsis {
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

    static class NegativeConvertEmlToPDF extends springXssSanitizedMinNoPatternEllipsis {
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

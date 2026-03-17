package custom;

import base.RuleSample;
import base.RuleSet;
import custom.stirling.ConvertEmlToPDF;
import custom.stirling.EmlToPdfRequest;

@RuleSet("custom/springXssSanitized.yaml")
public abstract class springXssSanitized implements RuleSample {
    static class PositiveConvertEmlToPDF1 extends springXssSanitized {
        @Override
        public void entrypoint() {
            new ConvertEmlToPDF().convertEmlToPdf(new EmlToPdfRequest());
        }
    }

    static class PositiveConvertEmlToPDF2 extends springXssSanitized {
        @Override
        public void entrypoint() {
            new ConvertEmlToPDF().convertEmlToPdf2(new EmlToPdfRequest());
        }
    }

    static class NegativeConvertEmlToPDF extends springXssSanitized {
        @Override
        public void entrypoint() {
            new ConvertEmlToPDF().convertEmlToPdf3(new EmlToPdfRequest());
        }
    }
}

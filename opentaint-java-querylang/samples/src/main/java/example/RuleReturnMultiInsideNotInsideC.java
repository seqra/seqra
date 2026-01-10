package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleReturnMultiInsideNotInsideC.yaml")
public abstract class RuleReturnMultiInsideNotInsideC implements RuleSample {
    static class Positive extends RuleReturnMultiInsideNotInsideC {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 t1 = src.mkType2();
            CustomType3 sink = t1.mkType3();
            return sink;
        }
    }

    static class Negative extends RuleReturnMultiInsideNotInsideC {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 t1 = src.mkType2();
            sanitizeC(t1);
            CustomType3 sink = t1.mkType3();
            return sink;
        }

        private void sanitizeC(Object o) {}
    }

    static class Negative2 extends RuleReturnMultiInsideNotInsideC {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 t1 = src.mkType2();
            CustomType3 sink = t1.mkType3();
            sanitizeC(sink);
            return sink;
        }

        private void sanitizeC(Object o) {}
    }


    static class Positive2 extends RuleReturnMultiInsideNotInsideC {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType3 simple(CustomType1 src) {
            CustomType2 t1 = src.mkType2();
            CustomType3 sink = t1.mkType3();
            sanitizeC(src);
            return sink;
        }

        private void sanitizeC(Object o) {}
    }
}
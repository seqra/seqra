package example;

import base.RuleSample;
import base.RuleSet;
import java.util.List;
import java.util.Map;

@RuleSet("example/RuleWithTwoArgGeneric.yaml")
public abstract class RuleWithTwoArgGeneric implements RuleSample {

    void sink(String data) {}

    void methodWithStringObjectMap(Map<String, Object> m, String data) {
        sink(data);
    }

    void methodWithStringStringMap(Map<String, String> m, String data) {
        sink(data);
    }

    void methodWithList(List<String> m, String data) {
        sink(data);
    }

    final static class PositiveStringObjectMap extends RuleWithTwoArgGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, Object> m = null;
            methodWithStringObjectMap(m, data);
        }
    }

    final static class PositiveStringStringMap extends RuleWithTwoArgGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            Map<String, String> m = null;
            methodWithStringStringMap(m, data);
        }
    }

    final static class NegativeListNotMap extends RuleWithTwoArgGeneric {
        @Override
        public void entrypoint() {
            String data = "tainted";
            List<String> m = null;
            methodWithList(m, data);
        }
    }
}

package taint;

import base.RuleSample;

public abstract class RuleWithPass implements RuleSample {
    String src() {
        return "tainted string";
    }

    String pass(String src) {
        return "string copy";
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends RuleWithPass {
        @Override
        public void entrypoint() {
            String data = src();
            String other = pass(data);
            sink(other);
        }
    }
}

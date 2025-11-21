package taint;

import base.RuleSample;

public abstract class Rule implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }
}

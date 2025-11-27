package taint;

import base.RuleSample;

import base.RuleSet;

@RuleSet("taint/RuleNoMeta.yaml")
public abstract class RuleNoMeta implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends RuleNoMeta {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }
}

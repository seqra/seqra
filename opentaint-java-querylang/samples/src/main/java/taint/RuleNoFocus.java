package taint;

import base.RuleSample;

import base.RuleSet;

@RuleSet("taint/RuleNoFocus.yaml")
public abstract class RuleNoFocus implements RuleSample {
    String src() {
        return "tainted string";
    }

    void sink(String data) {

    }

    final static class PositiveSimple extends RuleNoFocus {
        @Override
        public void entrypoint() {
            String data = src();
            sink(data);
        }
    }
}

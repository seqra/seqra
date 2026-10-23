package taint;

import base.RuleSample;
import base.RuleSet;

@RuleSet("taint/StarSink.yaml")
public abstract class StarSink implements RuleSample {
    String src() { return "tainted"; }
    static final class Box { String value; }
    void sink(Box b) {}

    final static class PositiveTaintedField extends StarSink {
        @Override public void entrypoint() {
            String data = src();
            Box b = new Box();
            b.value = data;   // taints a field
            sink(b);          // $*Y sink fires on tainted field
        }
    }

    final static class NegativeCleanObject extends StarSink {
        @Override public void entrypoint() {
            Box b = new Box();
            b.value = "safe";
            sink(b);
        }
    }
}

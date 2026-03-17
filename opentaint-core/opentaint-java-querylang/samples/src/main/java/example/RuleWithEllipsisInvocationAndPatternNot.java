package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithEllipsisInvocationAndPatternNot.yaml")
public abstract class RuleWithEllipsisInvocationAndPatternNot implements RuleSample {
    Inner src() {
        return new Inner(new Object());
    }

    void sink(String data) {}

    final static class Positive extends RuleWithEllipsisInvocationAndPatternNot {
        @Override
        public void entrypoint() {
            Inner data = src();
            String str = data.getObjGood().toString();
            sink(str);
        }
    }

    final static class Negative extends RuleWithEllipsisInvocationAndPatternNot {
        @Override
        public void entrypoint() {
            Inner data = src();
            String str = data.getObjBad().toString();
            sink(str);
        }
    }

    static final private class Inner {
        final private Object obj;

        public Inner(Object obj) {
            this.obj = obj;
        }

        public Object getObjGood() {
            return obj;
        }

        public Object getObjBad() {
            return obj;
        }
    }
}
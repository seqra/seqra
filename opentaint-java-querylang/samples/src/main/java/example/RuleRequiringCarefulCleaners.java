package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleRequiringCarefulCleaners.yaml")
public abstract class RuleRequiringCarefulCleaners implements RuleSample {
    Inner src() {
        return new Inner(new Object());
    }

    void sink(Object data) {}

    final static class Positive extends RuleRequiringCarefulCleaners {
        @Override
        public void entrypoint() {
            Inner data = src();
            Object str = data.getObjBad();
            str = data.getObjGood();
            sink(str);
        }
    }

    final static class Negative extends RuleRequiringCarefulCleaners {
        @Override
        public void entrypoint() {
            Inner data = src();
            Object str = data.getObjGood();
            str = data.getObjBad();
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

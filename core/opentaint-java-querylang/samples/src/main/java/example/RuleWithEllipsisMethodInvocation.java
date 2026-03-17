package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/RuleWithEllipsisMethodInvocation.yaml")
public abstract class RuleWithEllipsisMethodInvocation implements RuleSample {
    Inner src() {
        return new Inner(new Object());
    }

    void sink(String data) {}

    final static class PositiveOneCall extends RuleWithEllipsisMethodInvocation {
        @Override
        public void entrypoint() {
            Inner data = src();
            String str = data.getObjGood().toString();
            sink(str);
        }
    }

    final static class PositiveZeroCalls extends RuleWithEllipsisMethodInvocation {
        @Override
        public void entrypoint() {
            Inner data = src();
            String str = data.toString();
            sink(str);
        }
    }

    final static class NegativeTwoCalls extends RuleWithEllipsisMethodInvocation {
        @Override
        public void entrypoint() {
            Inner data = src();
            String str = data.getObjGood().getClass().toString();
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
    }
}


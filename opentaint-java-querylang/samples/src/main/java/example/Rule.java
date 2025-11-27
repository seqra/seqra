package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/Rule.yaml")
public abstract class Rule implements RuleSample {
    String src() {
        return "tainted string";
    }

    void clean(String data) {

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

    public static class StringContainer {
        private String value;

        public final String getValue(){
            return value;
        }

        public void setValue(String value){
            this.value = value;
        }
    }

    final static class PositiveSimpleWithContainer extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            StringContainer container = new StringContainer();
            container.setValue(data);
            sink(container.getValue());
        }
    }

    final static class PositiveWithEllipsis extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            System.out.println(data);
            sink(data);
        }
    }

    final static class PositiveIterProc extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            sinkWrapper(data);
        }

        void sinkWrapper(String data) {
            sink(data);
        }
    }

    final static class NegativeSimple extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            clean(data);
            sink(data);
        }
    }

    final static class NegativeWithEllipsis extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            System.out.println(data);
            clean(data);
            sink(data);
        }
    }

    final static class NegativeIterProc extends Rule {
        @Override
        public void entrypoint() {
            String data = src();
            sinkWrapper(data);
        }

        void sinkWrapper(String data) {
            clean(data);
            sink(data);
        }
    }
}

package example;

import base.EntryPoint;
import base.RuleSample;
import base.RuleSet;

@RuleSet("example/ArrayExample.yaml")
public abstract class ArrayExample implements RuleSample {
    public String[] src() {
        return new String[]{"data"};
    }

    public void arraySink(String[] data) {
    }

    public void elementSink(String data) {
    }

    public void otherElementSink(String data) {
    }

    static class PositiveEpArray extends ArrayExample {
        @Override
        public void entrypoint() {
            method(new String[]{"tainted"});
        }

        @EntryPoint
        public void method(String[] data) {
            arraySink(data);
        }
    }

//    todo: array access
//    static class PositiveEpElement extends ArrayExample {
//        @Override
//        public void entrypoint() {
//            method(new String[]{"tainted"});
//        }
//
//        @EntryPoint
//        public void method(String[] data) {
//            elementSink(data[0]);
//        }
//    }

    static class PositiveEpElementGuessed extends ArrayExample {
        @Override
        public void entrypoint() {
            method(new String[]{"tainted"});
        }

        @EntryPoint
        public void method(String[] data) {
            otherElementSink(data[0]);
        }
    }
//
    static class PositiveSrcArray extends ArrayExample {
        @Override
        public void entrypoint() {
            method();
        }

        public void method() {
            String[] data = src();
            arraySink(data);
        }
    }

//    todo: array access
//    static class PositiveSrcElement extends ArrayExample {
//        @Override
//        public void entrypoint() {
//            method();
//        }
//
//        public void method() {
//            String[] data = src();
//            elementSink(data[0]);
//        }
//    }

    static class PositiveSrcElementGuessed extends ArrayExample {
        @Override
        public void entrypoint() {
            method();
        }

        public void method() {
            String[] data = src();
            otherElementSink(data[0]);
        }
    }

    static class NegativeEpElement extends ArrayExample {
        @Override
        public void entrypoint() {
            method("tainted");
        }

        @EntryPoint
        public void method(String data) {
            elementSink(data);
        }
    }
}

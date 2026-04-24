package issues;

import base.RuleSample;
import base.RuleSet;
import issues.i99.DataHoarder;
import issues.i99.FakeResponse;
import issues.i99.Response;

@RuleSet("issues/issue99.yaml")
public abstract class issue99 implements RuleSample {
    //protected String source() { return "joinful"; }

    protected DataHoarder source() { return new DataHoarder(); }

    protected void fakeSink(String data) {}

    protected void fakeSink(DataHoarder dh) {
        fakeSink(dh.field1);
        fakeSink(dh.field2);
        fakeSink(dh.field3);
        fakeSink(dh.field4);
        fakeSink(dh.field5);
    }

    static class PositiveTaint extends issue99 {
        @Override
        public void entrypoint() {
            DataHoarder dh = source();
            Response r = new Response();
            r.getTaker().write(dh.field2);
        }
    }

    static class NegativeResponseTaint extends issue99 {
        @Override
        public void entrypoint() {
            DataHoarder dh = source();
            FakeResponse r = new FakeResponse();
            Object a = dh.field2;
            r.getTaker().write(a);
        }
    }

    protected DataHoarder.DH4 deepSource() {
        DataHoarder dh = source();
        return new DataHoarder.DH4(new DataHoarder.DH3(new DataHoarder.DH2(new DataHoarder.DH1(dh))));
    }

    static class PositiveDeepTaint extends issue99 {
        @Override
        public void entrypoint() {
            Object dh = deepSource();
            mkResponse(dh);
        }

        public void mkResponse(Object dh) {
            Response r = new Response();
            r.getTaker().write(dh);
        }
    }

    static class NegativeResponseDeepTaint extends issue99 {
        @Override
        public void entrypoint() {
            Object dh = deepSource();
            mkResponse(dh);
        }

        public void mkResponse(Object dh) {
            FakeResponse r = new FakeResponse();
            r.getTaker().write(dh);
        }
    }

    static class NegativeTaint extends issue99 {
        @Override
        public void entrypoint() {
            DataHoarder dh = source();
            fakeSink(dh);
        }
    }
}

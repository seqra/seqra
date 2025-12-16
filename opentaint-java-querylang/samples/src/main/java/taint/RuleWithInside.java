package taint;

import base.RuleSample;
import base.RuleSet;

@RuleSet("taint/RuleWithInside.yaml")
public abstract class RuleWithInside implements RuleSample {

    String src() {
        return "tainted data";
    }

    static class XClass {
        RClass getRequestDispatcher() {
            return new RClass();
        }
    }

    static class RClass {
        void include(String f, String s) {

        }
    }

    static class PositiveSimple extends RuleWithInside {
        @Override
        public void entrypoint() {
            String data = src();
            XClass x = new XClass();
            RClass r = x.getRequestDispatcher();
            r.include(data, "const");
        }
    }
}

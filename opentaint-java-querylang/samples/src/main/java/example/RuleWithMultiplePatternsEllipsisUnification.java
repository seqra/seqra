package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CustomType1;
import example.util.CustomType2;
import example.util.CustomType3;

@RuleSet("example/RuleWithMultiplePatternsEllipsisUnification.yaml")
public abstract class RuleWithMultiplePatternsEllipsisUnification implements RuleSample {
    static class PositiveSimple extends RuleWithMultiplePatternsEllipsisUnification {
        @Override
        public void entrypoint() {
            simple(new CustomType1());
        }

        private CustomType1 simple(CustomType1 src) {
            CustomType2 v2 = src.mkType2();
            CustomType3 v3 = v2.mkType3();
            CustomType1 sink = CustomType1.mkType1FromType3(v3);
            return sink;
        }
    }
}

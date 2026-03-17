package example;

import base.IFDSFalsePositive;
import base.RuleSample;
import base.RuleSet;
import example.util.BarInterface;
import example.util.OtherType;
import example.util.SimpleType;

@RuleSet("example/RuleWithInterfaceType.yaml")
public abstract class RuleWithInterfaceType implements RuleSample {
    public static class PositiveSample extends RuleWithInterfaceType {
        @Override
        public void entrypoint() {
            f(new SimpleType());
        }

        private void f(SimpleType o) {
            BarInterface x = o;
            x.bar();
        }
    }

    @IFDSFalsePositive("Virtual call on unknown concrete type")
    public static class NegativeSample extends RuleWithInterfaceType {
        @Override
        public void entrypoint() {
            f(new OtherType());
        }

        private void f(OtherType o) {
            BarInterface x = o;
            x.bar();
        }
    }
}

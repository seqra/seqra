package example;

import base.RuleSample;
import base.RuleSet;
import example.util.CleanableData;

@RuleSet("example/MustAliasExample.yaml")
public abstract class MustAliasExample implements RuleSample {
    protected CleanableData data;

    protected void cleanData(CleanableData data) {
        data.cleanData();
    }

    protected void doNothing(CleanableData data) { }

    static class NegativeSimpleFlowTaint extends MustAliasExample {
        @Override
        public void entrypoint() {
            this.data = new CleanableData();
            this.data.cleanData();
            this.data.sendInfo();
        }
    }

    static class PositiveSimpleFlowTaint extends MustAliasExample {
        @Override
        public void entrypoint() {
            this.data = new CleanableData();
            this.data.sendInfo();
        }
    }

    static class NegativeMethodFlowTaint extends MustAliasExample {
        @Override
        public void entrypoint() {
            CleanableData obj = new CleanableData();
            CleanableData obj2 = obj;
            cleanData(obj2);
            obj.sendInfo();
        }
    }

    static class PositiveMethodFlowTaint extends MustAliasExample {
        @Override
        public void entrypoint() {
            CleanableData obj = new CleanableData();
            CleanableData obj2 = obj;
            if (obj.info.length() > 5) {
                cleanData(obj2);
            }
            obj.sendInfo();
        }
    }

    static class PositiveMethodFlow2Taint extends MustAliasExample {
        @Override
        public void entrypoint() {
            CleanableData obj = new CleanableData();
            doNothing(obj);
            obj.sendInfo();
        }
    }
}

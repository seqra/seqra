package custom;

import base.RuleSample;
import custom.commandInjection.CommandInject_min;

import base.RuleSet;

@RuleSet("custom/springCommandInjection1.yaml")
public abstract class springCommandInjection1 implements RuleSample {
    static class PositiveCommandInject extends springCommandInjection1 {
        @Override
        public void entrypoint() {
            new CommandInject_min().codeInject("");
        }
    }
}

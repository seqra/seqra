package custom;

import base.RuleSample;
import custom.commandInjection.CommandInject_min;

import base.RuleSet;

@RuleSet("custom/springCommandInjectionOrigin.yaml")
public abstract class springCommandInjectionOrigin implements RuleSample {
    static class NegativeCommandInject extends springCommandInjectionOrigin {
        @Override
        public void entrypoint() {
            new CommandInject_min().codeInjectSec("");
        }
    }

    static class PositiveCommandInject extends springCommandInjectionOrigin {
        @Override
        public void entrypoint() {
            new CommandInject_min().codeInject("");
        }
    }
}

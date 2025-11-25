package custom;

import base.RuleSample;
import custom.commandInjection.CommandInject_min;

public abstract class springCommandInjection1 implements RuleSample {
    static class PositiveCommandInject extends springCommandInjection1 {
        @Override
        public void entrypoint() {
            new CommandInject_min().codeInject("");
        }
    }
}

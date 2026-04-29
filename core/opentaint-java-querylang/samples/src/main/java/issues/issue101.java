package issues;

import base.RuleSample;
import base.RuleSet;

@RuleSet("issues/issue101.yaml")
public abstract class issue101 implements RuleSample {
    // Tainted parameter returned via string concatenation. The same
    // rule fires when the body is `return name;` directly; only the
    // concat form fails. Drove the 23 false negatives that the XSS
    // html-response rule rewrite then partially fixed.
    static class PositiveConcatReturn extends issue101 {
        public String handler(String name) {
            return name + "!";
        }

        @Override
        public void entrypoint() {
            handler("attacker-controlled");
        }
    }
}

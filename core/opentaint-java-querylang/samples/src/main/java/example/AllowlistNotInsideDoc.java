package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Doc validation: pattern-not-inside excludes when the positively produced
 * client is the receiver of the safe-configuration call.
 */
@RuleSet("example/AllowlistNotInsideDoc.yaml")
public abstract class AllowlistNotInsideDoc implements RuleSample {

    static class Client {
        static Client builder() { return new Client(); }
        void allowHost(String host) {}
        void connect(String url) {}
    }

    static class Positive extends AllowlistNotInsideDoc {
        @Override
        public void entrypoint() {
            Client c = Client.builder();
            c.connect("http://example.com");
        }
    }

    static class Negative extends AllowlistNotInsideDoc {
        @Override
        public void entrypoint() {
            Client c = Client.builder();
            c.allowHost("trusted.example");
            c.connect("http://example.com");
        }
    }
}

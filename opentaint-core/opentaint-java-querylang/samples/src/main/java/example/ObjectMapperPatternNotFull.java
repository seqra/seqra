package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Tests pattern-not with full ObjectMapper initialization.
 * Pattern-not requires: $MAPPER = new ObjectMapper(); ... $MAPPER.enable(...); ... $MAPPER.foo("JSON");
 */
@RuleSet("example/ObjectMapperPatternNotFull.yaml")
public abstract class ObjectMapperPatternNotFull implements RuleSample {

    // Simulated ObjectMapper API
    static class ObjectMapper {
        ObjectMapper enableDefaultTyping() { return this; }
        void enable() {}
        void foo(String json) {}
    }

    static class Positive extends ObjectMapperPatternNotFull {
        @Override
        public void entrypoint() {
            // Should match: has enableDefaultTyping but no enable() call
            ObjectMapper mapper = new ObjectMapper();
            mapper = mapper.enableDefaultTyping();
            mapper.foo("JSON");
        }
    }

    static class Negative extends ObjectMapperPatternNotFull {
        @Override
        public void entrypoint() {
            // Should NOT match: has both enableDefaultTyping and enable() - excluded by pattern-not
            ObjectMapper mapper = new ObjectMapper();
            mapper = mapper.enableDefaultTyping();
            mapper.enable();
            mapper.foo("JSON");
        }
    }
}

package example;

import base.RuleSample;
import base.RuleSet;

/**
 * Tests pattern-not with ellipsis prefix (matches enable() anywhere before foo()).
 * Pattern-not requires: ... $MAPPER.enable(...); ... $MAPPER.foo("JSON");
 */
@RuleSet("example/ObjectMapperPatternNotEllipsis.yaml")
public abstract class ObjectMapperPatternNotEllipsis implements RuleSample {

    // Simulated ObjectMapper API
    static class ObjectMapper {
        ObjectMapper enableDefaultTyping() { return this; }
        void enable() {}
        void foo(String json) {}
    }

    static class Positive extends ObjectMapperPatternNotEllipsis {
        @Override
        public void entrypoint() {
            // Should match: has enableDefaultTyping but no enable() call
            ObjectMapper mapper = new ObjectMapper();
            mapper = mapper.enableDefaultTyping();
            mapper.foo("JSON");
        }
    }

    static class Negative extends ObjectMapperPatternNotEllipsis {
        @Override
        public void entrypoint() {
            // Should NOT match: has enable() before foo() - excluded by pattern-not with ellipsis
            ObjectMapper mapper = new ObjectMapper();
            mapper = mapper.enableDefaultTyping();
            mapper.enable();
            mapper.foo("JSON");
        }
    }
}

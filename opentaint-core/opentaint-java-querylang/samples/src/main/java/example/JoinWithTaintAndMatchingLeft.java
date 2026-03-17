package example;

import base.RuleSample;
import base.RuleSet;

@RuleSet("example/JoinWithTaintAndMatchingLeft.yaml")
public abstract class JoinWithTaintAndMatchingLeft implements RuleSample {

    // Stub methods for pattern matching
    static Object createInitialData() { return null; }
    static String getUntrustedInput() { return null; }
    static String processInput(String x) { return null; }
    static String transformData(String x) { return null; }
    static void executeDangerous(Object target) {}

    /**
     * Positive case: matching rule left side.
     * Data flows from createInitialData() to executeDangerous().
     */
    static class PositiveMatchingFlow extends JoinWithTaintAndMatchingLeft {
        @Override
        public void entrypoint() {
            Object data = createInitialData();
            executeDangerous(data);
        }
    }

    /**
     * Positive case: taint rule left side with full label chain.
     * Data flows through UNTRUSTED -> PROCESSED -> transformed -> sink.
     */
    static class PositiveTaintFlow extends JoinWithTaintAndMatchingLeft {
        @Override
        public void entrypoint() {
            String untrusted = getUntrustedInput();
            String processed = processInput(untrusted);
            String result = transformData(processed);
            executeDangerous(result);
        }
    }

    /**
     * Negative case: taint flow missing UNTRUSTED label.
     * processInput requires UNTRUSTED, but input is not from getUntrustedInput().
     */
    static class NegativeMissingUntrustedLabel extends JoinWithTaintAndMatchingLeft {
        @Override
        public void entrypoint() {
            String safe = "safe";
            String processed = processInput(safe);
            String result = transformData(processed);
            executeDangerous(result);
        }
    }

    /**
     * Negative case: taint flow missing PROCESSED label.
     * transformData requires PROCESSED, but input is directly from getUntrustedInput().
     */
    static class NegativeMissingProcessedLabel extends JoinWithTaintAndMatchingLeft {
        @Override
        public void entrypoint() {
            String untrusted = getUntrustedInput();
            String result = transformData(untrusted);
            executeDangerous(result);
        }
    }

    /**
     * Negative case: no dangerous execution.
     * Data is created but never passed to executeDangerous().
     */
    static class NegativeNoSink extends JoinWithTaintAndMatchingLeft {
        @Override
        public void entrypoint() {
            Object data = createInitialData();
            System.out.println(data);
        }
    }
}

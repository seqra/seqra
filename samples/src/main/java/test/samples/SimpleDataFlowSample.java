package test.samples;

public class SimpleDataFlowSample {
    public void simpleDataFlow() {
        String data = source();
        String processed = process(data);
        sink(processed);
    }

    private String process(String input) {
        return input;
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

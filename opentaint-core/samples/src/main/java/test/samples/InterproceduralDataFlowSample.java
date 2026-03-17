package test.samples;

public class InterproceduralDataFlowSample {
    public void interproceduralDataFlow() {
        String data = source();
        String validated = validate(data);
        String formatted = format(validated);
        sink(formatted);
    }

    private String validate(String input) {
        return input;
    }

    private String format(String input) {
        return input;
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

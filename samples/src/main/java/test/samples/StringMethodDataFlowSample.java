package test.samples;

public class StringMethodDataFlowSample {

    public void substringFlow() {
        String data = source();
        String sub = data.substring(1);
        sink(sub);
    }

    public void toLowerCaseFlow() {
        String data = source();
        String lower = data.toLowerCase();
        sink(lower);
    }

    public void trimFlow() {
        String data = source();
        String trimmed = data.trim();
        sink(trimmed);
    }

    public void concatFlow() {
        String data = source();
        String result = data.concat(" suffix");
        sink(result);
    }

    public void replaceFlow() {
        String data = source();
        String replaced = data.replace("a", "b");
        sink(replaced);
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

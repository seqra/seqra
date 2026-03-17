package test.samples;

public class BranchLoopDataFlowSample {
    public void branchLoopDataFlow(boolean flag) {
        String data = source();
        String processed;
        if (flag) {
            processed = data;
        } else {
            processed = data;
        }
        String result = loopProcess(processed);
        sink(result);
    }

    private String loopProcess(String input) {
        return input;
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

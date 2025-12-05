public class TaintExamples {
    private String source() {
        return "tainted data";
    }

    private void sink(String data) {
        System.out.println("data = \"" + data + "\"");
    }

    public void bad() {
        String data = "good data";
        try {
            data = source();
            throw new Exception("error");
        } catch (Exception e) {
            sink(data);
        }
    }
}

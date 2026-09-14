package test.samples;

public class AnyFieldInterproceduralSample {
    public static class Path {
        public String value;
    }

    public static class File {
        public Path path;
    }

    public String source() {
        return "tainted";
    }

    public void sink(Path path) { }

    private void consume(Path path) {
        sink(path);
    }

    private void readFile(File file) {
        consume(file.path);
    }

    private void store(String value) {
        File file = new File();
        file.path = new Path();
        file.path.value = value;

        // Keeping this write and the sink in different summarized callees makes readFile's
        // initial fact expose the single-field shape before it exposes the taint mark.
        readFile(file);
    }

    public void fieldFlow() {
        store(source());
    }
}

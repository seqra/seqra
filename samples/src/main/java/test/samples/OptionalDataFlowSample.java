package test.samples;

import java.util.Optional;

public class OptionalDataFlowSample {

    public void optionalOfGetFlow() {
        String data = source();
        Optional<String> opt = Optional.of(data);
        String retrieved = opt.get();
        sink(retrieved);
    }

    public void optionalMapFlow() {
        String data = source();
        Optional<String> opt = Optional.of(data);
        Optional<String> mapped = opt.map(s -> s.trim());
        String retrieved = mapped.get();
        sink(retrieved);
    }

    public void optionalOrElseFlow() {
        String data = source();
        Optional<String> opt = Optional.of(data);
        String retrieved = opt.orElse("default");
        sink(retrieved);
    }

    public void optionalFlatMapFlow() {
        String data = source();
        Optional<String> opt = Optional.of(data);
        Optional<String> result = opt.flatMap(s -> Optional.of(s.toLowerCase()));
        String retrieved = result.get();
        sink(retrieved);
    }

    public void optionalIfPresentFlow() {
        String data = source();
        Optional<String> opt = Optional.of(data);
        opt.ifPresent(s -> sink(s));
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

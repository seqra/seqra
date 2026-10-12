package test.samples;

import java.util.function.Consumer;

public class LambdaCapturedArrayAliasSample {

    public String source() { return "tainted"; }
    public String sink(String value) { return value; }

    private void emit(Consumer<String> consumer) {
        consumer.accept(source());
    }

    public void sinkInsideLambda() {
        emit(line -> sink(line));
    }

    public void capturedArrayOuterRead() {
        String[] holder = new String[1];
        emit(line -> holder[0] = line);
        sink(holder[0]);
    }
}

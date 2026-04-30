package test.samples;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public class LambdaDataFlowSample {

    public void lambdaIdentityFlow() {
        String data = source();
        Function<String, String> identity = s -> s;
        String result = identity.apply(data);
        sink(result);
    }

    public void lambdaTransformFlow() {
        String data = source();
        UnaryOperator<String> transform = s -> s.toUpperCase();
        String result = transform.apply(data);
        sink(result);
    }

    public void lambdaPassedToMethodFlow() {
        String data = source();
        String result = applyTransform(data, s -> s.trim());
        sink(result);
    }

    private String applyTransform(String input, Function<String, String> fn) {
        return fn.apply(input);
    }

    public String source() { return "tainted"; }
    public String sink(String data) { return data; }

    public void lambdaCaptureFlow() {
        String data = source();
        lambdaCapture(data, s -> sink(s));
    }

    private void lambdaCapture(String input, Function<String, String> fn) {
        Function<String, String> g = (x -> fn.apply(x));
        g.apply(input);
    }
}

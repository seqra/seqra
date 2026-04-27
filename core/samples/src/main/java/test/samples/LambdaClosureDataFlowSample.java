package test.samples;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class LambdaClosureDataFlowSample {

    // Returns a constant — taint from input is always dropped here.
    private String sanitize(String input) {
        return "sanitized";
    }

    // Taint flows through Iterable.forEach into the action lambda body.
    // Arrays.asList returns Arrays$ArrayList whose forEach is not overridden,
    // so the Iterable.forEach approximation is used to propagate taint.
    public void forEachIdentityLambdaTaintFlow() {
        String tainted = source();
        List<String> items = Arrays.asList(tainted);
        items.forEach(item -> sink(item));
    }

    // FP elimination: forEach lambda captures a sanitizing inner lambda.
    // JIRLambdaCapturedFieldsMethodContext resolves the captured sanitizer to the
    // constant-returning sanitize() method, so taint does not reach the sink.
    public void forEachCapturedSanitizingLambdaNoTaintFlow() {
        String tainted = source();
        List<String> items = Arrays.asList(tainted);
        Function<String, String> sanitizer = s -> sanitize(s);
        items.forEach(item -> sink(sanitizer.apply(item)));
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

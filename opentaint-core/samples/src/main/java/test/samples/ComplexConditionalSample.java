package test.samples;

public class ComplexConditionalSample {
    public void complexConditionalFlow() {
        /*cond_taintA:start*/String a = taintA()/*cond_taintA:end:Call to "taintA" puts taintA data to "a"*/;
        /*cond_taintB:start*/String b = taintB()/*cond_taintB:end:Method "taintB" puts taintB data to "b"*/;
        /*cond_copy:start*/String c = copy(a, b)/*cond_copy:end:Method "copy" starts with taintA at "a"; taintB at "b", then puts combined data to "c"*/;
        /*cond_sink:start*/sink(c)/*cond_sink:end:Sink message: complex-cond-rule*/;
    }

    public String taintA() { return "a"; }
    public String taintB() { return "b"; }
    public String copy(String x, String y) { return x + y; }
    public void sink(String data) { }
}

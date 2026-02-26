package test.samples;

public class DeepNestedConditionSample {
    public void deepNestedConditionFlow() {
        /*deep_taintA:start*/String a = taintA()/*deep_taintA:end:Call to "taintA" puts taintA data to "a"*/;
        /*deep_taintB:start*/String b = taintB()/*deep_taintB:end:Method "taintB" puts taintB data to "b"*/;
        /*deep_combine:start*/String combined = combineWithCondition(a, b)/*deep_combine:end:Method "combineWithCondition" starts with taintA at "a"; taintB at "b", then puts combined data to "combined"*/;
        /*deep_outer:start|<$>|deep_outer_prop:start*/String result = outerMethod(combined)/*deep_outer:end:Calling "outerMethod" with combined data at "combined"|<$>|deep_outer_prop:end:Method "outerMethod" propagates combined data from "combined" to "result"*/;
        /*deep_sink:start*/sink(result)/*deep_sink:end:Sink message: deep-nested-rule*/;
    }

    private /*deep_outer_entry:start*/String outerMethod(String x)/*deep_outer_entry:end:Entering "outerMethod" with marked data at the 1st argument of "outerMethod"*/ {
        /*deep_middle:start*/return /*deep_middle_call:start*/middleMethod(x)/*deep_middle_call:end:Calling "middleMethod" with marked data at the 1st argument of "outerMethod"|<$>|deep_middle:end:The returning value is assigned marked data from "middleMethod" call*/;
    /*deep_outer_exit:start*/}/*deep_outer_exit:end:Exiting "outerMethod"*/

    private /*deep_middle_entry:start*/String middleMethod(String x)/*deep_middle_entry:end:Entering "middleMethod" with marked data at the 1st argument of "middleMethod"*/ {
        /*deep_inner:start*/return /*deep_inner_call:start*/innerMethod(x)/*deep_inner_call:end:Calling "innerMethod" with marked data at the 1st argument of "middleMethod"|<$>|deep_inner:end:The returning value is assigned marked data from "innerMethod" call*/;
    /*deep_middle_exit:start*/}/*deep_middle_exit:end:Exiting "middleMethod"*/

    private String innerMethod(String x) {
        /*deep_inner_body:start*/return x;/*deep_inner_body:end:Takes marked data at the 1st argument of "innerMethod" and ends up with marked data at the returned value*/
    /*deep_inner_exit:start*/}/*deep_inner_exit:end:Exiting "innerMethod"*/

    public String taintA() { return "a"; }
    public String taintB() { return "b"; }
    public String combineWithCondition(String x, String y) { return x + y; }
    public void sink(String data) { }
}

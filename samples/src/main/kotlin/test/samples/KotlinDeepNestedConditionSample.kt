package test.samples

class KotlinDeepNestedConditionSample {
    fun deepNestedConditionFlow() {
        /*deep_taintA:start*/val a = taintA()/*deep_taintA:end:Call to "taintA" puts taintA data to "a"*/
        /*deep_taintB:start*/val b = taintB()/*deep_taintB:end:Method "taintB" puts taintB data to "b"*/
        /*deep_combine:start*/val combined = combineWithCondition(a, b)/*deep_combine:end:Method "combineWithCondition" starts with taintA at "a"; taintB at "b", then puts combined data to "combined"*/
        /*deep_outer:start|<$>|deep_outer_prop:start*/val result = outerMethod(combined)/*deep_outer:end:Calling "outerMethod" with combined data at "combined"|<$>|deep_outer_prop:end:Method "outerMethod" propagates combined data from "combined" to "result"*/
        /*deep_sink:start*/sink(result)/*deep_sink:end:Sink message: deep-nested-rule*/
    }

    private /*deep_outer_entry:start*/fun outerMethod(x: String): String/*deep_outer_entry:end:Entering "outerMethod" with marked data at "x"*/ {
        /*deep_middle:start*/return /*deep_middle_call:start*/middleMethod(x)/*deep_middle_call:end:Calling "middleMethod" with marked data at "x"|<$>|deep_middle:end:The returning value is assigned marked data from "middleMethod" call*/
    /*deep_outer_exit:start*/}/*deep_outer_exit:end:Exiting "outerMethod"*/

    private /*deep_middle_entry:start*/fun middleMethod(x: String): String/*deep_middle_entry:end:Entering "middleMethod" with marked data at "x"*/ {
        /*deep_inner:start*/return /*deep_inner_call:start*/innerMethod(x)/*deep_inner_call:end:Calling "innerMethod" with marked data at "x"|<$>|deep_inner:end:The returning value is assigned marked data from "innerMethod" call*/
    /*deep_middle_exit:start*/}/*deep_middle_exit:end:Exiting "middleMethod"*/

    private fun innerMethod(x: String): String {
        /*deep_inner_body:start*/return x/*deep_inner_body:end:Takes marked data at "x" and ends up with marked data at the returned value*/
    /*deep_inner_exit:start*/}/*deep_inner_exit:end:Exiting "innerMethod"*/

    fun taintA(): String = "a"
    fun taintB(): String = "b"
    fun combineWithCondition(x: String, y: String): String = x + y
    fun sink(data: String) {}
}

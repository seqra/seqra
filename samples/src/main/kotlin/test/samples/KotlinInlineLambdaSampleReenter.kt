package test.samples

class KotlinInlineLambdaSampleReenter {
    fun inlineLambdaFlow() {
        /*inline_source:start*/val a = source()/*inline_source:end:Call to "source" puts tainted data to "a"*/
        /*f1_call:start|<$>|f1_propagate:start*/val b = f(a)/*f1_call:end:Calling "f" with tainted data at "a"|<$>|f1_propagate:end:Method "f" propagates tainted data from "a" to "b"*/
        /*f2_call:start|<$>|f2_propagate:start*/val c = f(b)/*f2_call:end:Calling "f" with tainted data at "b"|<$>|f2_propagate:end:Method "f" propagates tainted data from "b" to "c"*/
        /*inline_sink:start*/sink(c)/*inline_sink:end:Sink message: inline-lambda-reenter-rule*/
    }

    /*f_enter:start*/fun f(x: String): String/*f_enter:end:Entering "f" with marked data at "x"*/ {
        val /*i1:start*/res/*i1:end:Inline method "buildIt" inserted*/ = buildIt {
            /*inline_call:start|<$>|inline_propagate:start*/val y = x.transform()/*inline_call:end:Calling "transform" with marked data at "x"|<$>|inline_propagate:end:Method "transform" propagates marked data from "x" to "y"*/
/*inline_pass:start|<$>|inline_pass_propagate:start*/val z = pass(y)/*inline_pass:end:Calling "pass" with marked data at "y"|<$>|inline_pass_propagate:end:Method "pass" propagates marked data from "y" to "res"*/
            z
        }
        /*f_return:start*/return res/*f_return:end:The returning value is assigned a value with marked data*/
        /*f_exit:start*/}/*f_exit:end:Exiting "f"*/

    fun source(): String = "tainted"
    fun sink(data: String) {}

    fun String.transform(): String {
        /*inline_transform:start*/return this/*inline_transform:end:Takes marked data at the 1st argument of "transform" and ends up with marked data at the returned value*/
        /*transform_exit:start*/}/*transform_exit:end:Exiting "transform"*/

    /*i2:start|<$>|i3:start*/inline fun buildIt(block: StringBuilder.() -> String): String = StringBuilder().block()/*i2:end:Inlined body of method "buildIt" entered|<$>|i3:end:Inline lambda inserted*/

    fun pass(s: String): String /*inline_pass_exit:start*/=
        /*inline_pass_body:start*/(s)/*inline_pass_body:end:Takes marked data at "s" and ends up with marked data at the returned value|<$>|inline_pass_exit:end:Exiting "pass"*/
}

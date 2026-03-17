package test.samples

class KotlinInlineLambdaSample {
    fun inlineLambdaFlow() {
        /*inline_source:start*/val data = source()/*inline_source:end:Call to "source" puts tainted data to "data"*/
        val /*i1:start*/result/*i1:end:Inline method "buildIt" inserted*/ = buildIt {
            /*inline_call:start|<$>|inline_propagate:start*/val s = data.transform()/*inline_call:end:Calling "transform" with tainted data at "data"|<$>|inline_propagate:end:Method "transform" propagates tainted data from "data" to "s"*/
            /*inline_pass:start|<$>|inline_pass_propagate:start*/val result = pass(s)/*inline_pass:end:Calling "pass" with tainted data at "s"|<$>|inline_pass_propagate:end:Method "pass" propagates tainted data from "s" to "result"*/
            result
        }
        /*inline_sink:start*/sink(result)/*inline_sink:end:Sink message: inline-lambda-rule*/
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}

    fun String.transform(): String {
        /*inline_transform:start*/return this/*inline_transform:end:Takes marked data at the 1st argument of "transform" and ends up with marked data at the returned value*/
        /*transform_exit:start*/}/*transform_exit:end:Exiting "transform"*/

    /*i2:start|<$>|i3:start*/inline fun buildIt(block: StringBuilder.() -> String): String = StringBuilder().block()/*i2:end:Inlined body of method "buildIt" entered|<$>|i3:end:Inline lambda inserted*/

    fun pass(s: String): String /*inline_pass_exit:start*/=
        /*inline_pass_body:start*/(s)/*inline_pass_body:end:Takes marked data at "s" and ends up with marked data at the returned value|<$>|inline_pass_exit:end:Exiting "pass"*/
}

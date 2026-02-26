package test.samples

class KotlinInlineLambdaSample2 {
    fun inlineLambdaFlow() {
        /*inline_source:start*/val data = source()/*inline_source:end:Call to "source" puts tainted data to "data"*/
        val /*i4:start*/d1/*i4:end:Inline method "buildIt" inserted*/ = buildIt {
            /*d1_call:start|<$>|d1_propagate:start*/val x = data.transform()/*d1_call:end:Calling "transform" with tainted data at "data"|<$>|d1_propagate:end:Method "transform" propagates tainted data from "data" to "x"*/
            /*d1_pass:start|<$>|d1_pass_propagate:start*/val r = pass(x)/*d1_pass:end:Calling "pass" with tainted data at "x"|<$>|d1_pass_propagate:end:Method "pass" propagates tainted data from "x" to "d1"*/
            r
        }
        val /*i1:start*/result/*i1:end:Inline method "buildIt" inserted*/ = buildIt {
            /*inline_call:start|<$>|inline_propagate:start*/val s = d1.transform()/*inline_call:end:Calling "transform" with tainted data at "d1"|<$>|inline_propagate:end:Method "transform" propagates tainted data from "d1" to "s"*/
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

    /*i2:start|<$>|i3:start|<$>|i5:start|<$>|i6:start*/inline fun buildIt(block: StringBuilder.() -> String): String = StringBuilder().block()/*i2:end:Inlined body of method "buildIt" entered|<$>|i3:end:Inline lambda inserted|<$>|i5:end:Inlined body of method "buildIt" entered|<$>|i6:end:Inline lambda inserted*/

    fun pass(s: String): String /*inline_pass_exit:start|<$>|d1_pass_exit:start*/=
        /*inline_pass_body:start|<$>|d1_pass_body:start*/(s)/*inline_pass_body:end:Takes marked data at "s" and ends up with marked data at the returned value|<$>|d1_pass_body:end:Takes marked data at "s" and ends up with marked data at the returned value|<$>|inline_pass_exit:end:Exiting "pass"|<$>|d1_pass_exit:end:Exiting "pass"*/
}

package test.samples

class KotlinComplexConditionalSample {
    fun complexConditionalFlow() {
        /*cond_taintA:start*/val a = taintA()/*cond_taintA:end:Call to "taintA" puts taintA data to "a"*/
        /*cond_taintB:start*/val b = taintB()/*cond_taintB:end:Method "taintB" puts taintB data to "b"*/
        /*cond_copy:start*/val c = copy(a, b)/*cond_copy:end:Method "copy" starts with taintA at "a"; taintB at "b", then puts combined data to "c"*/
        /*cond_sink:start*/sink(c)/*cond_sink:end:Sink message: complex-cond-rule*/
    }

    fun taintA(): String = "a"
    fun taintB(): String = "b"
    fun copy(x: String, y: String): String = x + y
    fun sink(data: String) {}
}

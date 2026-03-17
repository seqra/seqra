package test.samples

import java.util.Locale

object KotlinStaticMethodSample {
    private fun mkStr(): String = "static"

    @JvmField
    val staticField: String = mkStr()

    @JvmStatic
    fun staticMethod(): String {
        return /*staticCall:start*/staticField.lowercase(Locale.ROOT)/*staticCall:end*/
    }

    @JvmStatic
    /*staticMethodEntry:start*/fun staticMethodWithArgs(arg: String)/*staticMethodEntry:end*/ {
        println(arg)
    /*staticMethodExit:start*/}/*staticMethodExit:end*/
}

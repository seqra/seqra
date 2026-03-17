package test.samples

class KotlinMethodExitSinkSample {
    fun methodExitFlow(): String {
        /*exit_source:start*/val data = source()/*exit_source:end:Call to "source" puts secret data to "data"*/
        /*exit_return:start|<$>|exit_sink_loc:start*/return data/*exit_return:end:The returning value is assigned a value with secret data|<$>|exit_sink_loc:end:*/
    }

    fun source(): String = "tainted"
}

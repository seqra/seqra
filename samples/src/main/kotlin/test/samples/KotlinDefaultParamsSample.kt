package test.samples

class KotlinDefaultParamsSample {
    fun defaultParamsFlow() {
        /*default_source:start*/val data = source()/*default_source:end:Call to "source" puts tainted data to "data"*/
/*default_calling:start|<$>|default_call:start*/val other = KotlinDefaultProcessor().process(data)/*default_calling:end:Calling "process" with tainted data at "data"|<$>|default_call:end:Method "process" propagates tainted data from "data" to "other"*/
        /*default_sink:start*/sink(other)/*default_sink:end:Sink message: default-params-rule*/
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}

class KotlinDefaultProcessor {
    fun process(
        input: String,
        extra: String = ""
    ): String /*default_exit_process:start*/=
        /*default_takes:start*/(input)/*default_exit_process:end:Exiting "process"|<$>|default_takes:end:Takes marked data at "input" and ends up with marked data at the returned value*/
}

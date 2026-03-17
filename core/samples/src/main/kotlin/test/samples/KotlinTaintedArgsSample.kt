package test.samples

class KotlinTaintedArgsSample {
    fun taintedArgsFlow() {
        /*args_source:start*/val tainted = source()/*args_source:end:Call to "source" puts tainted data to "tainted"*/
        /*args_process_call:start|<$>|args_process_prop:start*/val result = processData(tainted)/*args_process_call:end:Calling "processData" with tainted data at "tainted"|<$>|args_process_prop:end:Method "processData" propagates tainted data from "tainted" to "result"*/
        /*args_sink:start*/sink(result)/*args_sink:end:Sink message: args-sink-rule*/
    }

    private /*args_process_entry:start*/fun processData(input: String): String/*args_process_entry:end:Entering "processData" with marked data at "input"*/ {
        /*args_transform:start*/return /*args_transform_call:start*/transform(input)/*args_transform_call:end:Calling "transform" with marked data at "input"|<$>|args_transform:end:The returning value is assigned marked data from "transform" call*/
    /*args_process_exit:start*/}/*args_process_exit:end:Exiting "processData"*/

    private fun transform(value: String): String {
        /*args_transform_body:start*/return value/*args_transform_body:end:Takes marked data at "value" and ends up with marked data at the returned value*/
    /*args_transform_exit:start*/}/*args_transform_exit:end:Exiting "transform"*/

    fun source(): String = "tainted"
    fun sink(data: String) {}
}

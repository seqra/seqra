package test.samples

class KotlinStaticFieldSample {
    companion object {
        private var staticField: String = ""
    }

    fun staticFieldFlow() {
        /*static_source:start*/staticField = source()/*static_source:end:Puts tainted data to the staticField*/
        /*static_process_call:start*/processStatic()/*static_process_call:end:Calling "processStatic" with tainted data at the staticField*/
    }

    private /*static_process_entry:start*/fun processStatic()/*static_process_entry:end:Entering "processStatic" with tainted data at the staticField*/ {
        /*static_read:start*/val data = staticField/*static_read:end:"data" is assigned a value with tainted data*/
        /*static_sink:start*/sink(data)/*static_sink:end:Sink message: static-field-rule*/
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}

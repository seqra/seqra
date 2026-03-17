package test.samples

class KotlinTaintedFieldsSample {
    private var taintedField: String = ""

    fun taintedFieldFlow() {
        /*field_source:start*/taintedField = source()/*field_source:end:Puts tainted data to the calling object*/
        /*field_process_call:start*/processField()/*field_process_call:end:Calling "processField" with tainted data at "this"*/
    }

    private /*field_process_entry:start*/fun processField()/*field_process_entry:end:Entering "processField" with tainted data at the calling object*/ {
        /*field_read:start*/val data = taintedField/*field_read:end:"data" is assigned a value with tainted data*/
        /*field_sink:start*/sink(data)/*field_sink:end:Sink message: field-sink-rule*/
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}

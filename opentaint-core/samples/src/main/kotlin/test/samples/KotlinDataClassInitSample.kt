package test.samples

/*data_prop_get_ret:start|<$>|data_prop_get_exit:start*/data class KotlinDataRecord/*data_prop_entering:start*/(/*data_prop_id_assign:start*/val id: String,/*data_prop_id_assign:end:"this.id" is assigned a value with marked data*/ val name: String, val count: Int = 0/*data_prop_exiting:start*/)/*data_prop_entering:end:Entering "KotlinDataRecord" initializer with marked data at "id"|<$>|data_prop_exiting:end:Exiting "KotlinDataRecord" initializer|<$>|data_prop_get_ret:end:The returning value is assigned marked data from "this.id"|<$>|data_prop_get_exit:end:Exiting "getId"*/

class KotlinDataClassInitSample {
    fun dataClassInitFlow() {
        /*data_source:start*/val id = source()/*data_source:end:Call to "source" puts tainted data to "id"*/
        KotlinDataRecord(id, "name")
        /*data_sink:start*/sink(id)/*data_sink:end:Sink message: data-class-init-rule*/
    }

    fun dataClassPropagationFlow() {
        /*data_prop_source:start*/val id = source()/*data_prop_source:end:Call to "source" puts tainted data to "id"*/
        /*data_prop_calling:start|<$>|data_prop_call:start*/val record = KotlinDataRecord(id, "name")/*data_prop_calling:end:Calling "KotlinDataRecord" initializer with tainted data at "id"|<$>|data_prop_call:end:"KotlinDataRecord" initializer propagates tainted data from "id" to "record"*/
        /*data_prop_get_call:start|<$>|data_prop_get_prop:start|<$>|data_prop_sink:start*/sink(record.id)/*data_prop_get_call:end:Calling "getId" with tainted data at "record"|<$>|data_prop_get_prop:end:Method "getId" propagates tainted data from "record" to a local variable|<$>|data_prop_sink:end:Sink message: data-class-propagation-rule*/
    }

    fun source(): String = "tainted"
    fun sink(data: String) {}
}

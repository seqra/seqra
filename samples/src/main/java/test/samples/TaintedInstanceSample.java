package test.samples;

public class TaintedInstanceSample {
    private String value;

    public /*inst_set_entry:start*/void setValue(String value)/*inst_set_entry:end:Entering "setValue" with marked data at the 1st argument of "setValue"*/ {
        /*inst_set_assign:start*/this.value/*inst_set_assign:end:"this.value" is assigned a value with marked data*/ = value;
    /*inst_set_exit:start*/}/*inst_set_exit:end:Exiting "setValue"*/

    public String getValue() {
        /*inst_get_return:start*/return value/*inst_get_return:end:The returning value is assigned marked data from "this.value"*/;
    /*inst_get_exit:start*/}/*inst_get_exit:end:Exiting "getValue"*/

    public void taintedInstanceFlow() {
        /*inst_create_inside:start|<$>|inst_create:start*/TaintedInstanceSample obj = createTaintedInstance()/*inst_create_inside:end:Inside of "createTaintedInstance"|<$>|inst_create:end:Call to "createTaintedInstance" puts tainted data to "obj"*/;
        /*inst_get_call:start|<$>|inst_get_prop:start*/String data = obj.getValue()/*inst_get_call:end:Calling "getValue" with tainted data at "obj"|<$>|inst_get_prop:end:Method "getValue" propagates tainted data from "obj" to "data"*/;
        /*inst_sink:start*/sink(data)/*inst_sink:end:Sink message: instance-sink-rule*/;
    }

    private TaintedInstanceSample createTaintedInstance() {
        TaintedInstanceSample instance = new TaintedInstanceSample();
        /*inst_source:start*/String tainted = source()/*inst_source:end:Call to "source" puts tainted data to "tainted"*/;
        /*inst_set_call:start|<$>|inst_set_prop:start*/instance.setValue(tainted)/*inst_set_call:end:Calling "setValue" with tainted data at "tainted"|<$>|inst_set_prop:end:Method "setValue" propagates tainted data from "tainted" to "instance"*/;
        /*inst_return:start*/return instance/*inst_return:end:The returning value is assigned a value with tainted data*/;
    /*inst_create_exit:start*/}/*inst_create_exit:end:Exiting "createTaintedInstance"*/

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

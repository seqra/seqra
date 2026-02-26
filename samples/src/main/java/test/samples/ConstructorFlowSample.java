package test.samples;

public class ConstructorFlowSample {
    private String data;

    public /*ctor_init_entry:start*/ConstructorFlowSample(String data)/*ctor_init_entry:end:Entering "ConstructorFlowSample" initializer with marked data at the 1st argument of "<init>"*/ {
        /*ctor_init_assign:start*/this.data/*ctor_init_assign:end:"this.data" is assigned a value with marked data*/ = data;
    /*ctor_init_exit:start*/}/*ctor_init_exit:end:Exiting "ConstructorFlowSample" initializer*/

    public String getData() {
        /*ctor_get_return:start*/return data/*ctor_get_return:end:The returning value is assigned marked data from "this.data"*/;
    /*ctor_get_exit:start*/}/*ctor_get_exit:end:Exiting "getData"*/

    public void constructorFlow() {
        /*ctor_source:start*/String tainted = source()/*ctor_source:end:Call to "source" puts tainted data to "tainted"*/;
        /*ctor_init_call:start|<$>|ctor_init_prop:start*/ConstructorFlowSample obj = new ConstructorFlowSample(tainted)/*ctor_init_call:end:Calling "ConstructorFlowSample" initializer with tainted data at "tainted"|<$>|ctor_init_prop:end:"ConstructorFlowSample" initializer propagates tainted data from "tainted" to "obj"*/;
        /*ctor_get_call:start|<$>|ctor_get_prop:start*/String extracted = obj.getData()/*ctor_get_call:end:Calling "getData" with tainted data at "obj"|<$>|ctor_get_prop:end:Method "getData" propagates tainted data from "obj" to "extracted"*/;
        /*ctor_sink:start*/sink(extracted)/*ctor_sink:end:Sink message: ctor-sink-rule*/;
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

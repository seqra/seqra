package test.samples;

public class TaintedArgsSample {
    public void taintedArgsFlow() {
        /*args_source:start*/String tainted = source()/*args_source:end:Call to "source" puts tainted data to "tainted"*/;
        /*args_process_call:start|<$>|args_process_prop:start*/String result = processData(tainted)/*args_process_call:end:Calling "processData" with tainted data at "tainted"|<$>|args_process_prop:end:Method "processData" propagates tainted data from "tainted" to "result"*/;
        /*args_sink:start*/sink(result)/*args_sink:end:Sink message: args-sink-rule*/;
    }

    private /*args_process_entry:start*/String processData(String input)/*args_process_entry:end:Entering "processData" with marked data at the 1st argument of "processData"*/ {
        /*args_transform:start*/return /*args_transform_call:start*/transform(input)/*args_transform_call:end:Calling "transform" with marked data at the 1st argument of "processData"|<$>|args_transform:end:The returning value is assigned marked data from "transform" call*/;
    /*args_process_exit:start*/}/*args_process_exit:end:Exiting "processData"*/

    private String transform(String value) {
        /*args_transform_body:start*/return value;/*args_transform_body:end:Takes marked data at the 1st argument of "transform" and ends up with marked data at the returned value*/
    /*args_transform_exit:start*/}/*args_transform_exit:end:Exiting "transform"*/

    public String source() { return "tainted"; }
    public void sink(String data) { }
}

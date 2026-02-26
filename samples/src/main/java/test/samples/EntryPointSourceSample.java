package test.samples;

public class EntryPointSourceSample {
    public /*entry_point_mark:start*/void entryPointFlow(String userInput)/*entry_point_mark:end:Method entry marks the 1st argument of "entryPointFlow" as userTaint*/ {
        /*entry_process_call:start|<$>|entry_process_prop:start*/String processed = process(userInput)/*entry_process_call:end:Calling "process" with userTaint data at the 1st argument of "entryPointFlow"|<$>|entry_process_prop:end:Method "process" propagates userTaint data from the 1st argument of "entryPointFlow" to "processed"*/;
        /*entry_sink:start*/sink(processed)/*entry_sink:end:Sink message: entry-point-rule*/;
    }

    private /*entry_process_entry:start*/String process(String input)/*entry_process_entry:end:Entering "process" with marked data at the 1st argument of "process"*/ {
        /*entry_transform:start*/return /*entry_transform_call:start*/transform(input)/*entry_transform_call:end:Calling "transform" with marked data at the 1st argument of "process"|<$>|entry_transform:end:The returning value is assigned marked data from "transform" call*/;
    /*entry_process_exit:start*/}/*entry_process_exit:end:Exiting "process"*/

    private String transform(String value) {
        /*entry_transform_body:start*/return value;/*entry_transform_body:end:Takes marked data at the 1st argument of "transform" and ends up with marked data at the returned value*/
    /*entry_transform_exit:start*/}/*entry_transform_exit:end:Exiting "transform"*/

    public void sink(String data) { }
}

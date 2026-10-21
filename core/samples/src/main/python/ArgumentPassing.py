def source() -> str:
    pass

def sink(data: str):
    pass

# Helper functions

def kill_arg(a: str) -> str:
    a = "safe"
    return a

def sink_second(a: str, b: str):
    sink(b)

def sink_first(a: str, b: str):
    sink(a)

# P1: taint killed inside callee
def call_arg_kill():
    sink(kill_arg(source()))

# P1: multiple args, taint on second, sink reads second
def call_multiple_args_positive():
    sink_second("safe", source())

# P1: multiple args, taint on second, sink reads first
def call_multiple_args_negative():
    sink_first("safe", source())

def source() -> str:
    pass

def sink(data: str):
    pass

# Helper functions (module-level for PIR resolution)

def helper_sink(a: str):
    sink(a)

def helper_return() -> str:
    return source()

def helper_passthrough(a: str) -> str:
    return a

# P0: taint passed as argument to function that calls sink
def call_simple():
    helper_sink(source())

# P0: taint returned from function
def call_return():
    sink(helper_return())

# P0: taint passed through and returned
def call_pass_through():
    sink(helper_passthrough(source()))

def source() -> str:
    pass

def sink(data: str):
    pass

class Wrapper:
    def __init__(self, data: str):
        self.data = data

    def read(self) -> str:
        return self.data

# Field set in the CONSTRUCTOR from a tainted arg; a SEPARATE method RETURNS it
# and the caller sinks the return. Minimal mirror of owasp request_wrapper
# (BenchmarkTest00283): wrapped = request_wrapper(request);
#   param = wrapped.get_form_parameter(...); sink(param)
def ctor_field_via_method():
    w = Wrapper(source())
    param = w.read()
    sink(param)

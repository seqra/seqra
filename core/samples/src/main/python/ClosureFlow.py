def source() -> str:
    pass

def sink(data: str):
    pass

def closure_forwarded_arg():
    k = "k"
    def inner(a):
        b = k
        sink(a)
    inner(source())

def closure_captured_local():
    x = source()
    def inner():
        sink(x)
    inner()

def _captured_param_helper(p):
    def inner():
        sink(p)
    inner()

def closure_captured_param():
    _captured_param_helper(source())

def closure_captured_safe():
    k = "safe"
    def inner(a):
        sink(k)
    inner(source())

def closure_late_binding():
    x = "safe"
    def inner():
        sink(x)
    x = source()
    inner()

def closure_nonlocal_write():
    x = "safe"
    def inner():
        nonlocal x
        x = source()
    inner()
    sink(x)

def _make_sinker():
    x = source()
    def inner():
        sink(x)
    return inner

def closure_returned():
    f = _make_sinker()
    f()

def closure_nonlocal_overwrite():
    x = source()
    def inner():
        nonlocal x
        x = "safe"
    inner()
    sink(x)

def closure_other_capture_safe():
    x = source()
    y = "safe"
    def inner():
        b = x
        sink(y)
    inner()

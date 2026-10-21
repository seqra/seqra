def source() -> str:
    pass

def sink(data):
    pass

# --- Gap A: keyword args bind to callee parameters by name ---

def deliver_second(a: str, b: str):
    sink(b)

def deliver_first(a: str, b: str):
    sink(a)

def deliver_third(a: str, b: str = "d", c: str = "d"):
    sink(c)

def deliver_second_default(a: str, b: str = "d", c: str = "d"):
    sink(b)

def deliver_var_keyword(**kw):
    sink(kw)

class Receiver:
    def deliver(self, a: str, b: str):
        sink(b)

# P: taint passed by keyword to the matching parameter
def kw_into_param():
    deliver_second("safe", b=source())

# P: out-of-order keyword args bind by name, not position
def kw_out_of_order():
    deliver_first(b="safe", a=source())

# P: keyword fills a later parameter; sink reads it
def kw_guard_positive():
    deliver_third("x", c=source())

# N: keyword fills c; sink reads default b — taint must not leak into b's slot
def kw_guard_negative():
    deliver_second_default("x", c=source())

# N: **kwargs capture is dropped (precise) — taint into **kw is not tracked
def kw_var_keyword_dropped():
    deliver_var_keyword(data=source())

# P: instance method (self offset), out-of-order keyword binds by name
def kw_instance_method():
    Receiver().deliver(b=source(), a="x")

# --- Gap B: rule positions written as kwarg(name) ---

def kw_sink(a: str = "", b: str = ""):
    pass

# rule sink pos = kwarg("a"); taint passed as a=
def kw_rule_present():
    kw_sink(a=source())

# rule sink pos = kwarg("a"); taint passed as b= (a absent) — rule must not fire
def kw_rule_absent():
    kw_sink(b=source())

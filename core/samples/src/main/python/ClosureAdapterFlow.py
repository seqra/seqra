def source() -> str:
    pass

def sink(data: str):
    pass

class Box:
    def __init__(self):
        self.value = None

class Holder:
    def __init__(self, env):
        self.env = env

class Adapter:
    def __init__(self, env):
        self.env = env

    def __call__(self):
        return impl(self)

def impl(s):
    sink(s.env["x"].value)

def dict_scalar():
    d = {"x": source()}
    sink(d["x"])

def dict_obj_field():
    c = Box()
    c.value = source()
    d = {"x": c}
    sink(d["x"].value)

def ctor_field():
    h = Holder(source())
    sink(h.env)

def ctor_dict_obj_field():
    c = Box()
    c.value = source()
    h = Holder({"x": c})
    sink(h.env["x"].value)

def full_chain():
    c = Box()
    c.value = source()
    a = Adapter({"x": c})
    a()

def full_chain_direct_impl():
    c = Box()
    c.value = source()
    a = Adapter({"x": c})
    impl(a)

def full_chain_explicit_call():
    c = Box()
    c.value = source()
    a = Adapter({"x": c})
    a.__call__()

class Sinker:
    def __call__(self, v):
        sink(v)

class FieldSinker:
    def __init__(self, v):
        self.v = v

    def __call__(self):
        sink(self.v)

def callable_arg():
    s = Sinker()
    s(source())

def callable_field():
    s = FieldSinker(source())
    s()

class RunSinker:
    def __init__(self, v):
        self.v = v

    def run(self):
        sink(self.v)

def method_field():
    s = RunSinker(source())
    s.run()

def callable_field_explicit():
    s = FieldSinker(source())
    s.__call__()

def read_v(s):
    sink(s.v)

class Forwarder:
    def __init__(self, v):
        self.v = v

    def run(self):
        read_v(self)

def method_forwards_self():
    f = Forwarder(source())
    f.run()

class Forwarder2:
    def __init__(self, v):
        self.v = v

    def run(self):
        me = self
        me = me
        read_v(me)

def method_forwards_self_copy():
    f = Forwarder2(source())
    f.run()

class DeepSinker:
    def __init__(self, v):
        self.v = v

    def run(self):
        sink(self.v["x"].value)

def method_field_deep():
    c = Box()
    c.value = source()
    s = DeepSinker({"x": c})
    s.run()

class DeepSinker2:
    def __init__(self, v):
        self.v = v

    def run(self):
        sink(self.v.value)

def method_field_depth2():
    c = Box()
    c.value = source()
    s = DeepSinker2(c)
    s.run()

class DeepSinker3:
    def __init__(self, v):
        self.v = v

    def run(self):
        sink(self.v["x"])

def method_field_dict():
    s = DeepSinker3({"x": source()})
    s.run()

class AdapterRun:
    def __init__(self, env):
        self.env = env

    def run(self):
        return impl(self)

def adapter_run():
    c = Box()
    c.value = source()
    a = AdapterRun({"x": c})
    a.run()

class AdapterNoReturn:
    def __init__(self, env):
        self.env = env

    def __call__(self):
        impl(self)

def adapter_no_return():
    c = Box()
    c.value = source()
    a = AdapterNoReturn({"x": c})
    a.__call__()

def impl_v(s):
    sink(s.v["x"].value)

class AdapterV:
    def __init__(self, v):
        self.v = v

    def __call__(self):
        return impl_v(self)

def adapter_v():
    c = Box()
    c.value = source()
    a = AdapterV({"x": c})
    a.__call__()

def read_v_value(s):
    sink(s.v.value)

class FwdDepth2:
    def __init__(self, v):
        self.v = v

    def run(self):
        read_v_value(self)

def fwd_depth2():
    c = Box()
    c.value = source()
    f = FwdDepth2(c)
    f.run()

def read_v_dict(s):
    sink(s.v["x"])

class FwdDict:
    def __init__(self, v):
        self.v = v

    def run(self):
        read_v_dict(self)

def fwd_dict():
    f = FwdDict({"x": source()})
    f.run()

def fwd_depth2_noctor_helper(s):
    s.run2()

class FwdNoCtor:
    def run(self):
        read_v_value(self)

def fwd_depth2_noctor():
    c = Box()
    c.value = source()
    f = FwdNoCtor()
    f.v = c
    f.run()

class FwdNoCtorCopy:
    def run(self):
        self = self
        read_v_value(self)

def fwd_depth2_noctor_copy():
    c = Box()
    c.value = source()
    f = FwdNoCtorCopy()
    f.v = c
    f.run()

def read_v_value_copy(s):
    s = s
    sink(s.v.value)

class FwdNoCtorCalleeCopy:
    def run(self):
        read_v_value_copy(self)

def fwd_depth2_noctor_callee_copy():
    c = Box()
    c.value = source()
    f = FwdNoCtorCalleeCopy()
    f.v = c
    f.run()

def fwd_depth2_plain_fn():
    c = Box()
    c.value = source()
    f = FwdNoCtor()
    f.v = c
    pass_through(f)

def pass_through(o):
    read_v_value(o)

def d2_intra():
    c = Box()
    c.value = source()
    f = FwdNoCtor()
    f.v = c
    sink(f.v.value)

def d2_one_call():
    c = Box()
    c.value = source()
    f = FwdNoCtor()
    f.v = c
    read_v_value(f)

def d2_one_call_store_after():
    f = FwdNoCtor()
    c = Box()
    f.v = c
    c.value = source()
    read_v_value(f)

def d2_two_calls_direct_store():
    f = FwdNoCtor()
    f.v = Box()
    f.v.value = source()
    pass_through(f)

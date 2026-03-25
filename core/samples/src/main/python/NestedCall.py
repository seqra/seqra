import os


def source() -> str:
    return ""


def sink(data: str):
    pass


# P1: Nested function receives tainted arg and calls sink
def nested_arg_to_sink():
    def process(arg):
        sink(arg)

    process(source())


# P2: Nested function calls module-level sink with tainted arg
def nested_arg_to_module_sink():
    def process(arg):
        sink(arg)

    data = source()
    process(data)


# P3: Entry source through nested function - benchmark pattern
def entry_source_nested(taint_src):
    def process(arg):
        taint_sink(arg)

    process(taint_src)


def taint_sink(o):
    os.system(o)


# P4: Entry source direct - should work
def entry_source_direct(taint_src):
    taint_sink(taint_src)


# P5: Nested function returns tainted value
def nested_return():
    def process():
        return source()

    data = process()
    sink(data)


# P6: Nested class with __init__
def nested_class_field():
    class A:
        def __init__(self):
            self.data = source()

    obj = A()
    sink(obj.data)

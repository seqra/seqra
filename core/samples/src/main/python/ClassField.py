def source() -> str:
    pass

def sink(data: str):
    pass

class Container:
    def __init__(self):
        self.data: str = ""
        self.other: str = ""

# P1: simple field read
def field_simple_read():
    obj = Container()
    obj.data = source()
    sink(obj.data)

# P2: different field - should NOT propagate (disabled for now)
def field_different_field():
    obj = Container()
    obj.data = source()
    sink(obj.other)

# P2: overwrite field kills taint (disabled for now)
def field_overwrite():
    obj = Container()
    obj.data = source()
    obj.data = "safe"
    sink(obj.data)

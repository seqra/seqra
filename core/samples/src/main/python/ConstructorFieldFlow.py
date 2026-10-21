def source() -> str:
    pass

def sink(data: str):
    pass

class Box:
    def __init__(self, v: str):
        self.value = v

# Tainted constructor argument is stored on `self.value`; it must surface on the
# constructed object `b` and reach the sink through `b.value`.
def ctor_field_to_sink():
    b = Box(source())
    sink(b.value)

def source() -> str:
    pass

def sink(data: str):
    pass

class Holder:
    def __init__(self):
        self.data: str = ""

    # The receiver's tainted field flows in through `self`.
    def leak(self):
        sink(self.data)

# A tainted field on the receiver reaches a sink via `self` inside an
# instance method: the receiver (carrying `.data`) maps to the callee's
# self = Argument(0).
def receiver_field_to_self():
    obj = Holder()
    obj.data = source()
    obj.leak()

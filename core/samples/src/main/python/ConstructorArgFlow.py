def source() -> str:
    pass

def sink(data: str):
    pass

class MyService:
    def __init__(self, value: str):
        sink(value)

class NoInitService:
    def handle(self, x: str):
        sink(x)

# Tainted constructor argument reaches a sink inside __init__
def ctor_arg_to_sink():
    MyService(source())

# No __init__ body: reconstructor falls back to the class QN, result-type
# binding still lets `obj.handle(...)` resolve to NoInitService.handle.
def no_init_chained_method():
    obj = NoInitService()
    obj.handle(source())

# Source is produced but not threaded through the constructor; a class-QN
# constructor sink rule must not fire.
def ctor_untainted_arg():
    t = source()
    MyService("safe")

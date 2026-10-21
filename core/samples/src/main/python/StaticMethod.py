def source() -> str:
    pass

def sink(data: str):
    pass

class Util:
    @staticmethod
    def transform(data: str) -> str:
        return data

    @classmethod
    def process(cls, data: str):
        sink(data)

# P2: static method pass-through
def static_method_call():
    result = Util.transform(source())
    sink(result)

# P2: classmethod sink
def classmethod_call():
    Util.process(source())

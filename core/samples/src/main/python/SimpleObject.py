def source() -> str:
    pass

def sink(data: str):
    pass

class MyClass:
    def process(self, data: str):
        sink(data)
    
    def get_data(self) -> str:
        return source()

# P1: method call on instance
def class_method_call():
    obj = MyClass()
    obj.process(source())

# P2: taint through method return
def class_method_return():
    obj = MyClass()
    result = obj.get_data()
    sink(result)

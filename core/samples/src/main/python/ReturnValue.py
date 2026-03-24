def source() -> str:
    pass

def sink(data: str):
    pass

# Helper functions

def return_tainted() -> str:
    x = source()
    return x

def return_safe(a: str) -> str:
    return "safe"

# P1: function returns tainted value
def return_assign_and_sink():
    result = return_tainted()
    sink(result)

# P1: function returns safe despite tainted input
def return_safe_despite_tainted_input():
    sink(return_safe(source()))

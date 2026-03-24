def source() -> str:
    pass

def sink(data: str):
    pass

# P2: dict literal with tainted value
def dict_literal():
    d = {"key": source()}
    sink(d["key"])

# P2: dict assignment 
def dict_assign():
    d = {}
    d["key"] = source()
    sink(d["key"])

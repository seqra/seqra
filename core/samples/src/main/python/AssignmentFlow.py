def source() -> str:
    pass

def sink(data: str):
    pass

# P0: direct assignment
def assign_direct():
    x = source()
    sink(x)

# P0: chain of assignments
def assign_chain():
    x = source()
    y = x
    sink(y)

# P0: long chain
def assign_long_chain():
    a = source()
    b = a
    c = b
    d = c
    sink(d)

# P0: overwrite kills taint
def assign_overwrite():
    x = source()
    x = "safe"
    sink(x)

# P0: overwriting a different variable does NOT kill taint
def assign_overwrite_other():
    x = source()
    y = "safe"
    sink(x)

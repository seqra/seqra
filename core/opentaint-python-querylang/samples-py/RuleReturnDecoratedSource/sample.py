def source():
    return "tainted"

def entry_point(f):
    return f

def id(arg):
    return arg

@entry_point
def Positive_returns_source():
    data = source()
    return data

@entry_point
def Negative_return_from_nested_call():
    data = source()
    id(data)
    return

@entry_point
def Negative_returns_constant():
    data = source()
    return "safe"

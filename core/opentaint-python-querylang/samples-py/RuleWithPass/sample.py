def src():
    return "tainted string"


def pass_through(src):
    return "string copy"


def sink(data):
    pass


def Positive_simple():
    data = src()
    other = pass_through(data)
    sink(other)

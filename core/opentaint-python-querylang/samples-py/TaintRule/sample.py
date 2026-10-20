def src():
    return "tainted string"


def sink(data):
    pass


def Positive_simple():
    data = src()
    sink(data)

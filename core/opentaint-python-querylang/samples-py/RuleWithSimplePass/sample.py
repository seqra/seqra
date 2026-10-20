def src():
    return "tainted string"


def clean(data):
    pass


def do_pass(data, other):
    pass


def sink(*data):
    pass


def Positive_simple():
    data = src()
    other = "other"
    do_pass(data, other)
    sink(other)


def Positive_simple2():
    data1 = src()
    data2 = src()
    sink(data1, data2)

def src():
    return "data"


def sink(data):
    pass


def other(data):
    return "other"


def Positive_simple():
    src_var = src()
    sink(src_var)


def Negative_simple1():
    src_var = src()
    other(src_var)


def Negative_simple2():
    src_var = other("other")
    sink(src_var)

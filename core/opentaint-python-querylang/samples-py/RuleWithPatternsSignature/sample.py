def sink(data):
    pass


def other(data):
    return "other"


def positive_simple_method(src):
    sink(src)


def Positive_simple():
    positive_simple_method("data")


def negative_simple1_method(src):
    other(src)


def Negative_simple1():
    negative_simple1_method("data")


def Negative_simple2():
    src = other("other")
    sink(src)

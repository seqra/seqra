def foo(data):
    return None


def bar(data):
    return None


def method(arg):
    x = foo(arg)
    y = bar(x)
    return y


def Positive_simple():
    method("data")

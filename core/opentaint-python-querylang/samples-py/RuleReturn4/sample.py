def src():
    return "tainted"


def clean(s):
    pass


def sink(s):
    pass


def positive_returned():
    a = src()
    return a


def Positive_simple():
    ret = positive_returned()
    sink(ret)


def negative_returned():
    a = src()
    clean(a)
    return a


def Negative_simple():
    ret = negative_returned()
    sink(ret)

def src():
    return "tainted"


def clean(s):
    pass


def f(s):
    pass


def _positive_returned():
    a = src()
    f(a)
    return a


def Positive_simple():
    res = _positive_returned()
    f(res)


def _negative_returned():
    a = src()
    clean(a)
    return a


def Negative_simple():
    res = _negative_returned()
    f(res)

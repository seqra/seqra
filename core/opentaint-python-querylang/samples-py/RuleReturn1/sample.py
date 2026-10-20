def src():
    return "tainted"


def clean(s):
    pass


def sink(s):
    pass


def _positive_returned():
    a = src()
    return a


def Positive_simple():
    res = _positive_returned()


def _negative_returned():
    a = src()
    return "safe"


def Negative_simple():
    res = _negative_returned()

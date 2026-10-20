def src(data):
    return "tainted"


def positive_returned(s):
    a = src(s)
    return a


def Positive_simple():
    positive_returned("tainted")


def negative_returned(s):
    a = src("safe")
    return a


def Negative_simple():
    negative_returned("tainted")

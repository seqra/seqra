def src(data):
    return "tainted"


def pass_through(data):
    return "copy"


def positive_returned(s):
    f = pass_through(s)
    a = src(f)
    return a


def Positive_simple():
    positive_returned("tainted")


def negative_returned(s):
    f = pass_through(s)
    a = src("safe")
    return a


def Negative_simple():
    negative_returned("tainted")


def negative2_returned(s):
    f = pass_through("safe")
    a = src(f)
    return a


def Negative_simple2():
    negative2_returned("tainted")

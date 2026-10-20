def src():
    return "tainted string"


def src1():
    return "not tainted string"


def sink(data):
    pass


def Positive_simple():
    data = src()
    sink(data)


def Positive_with_ellipsis():
    data = src()
    print(data)
    sink(data)


def sink_wrapper(data):
    print(data)
    sink(data)


def Positive_iter_proc():
    data = src()
    sink_wrapper(data)


def Negative_no_sink():
    data = src()


def Negative_no_source():
    data = src1()
    sink(data)

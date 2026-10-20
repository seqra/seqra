def src_init():
    return "src init data"


def sink_init():
    return "sink init data"


def src(init_data):
    return "tainted string"


def sink(init_data, data):
    pass


def Positive_simple():
    src_init_v = src_init()
    sink_init_v = sink_init()

    data = src(src_init_v)
    sink(sink_init_v, data)


def Negative_simple():
    src_init_v = src_init()

    data = src(src_init_v)
    sink(src_init_v, data)


def Negative_simple2():
    src_init_v = sink_init()

    data = src(src_init_v)
    sink(src_init_v, data)


def Negative_simple3():
    src_init_v = src_init()
    sink_init_v = sink_init()

    sink(sink_init_v, src_init_v)

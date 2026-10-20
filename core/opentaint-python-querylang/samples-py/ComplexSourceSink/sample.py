def src_init() -> str:
    return "a"


def src(a) -> str:
    return "tainted"


def sink_init() -> str:
    return "b"


def sink(b, y) -> None:
    pass


def Positive_flow():
    a = src_init()
    x = src(a)
    b = sink_init()
    sink(b, x)          # x (source taint, $Y) in arg index 1 -> should fire


def Negative_temp_in_sink():
    a = src_init()
    x = src(a)
    b = sink_init()
    sink(x, b)          # source taint in arg index 0 ($B position) -> should NOT fire


def Negative_no_src():
    a = src_init()
    b = sink_init()
    sink(b, a)          # no src() taint; a is plain src_init data -> should NOT fire

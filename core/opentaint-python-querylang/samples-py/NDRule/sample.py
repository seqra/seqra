def source() -> str:
    return "tainted"


def pass_through(a: str, b: str) -> str:
    return a + b


def sink(data) -> None:
    pass


def Positive_two_sources_one_sink():
    a = source()
    b = source()
    c = pass_through(a, b)
    sink(c)


def Negative_no_source():
    c = pass_through("a", "b")
    sink(c)


def Negative_no_sink():
    a = source()
    b = source()
    _ = pass_through(a, b)

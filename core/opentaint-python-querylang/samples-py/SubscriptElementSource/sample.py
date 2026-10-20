def source() -> list:
    return ["tainted"]


def sink(x) -> None:
    pass


def Positive_subscript_element():
    # `source()[0]` marks the element of source()'s result (Result[*]); reading element 0
    # into `x` should carry that taint to the sink.
    x = source()[0]
    sink(x)


def Negative_no_source():
    x = ["safe"][0]
    sink(x)

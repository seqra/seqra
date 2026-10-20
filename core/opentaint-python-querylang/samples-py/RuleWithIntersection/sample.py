def source() -> str:
    return "tainted"


def sink(data) -> None:
    pass


def Positive_simple():
    # Both $A=data and $B=data unify with source()->sink(); both patterns match.
    data = source()
    sink(data)


def Positive_two_flows():
    # Two independent source-to-sink flows; each pattern can bind to either.
    a = source()
    b = source()
    sink(a)
    sink(b)


def Negative_no_source():
    sink("constant")


def Negative_no_sink():
    _ = source()

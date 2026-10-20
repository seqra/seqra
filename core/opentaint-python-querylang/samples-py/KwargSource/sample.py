def read(payload) -> None:
    pass


def sink(y) -> None:
    pass


def Positive_kwarg_source():
    data = "x"
    read(payload=data)   # data tainted via the payload keyword source
    sink(data)           # tainted data reaches sink -> should fire


def Negative_other_kwarg():
    data = "x"
    read(other=data)     # payload keyword absent -> data NOT tainted
    sink(data)           # -> should NOT fire

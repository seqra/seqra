def source() -> str:
    return "tainted"


def Positive_no_return():
    x = source()
    _ = x


def Negative_returns_it():
    x = source()
    return x

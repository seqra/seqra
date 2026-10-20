def src() -> str:
    return "x"


def get_obj_good(x: str) -> str:
    return x


def get_obj_bad(x: str) -> str:
    return x


def sink(data) -> None:
    pass


def Positive_simple():
    a = src()
    b = get_obj_bad(a)
    b = get_obj_good(a)
    sink(b)


def Negative_bad():
    a = src()
    b = get_obj_good(a)
    b = get_obj_bad(a)
    sink(b)
